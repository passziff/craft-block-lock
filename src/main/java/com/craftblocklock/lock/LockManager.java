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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.craftblocklock.network.LockSyncPayload;

import java.util.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LockManager {
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
        return data.getPlacements(player.getUUID(), typeId).isEmpty();
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

    public static void unlockPlacementAt(ServerLevel level, BlockPos pos, BlockState brokenState) {
        LockSavedData data = LockSavedData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        Optional<LockSavedData.StoredPlacement> removed = data.removePlacementAt(dimension, pos.asLong());
        removed.flatMap(placement -> {
            try {
                return Optional.ofNullable(level.getServer().getPlayerList().getPlayer(UUID.fromString(placement.player())));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }).ifPresent(LockManager::syncLockState);
    }

    public static boolean reconcilePlacements(ServerPlayer player) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        return reconcilePlacements(player, data.getPlacements(player.getUUID()));
    }

    public static void reconcileAllPlayerPlacements(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (reconcilePlacements(player)) {
                syncLockState(player);
            }
        }
    }

    private static boolean reconcilePlacements(
        ServerPlayer player,
        List<LockSavedData.StoredPlacement> placements
    ) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        boolean changed = false;

        for (LockSavedData.StoredPlacement placement : placements) {
            Identifier dimensionId = Identifier.tryParse(placement.dimension());
            ServerLevel level = dimensionId == null
                ? null
                : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) {
                data.removePlacementAt(placement.dimension(), placement.position());
                changed = true;
                continue;
            }

            BlockPos pos = BlockPos.of(placement.position());
            if (!level.hasChunkAt(pos)) {
                continue;
            }

            BlockState currentState = level.getBlockState(pos);
            String currentBlockId = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
            if (currentBlockId.equals(placement.block())) {
                continue;
            }

            data.removePlacementAt(placement.dimension(), placement.position());
            changed = true;

            if (currentState.getBlock().asItem() instanceof BlockItem blockItem) {
                String currentTypeId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
                if (!CraftBlockLock.CONFIG.isBlockException(currentTypeId)) {
                    data.recordPlacement(
                        player.getUUID(),
                        currentTypeId,
                        currentBlockId,
                        placement.dimension(),
                        placement.position()
                    );
                }
            }
        }

        return changed;
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

    public static void consumeProvenance(ServerPlayer player, ItemStack stack) {
        if (bypassesLocks(player)) {
            OperationKeys.readProvenance(stack).ifPresent(provenance -> {
                OperationKeys.clear(stack);
                clearMatchingProvenance(player.getInventory(), provenance);
            });
            return;
        }
        OperationKeys.readProvenance(stack).ifPresent(provenance -> {
            lockRecipe(player, provenance.recipeKey());
            OperationKeys.clear(stack);
            clearMatchingProvenance(player.getInventory(), provenance);
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

    private static void clearMatchingProvenance(Inventory inventory, OperationKeys.Provenance provenance) {
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (OperationKeys.readProvenance(stack).filter(provenance::equals).isPresent()) {
                OperationKeys.clear(stack);
            }
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
}
