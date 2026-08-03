package com.craftblocklock.lock;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.data.LockSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.craftblocklock.network.LockSyncPayload;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class LockManager {
    private static final int RECONCILIATION_BUDGET = 16;
    private static final long RECONCILIATION_INTERVAL_TICKS = 200L;
    private static final ThreadLocal<FallingCreationContext> FALLING_CREATION = new ThreadLocal<>();
    private static final ThreadLocal<FallingBlockEntity> FALLING_TICK = new ThreadLocal<>();
    private static final ThreadLocal<Deque<PistonMoveContext>> PISTON_MOVES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<MinecraftServer, ReconciliationState> RECONCILIATION_STATES = new WeakHashMap<>();

    private LockManager() {
    }

    public static boolean isRecipeLocked(ServerPlayer player, String recipeKey) {
        if (bypassesLocks(player) || CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            return false;
        }
        return LockSavedData.get(player.level().getServer()).isRecipeLocked(player.getUUID(), recipeKey);
    }

    public static boolean isRecipeLocked(ServerPlayer player, RecipeHolder<?> recipe) {
        return isRecipeLocked(player, recipeKey(recipe));
    }

    public static void lockRecipe(ServerPlayer player, String recipeKey) {
        if (bypassesLocks(player) || CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            return;
        }
        LockSavedData.get(player.level().getServer()).lockRecipe(player.getUUID(), recipeKey);
        syncLockState(player);
    }

    public static void lockRecipe(ServerPlayer player, RecipeHolder<?> recipe) {
        lockRecipe(player, recipeKey(recipe));
    }

    public static boolean mayPlace(ServerPlayer player, String typeId) {
        if (bypassesLocks(player) || CraftBlockLock.CONFIG.isBlockException(typeId)) {
            return true;
        }
        LockSavedData data = LockSavedData.get(player.level().getServer());
        if (reconcilePlacements(player, data.getPlacements(player.getUUID(), typeId))) {
            syncLockState(player);
        }
        return !data.hasPlacedType(player.getUUID(), typeId);
    }

    public static void recordPlacement(ServerPlayer player, String typeId, String blockId, ServerLevel level, BlockPos pos) {
        if (bypassesLocks(player) || CraftBlockLock.CONFIG.isBlockException(typeId)) {
            return;
        }
        LockSavedData.get(player.level().getServer()).recordPlacement(
            player.getUUID(), typeId, blockId, level.dimension().identifier().toString(), pos.asLong()
        );
        syncLockState(player);
    }

    public static void onBlockStateChanged(ServerLevel level, BlockPos pos, BlockState newState) {
        if (!CraftBlockLock.CONFIG.blockLockEnabled || isHandledByPiston(level, pos) || isFallingSource(level, pos)) {
            return;
        }

        LockSavedData data = LockSavedData.get(level.getServer());
        FallingBlockEntity fallingEntity = FALLING_TICK.get();
        if (fallingEntity != null
            && fallingEntity.level() == level
            && data.getFallingPlacement(fallingEntity.getUUID()).isPresent()
            && !newState.isAir()
            && !newState.is(Blocks.MOVING_PISTON)
            && newState.getBlock() == fallingEntity.getBlockState().getBlock()) {
            completeFallingPlacement(level, pos, newState, fallingEntity.getUUID());
            return;
        }

        Optional<LockSavedData.StoredPlacement> stored = data.getPlacementAt(dimensionId(level), pos.asLong());
        if (stored.isEmpty()) {
            return;
        }
        Set<UUID> changedPlayers = new HashSet<>();
        reconcileKnownPlacement(level, pos, newState, data, stored.get(), changedPlayers);
        syncPlayers(level.getServer(), changedPlayers);
    }

    public static boolean reconcilePlacements(ServerPlayer player) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        return reconcilePlacements(player, data.getPlacements(player.getUUID()));
    }

    public static void tickReconciliation(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        ReconciliationState state = RECONCILIATION_STATES.computeIfAbsent(server, ignored -> new ReconciliationState());
        LockSavedData data = LockSavedData.get(server);

        if (state.cursor >= state.snapshot.size()) {
            if (gameTime < state.nextSnapshotTick) {
                return;
            }
            state.snapshot = data.getAllPlacements();
            state.cursor = 0;
            state.nextSnapshotTick = gameTime + RECONCILIATION_INTERVAL_TICKS;
        }

        Set<UUID> changedPlayers = new HashSet<>();
        int checked = 0;
        while (checked < RECONCILIATION_BUDGET && state.cursor < state.snapshot.size()) {
            LockSavedData.StoredPlacement placement = state.snapshot.get(state.cursor++);
            checked++;
            reconcileSnapshotPlacement(server, data, placement, changedPlayers);
        }
        syncPlayers(server, changedPlayers);
    }

    private static boolean reconcilePlacements(
        ServerPlayer player,
        List<LockSavedData.StoredPlacement> placements
    ) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        Set<UUID> changedPlayers = new HashSet<>();

        for (LockSavedData.StoredPlacement placement : placements) {
            Identifier dimensionId = Identifier.tryParse(placement.dimension());
            ServerLevel level = dimensionId == null
                ? null
                : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) {
                data.removePlacementAt(placement.dimension(), placement.position());
                parseUuid(placement.player()).ifPresent(changedPlayers::add);
                continue;
            }

            BlockPos pos = BlockPos.of(placement.position());
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState currentState = level.getBlockState(pos);
            reconcilePlacementAt(level, pos, currentState, data, changedPlayers);
        }

        return !changedPlayers.isEmpty();
    }

    public static void beginFallingCreation(ServerLevel level, BlockPos sourcePos) {
        String dimension = dimensionId(level);
        if (LockSavedData.get(level.getServer()).getPlacementAt(dimension, sourcePos.asLong()).isPresent()) {
            FALLING_CREATION.set(new FallingCreationContext(level, sourcePos.asLong()));
        }
    }

    public static void finishFallingCreation(ServerLevel level, BlockPos sourcePos, FallingBlockEntity entity) {
        FallingCreationContext context = FALLING_CREATION.get();
        FALLING_CREATION.remove();
        if (context == null || context.level != level || context.sourcePosition != sourcePos.asLong()) {
            return;
        }

        LockSavedData.get(level.getServer())
            .movePlacementToFalling(dimensionId(level), sourcePos.asLong(), entity.getUUID());
    }

    public static void beginFallingTick(FallingBlockEntity entity) {
        if (entity.level() instanceof ServerLevel) {
            FALLING_TICK.set(entity);
        }
    }

    public static void endFallingTick(FallingBlockEntity entity) {
        if (FALLING_TICK.get() == entity) {
            FALLING_TICK.remove();
        }
    }

    public static void onFallingEntityRemoved(FallingBlockEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        LockSavedData data = LockSavedData.get(level.getServer());
        data.getFallingPlacement(entity.getUUID()).flatMap(placement -> parseUuid(placement.player())).ifPresent(playerId -> {
            Map<UUID, Set<String>> before = placedTypes(data, Set.of(playerId));
            data.removeFallingPlacement(entity.getUUID());
            syncPlayersWithChangedTypes(level.getServer(), data, before);
        });
    }

    public static void beginPistonMove(ServerLevel level, BlockPos pistonPos, net.minecraft.core.Direction direction, boolean extending) {
        if (!CraftBlockLock.CONFIG.blockLockEnabled) {
            PISTON_MOVES.get().push(PistonMoveContext.inactive(level));
            return;
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, direction, extending);
        if (!resolver.resolve()) {
            PISTON_MOVES.get().push(PistonMoveContext.inactive(level));
            return;
        }

        Map<Long, Long> moves = new HashMap<>();
        Set<Long> destroyed = new HashSet<>();
        Set<Long> handled = new HashSet<>();
        net.minecraft.core.Direction pushDirection = resolver.getPushDirection();
        for (BlockPos source : resolver.getToPush()) {
            long sourcePosition = source.asLong();
            long destinationPosition = source.relative(pushDirection).asLong();
            moves.put(sourcePosition, destinationPosition);
            handled.add(sourcePosition);
            handled.add(destinationPosition);
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            destroyed.add(pos.asLong());
            handled.add(pos.asLong());
        }

        PISTON_MOVES.get().push(new PistonMoveContext(level, moves, destroyed, handled, true));
    }

    public static void finishPistonMove(ServerLevel level, boolean successful) {
        Deque<PistonMoveContext> moves = PISTON_MOVES.get();
        if (moves.isEmpty()) {
            return;
        }

        PistonMoveContext context = moves.pop();
        if (moves.isEmpty()) {
            PISTON_MOVES.remove();
        }
        if (!successful || !context.active || context.level != level) {
            return;
        }

        LockSavedData data = LockSavedData.get(level.getServer());
        String dimension = dimensionId(level);
        Map<Long, LockSavedData.StoredPlacement> movingPlacements = new HashMap<>();
        Set<UUID> changedPlayers = new HashSet<>();

        context.moves.forEach((source, destination) -> data.getPlacementAt(dimension, source)
            .ifPresent(placement -> movingPlacements.put(source, placement)));

        Set<Long> clearedPositions = new HashSet<>(context.destroyed);
        clearedPositions.addAll(context.moves.keySet());
        clearedPositions.addAll(context.moves.values());
        for (long position : clearedPositions) {
            data.getPlacementAt(dimension, position)
                .flatMap(placement -> parseUuid(placement.player()))
                .ifPresent(changedPlayers::add);
        }
        Map<UUID, Set<String>> beforeTypes = placedTypes(data, changedPlayers);
        for (long position : clearedPositions) {
            data.removePlacementAt(dimension, position);
        }

        context.moves.forEach((source, destination) -> {
            LockSavedData.StoredPlacement placement = movingPlacements.get(source);
            if (placement == null || CraftBlockLock.CONFIG.isBlockException(placement.type())) {
                return;
            }
            parseUuid(placement.player()).ifPresent(playerId -> {
                data.recordPlacement(playerId, placement.type(), placement.block(), dimension, destination);
                changedPlayers.add(playerId);
            });
        });
        syncPlayersWithChangedTypes(level.getServer(), data, beforeTypes);
    }

    private static void completeFallingPlacement(ServerLevel level, BlockPos pos, BlockState state, UUID entityId) {
        LockSavedData data = LockSavedData.get(level.getServer());
        Optional<LockSavedData.StoredFallingPlacement> moving = data.getFallingPlacement(entityId);
        if (moving.isEmpty()) {
            return;
        }

        Set<UUID> changedPlayers = new HashSet<>();
        String dimension = dimensionId(level);
        data.getPlacementAt(dimension, pos.asLong())
            .flatMap(placement -> parseUuid(placement.player()))
            .ifPresent(changedPlayers::add);

        LockSavedData.StoredFallingPlacement placement = moving.get();
        parseUuid(placement.player()).ifPresent(changedPlayers::add);
        Map<UUID, Set<String>> beforeTypes = placedTypes(data, changedPlayers);
        data.removeFallingPlacement(entityId);
        data.removePlacementAt(dimension, pos.asLong());

        parseUuid(placement.player()).ifPresent(playerId ->
            blockType(state).ifPresent(blockType -> {
                if (!CraftBlockLock.CONFIG.isBlockException(blockType.typeId)) {
                    data.recordPlacement(playerId, blockType.typeId, blockType.blockId, dimension, pos.asLong());
                }
            })
        );
        syncPlayersWithChangedTypes(level.getServer(), data, beforeTypes);
    }

    private static void reconcileSnapshotPlacement(
        MinecraftServer server,
        LockSavedData data,
        LockSavedData.StoredPlacement snapshot,
        Set<UUID> changedPlayers
    ) {
        Optional<LockSavedData.StoredPlacement> currentPlacement = data.getPlacementAt(snapshot.dimension(), snapshot.position());
        if (currentPlacement.isEmpty() || !currentPlacement.get().equals(snapshot)) {
            return;
        }

        Identifier dimensionId = Identifier.tryParse(snapshot.dimension());
        ServerLevel level = dimensionId == null
            ? null
            : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            data.removePlacementAt(snapshot.dimension(), snapshot.position());
            parseUuid(snapshot.player()).ifPresent(changedPlayers::add);
            return;
        }

        BlockPos pos = BlockPos.of(snapshot.position());
        if (level.isLoaded(pos)) {
            reconcilePlacementAt(level, pos, level.getBlockState(pos), data, changedPlayers);
        }
    }

    private static void reconcilePlacementAt(
        ServerLevel level,
        BlockPos pos,
        BlockState currentState,
        LockSavedData data,
        Set<UUID> changedPlayers
    ) {
        String dimension = dimensionId(level);
        Optional<LockSavedData.StoredPlacement> stored = data.getPlacementAt(dimension, pos.asLong());
        if (stored.isEmpty()) {
            return;
        }

        reconcileKnownPlacement(level, pos, currentState, data, stored.get(), changedPlayers);
    }

    private static void reconcileKnownPlacement(
        ServerLevel level,
        BlockPos pos,
        BlockState currentState,
        LockSavedData data,
        LockSavedData.StoredPlacement placement,
        Set<UUID> changedPlayers
    ) {
        String dimension = dimensionId(level);
        String currentBlockId = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
        if (currentBlockId.equals(placement.block()) || currentState.is(Blocks.MOVING_PISTON)) {
            return;
        }

        data.removePlacementAt(dimension, pos.asLong());
        parseUuid(placement.player()).ifPresent(playerId -> {
            changedPlayers.add(playerId);
            blockType(currentState).ifPresent(blockType -> {
                if (!CraftBlockLock.CONFIG.isBlockException(blockType.typeId)) {
                    data.recordPlacement(playerId, blockType.typeId, blockType.blockId, dimension, pos.asLong());
                }
            });
        });
    }

    private static Optional<BlockType> blockType(BlockState state) {
        if (!(state.getBlock().asItem() instanceof BlockItem blockItem)) {
            return Optional.empty();
        }
        return Optional.of(new BlockType(
            BuiltInRegistries.ITEM.getKey(blockItem).toString(),
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
        ));
    }

    private static boolean isFallingSource(ServerLevel level, BlockPos pos) {
        FallingCreationContext context = FALLING_CREATION.get();
        return context != null && context.level == level && context.sourcePosition == pos.asLong();
    }

    private static boolean isHandledByPiston(ServerLevel level, BlockPos pos) {
        Deque<PistonMoveContext> moves = PISTON_MOVES.get();
        if (moves.isEmpty()) {
            return false;
        }
        PistonMoveContext context = moves.peek();
        return context.active && context.level == level && context.handledPositions.contains(pos.asLong());
    }

    private static void syncPlayers(MinecraftServer server, Set<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                syncLockState(player);
            }
        }
    }

    private static Map<UUID, Set<String>> placedTypes(LockSavedData data, Set<UUID> playerIds) {
        Map<UUID, Set<String>> types = new HashMap<>();
        playerIds.forEach(playerId -> types.put(playerId, data.getPlacedTypes(playerId)));
        return types;
    }

    private static void syncPlayersWithChangedTypes(
        MinecraftServer server,
        LockSavedData data,
        Map<UUID, Set<String>> previousTypes
    ) {
        previousTypes.forEach((playerId, oldTypes) -> {
            if (!oldTypes.equals(data.getPlacedTypes(playerId))) {
                syncPlayers(server, Set.of(playerId));
            }
        });
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static boolean mayAcquireProvenance(ServerPlayer player, ItemStack stack) {
        if (bypassesLocks(player)) {
            return true;
        }
        return OperationKeys.read(stack).map(key -> !isRecipeLocked(player, key)).orElse(true);
    }

    public static void markOperation(ItemStack stack, String recipeKey) {
        if (!CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            OperationKeys.mark(stack, recipeKey);
        }
    }

    public static void markOperation(ItemStack stack, String recipeKey, String batchId) {
        if (!CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            OperationKeys.mark(stack, recipeKey, batchId);
        }
    }

    public static void consumeProvenance(ServerPlayer player, ItemStack stack) {
        if (bypassesLocks(player)) {
            OperationKeys.readProvenance(stack).ifPresent(provenance -> {
                OperationKeys.clear(stack);
                clearMatchingProvenance(player, provenance);
            });
            return;
        }
        OperationKeys.readProvenance(stack).ifPresent(provenance -> {
            lockRecipe(player, provenance.recipeKey());
            OperationKeys.clear(stack);
            clearMatchingProvenance(player, provenance);
        });
    }

    public static void showRecipeLocked(ServerPlayer player) {
        if (CraftBlockLock.CONFIG.messagesEnabled) {
            player.sendOverlayMessage(Component.literal("Recipe locked: you have already crafted this recipe."));
        }
        if (CraftBlockLock.CONFIG.denialSoundsEnabled) {
            sendDenialSound(player, SoundEvents.NOTE_BLOCK_BASS, 0.7F, 0.6F);
        }
    }

    public static void showBlockLocked(ServerPlayer player, ItemStack blockItem) {
        if (CraftBlockLock.CONFIG.messagesEnabled) {
            player.sendOverlayMessage(Component.literal("Block locked: you have already placed one somewhere."));
        }
        if (CraftBlockLock.CONFIG.denialSoundsEnabled) {
            sendDenialSound(player, SoundEvents.NOTE_BLOCK_BASS, 0.7F, 0.6F);
        }
    }

    public static void syncLockState(ServerPlayer player) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        Set<String> lockedRecipeKeys = data.getCraftedRecipes(player.getUUID()).stream()
            .filter(key -> !CraftBlockLock.CONFIG.isRecipeException(key))
            .collect(java.util.stream.Collectors.toSet());
        Set<Integer> lockedRecipeDisplays = new HashSet<>();
        for (String recipeKey : lockedRecipeKeys) {
            Identifier recipeId = Identifier.tryParse(recipeKey);
            if (recipeId != null) {
                player.level().recipeAccess().listDisplaysForRecipe(
                    ResourceKey.create(Registries.RECIPE, recipeId),
                    display -> lockedRecipeDisplays.add(display.id().index())
                );
            }
        }

        ServerPlayNetworking.send(player, new LockSyncPayload(
            lockedRecipeKeys,
            lockedRecipeDisplays,
            data.getPlacedTypes(player.getUUID()).stream()
                .filter(key -> !CraftBlockLock.CONFIG.isBlockException(key))
                .collect(java.util.stream.Collectors.toSet()),
            CraftBlockLock.CONFIG.recipeLockEnabled,
            CraftBlockLock.CONFIG.blockLockEnabled,
            CraftBlockLock.CONFIG.creativeModeBypass,
            CraftBlockLock.CONFIG.messagesEnabled,
            CraftBlockLock.CONFIG.denialSoundsEnabled,
            CraftBlockLock.CONFIG.lockedRecipeVisualsEnabled
        ));
    }

    public static void syncAllPlayers(net.minecraft.server.MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(LockManager::syncLockState);
    }

    public static String recipeKey(RecipeHolder<?> recipe) {
        return recipe.id().identifier().toString();
    }

    public static boolean bypassesLocks(ServerPlayer player) {
        return CraftBlockLock.CONFIG.creativeModeBypass && player.isCreative();
    }

    private static void clearMatchingProvenance(ServerPlayer player, OperationKeys.Provenance provenance) {
        boolean changed = false;
        Set<Container> containers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        player.containerMenu.slots.forEach(slot -> containers.add(slot.container));

        for (Container container : containers) {
            boolean containerChanged = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack item = container.getItem(slot);
                if (OperationKeys.readProvenance(item).filter(provenance::equals).isPresent()) {
                    OperationKeys.clear(item);
                    containerChanged = true;
                    changed = true;
                }
            }
            if (containerChanged) {
                container.setChanged();
            }
        }
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void sendDenialSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
            sound,
            SoundSource.PLAYERS,
            player.getX(),
            player.getY(),
            player.getZ(),
            volume,
            pitch,
            player.getRandom().nextLong()
        ));
    }

    private record BlockType(String typeId, String blockId) {
    }

    private record FallingCreationContext(ServerLevel level, long sourcePosition) {
    }

    private record PistonMoveContext(
        ServerLevel level,
        Map<Long, Long> moves,
        Set<Long> destroyed,
        Set<Long> handledPositions,
        boolean active
    ) {
        private static PistonMoveContext inactive(ServerLevel level) {
            return new PistonMoveContext(level, Map.of(), Set.of(), Set.of(), false);
        }
    }

    private static final class ReconciliationState {
        private List<LockSavedData.StoredPlacement> snapshot = List.of();
        private int cursor;
        private long nextSnapshotTick;
    }
}
