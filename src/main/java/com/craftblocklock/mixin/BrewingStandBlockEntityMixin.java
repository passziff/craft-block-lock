package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.BrewingStandLockAccess;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin implements BrewingStandLockAccess {
    @Unique
    private static final String craftblocklock$brewerTag = "craftblocklock_brewer";

    @Unique
    private static final int craftblocklock$ingredientSlot = 3;

    @Unique
    private static final int craftblocklock$bottleSlotCount = 3;

    @Unique
    private static final ThreadLocal<BrewingBatchContext> craftblocklock$batch = new ThreadLocal<>();

    @Unique
    private UUID craftblocklock$brewer;

    @Unique
    private String craftblocklock$lastDeniedOperation;

    @Unique
    private long craftblocklock$lastDeniedGameTime = Long.MIN_VALUE;

    @Shadow
    private static boolean isBrewable(PotionBrewing brewing, NonNullList<ItemStack> items) {
        throw new AssertionError();
    }

    @Override
    public void craftblocklock$setBrewer(UUID playerId) {
        if (Objects.equals(craftblocklock$brewer, playerId)) {
            return;
        }
        craftblocklock$brewer = playerId;
        craftblocklock$clearDeniedOperation();
        ((BrewingStandBlockEntity) (Object) this).setChanged();
    }

    @Override
    public UUID craftblocklock$getBrewer() {
        return craftblocklock$brewer;
    }

    @Override
    public boolean craftblocklock$shouldNotifyDenied(String operation, long gameTime) {
        if (!operation.equals(craftblocklock$lastDeniedOperation)
            || craftblocklock$lastDeniedGameTime == Long.MIN_VALUE
            || gameTime - craftblocklock$lastDeniedGameTime >= 40L) {
            craftblocklock$lastDeniedOperation = operation;
            craftblocklock$lastDeniedGameTime = gameTime;
            return true;
        }
        return false;
    }

    @Override
    public void craftblocklock$clearDeniedOperation() {
        craftblocklock$lastDeniedOperation = null;
        craftblocklock$lastDeniedGameTime = Long.MIN_VALUE;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void craftblocklock$saveBrewer(ValueOutput output, CallbackInfo callback) {
        if (craftblocklock$brewer != null) {
            output.store(craftblocklock$brewerTag, Codec.STRING, craftblocklock$brewer.toString());
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void craftblocklock$loadBrewer(ValueInput input, CallbackInfo callback) {
        craftblocklock$brewer = input.read(craftblocklock$brewerTag, Codec.STRING)
            .flatMap(value -> {
                try {
                    return java.util.Optional.of(UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    return java.util.Optional.empty();
                }
            })
            .orElse(null);
        craftblocklock$clearDeniedOperation();
    }

    @Redirect(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;isBrewable(Lnet/minecraft/world/item/alchemy/PotionBrewing;Lnet/minecraft/core/NonNullList;)Z"
        )
    )
    private static boolean craftblocklock$denyLockedBrew(
        PotionBrewing brewing,
        NonNullList<ItemStack> items,
        Level level,
        BlockPos pos,
        BlockState state,
        BrewingStandBlockEntity stand
    ) {
        boolean brewable = isBrewable(brewing, items);
        if (!brewable || !CraftBlockLock.CONFIG.recipeLockEnabled) {
            ((BrewingStandLockAccess) stand).craftblocklock$clearDeniedOperation();
            return brewable;
        }

        BrewingStandLockAccess access = (BrewingStandLockAccess) stand;
        UUID brewerId = access.craftblocklock$getBrewer();
        ServerPlayer player = craftblocklock$findBrewer(level, brewerId);
        if (player == null && brewerId == null) {
            player = craftblocklock$findPlayerUsingStand(level, stand);
            if (player != null) {
                access.craftblocklock$setBrewer(player.getUUID());
            }
        }
        if (player == null) {
            return true;
        }

        ItemStack ingredient = items.get(craftblocklock$ingredientSlot);
        for (int slot = 0; slot < craftblocklock$bottleSlotCount; slot++) {
            ItemStack input = items.get(slot);
            if (input.isEmpty()) {
                continue;
            }

            ItemStack result = brewing.mix(ingredient, input);
            if (ItemStack.isSameItemSameComponents(input, result)) {
                continue;
            }

            String operation = OperationKeys.brewing(ingredient, input, result);
            if (!LockManager.isRecipeLocked(player, operation)) {
                continue;
            }

            if (craftblocklock$isUsingStand(player, stand)
                && access.craftblocklock$shouldNotifyDenied(operation, level.getGameTime())) {
                LockManager.showRecipeLocked(player);
            }
            return false;
        }

        access.craftblocklock$clearDeniedOperation();
        return true;
    }

    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void craftblocklock$beginBrewingBatch(
        Level level,
        BlockPos pos,
        NonNullList<ItemStack> items,
        CallbackInfo callback
    ) {
        craftblocklock$batch.remove();
        if (!CraftBlockLock.CONFIG.recipeLockEnabled) {
            return;
        }

        ServerPlayer player = null;
        if (level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand) {
            BrewingStandLockAccess access = (BrewingStandLockAccess) stand;
            UUID brewer = access.craftblocklock$getBrewer();
            player = craftblocklock$findBrewer(level, brewer);
            if (player == null && brewer == null) {
                player = craftblocklock$findPlayerUsingStand(level, stand);
                if (player != null) {
                    access.craftblocklock$setBrewer(player.getUUID());
                }
            }
        }

        craftblocklock$batch.set(new BrewingBatchContext(
            UUID.randomUUID().toString(),
            player
        ));
    }

    @Redirect(
        method = "doBrew",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;mix(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private static ItemStack craftblocklock$handleBrewingResult(
        PotionBrewing brewing,
        ItemStack ingredient,
        ItemStack input
    ) {
        ItemStack result = brewing.mix(ingredient, input);
        if (!CraftBlockLock.CONFIG.recipeLockEnabled
            || ItemStack.isSameItemSameComponents(input, result)) {
            return result;
        }

        String operation = OperationKeys.brewing(ingredient, input, result);
        BrewingBatchContext context = craftblocklock$batch.get();
        if (context != null && context.player != null) {
            context.operations.add(operation);
        } else if (context != null) {
            LockManager.markOperation(result, operation, context.batchId);
        } else {
            LockManager.markOperation(result, operation);
        }
        return result;
    }

    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void craftblocklock$finishBrewingBatch(
        Level level,
        BlockPos pos,
        NonNullList<ItemStack> items,
        CallbackInfo callback
    ) {
        BrewingBatchContext context = craftblocklock$batch.get();
        try {
            if (context != null && context.player != null) {
                for (String operation : context.operations) {
                    LockManager.lockRecipe(context.player, operation);
                }
            }
        } finally {
            craftblocklock$batch.remove();
        }
    }

    @Unique
    private static ServerPlayer craftblocklock$findBrewer(Level level, UUID playerId) {
        if (playerId == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(playerId);
    }

    @Unique
    private static ServerPlayer craftblocklock$findPlayerUsingStand(
        Level level,
        BrewingStandBlockEntity stand
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (craftblocklock$isUsingStand(player, stand)) {
                return player;
            }
        }
        return null;
    }

    @Unique
    private static boolean craftblocklock$isUsingStand(
        ServerPlayer player,
        BrewingStandBlockEntity stand
    ) {
        return player.containerMenu instanceof BrewingStandMenu menu
            && menu.getSlot(0).container == stand;
    }

    @Unique
    private static final class BrewingBatchContext {
        private final String batchId;
        private final ServerPlayer player;
        private final Set<String> operations = new HashSet<>();

        private BrewingBatchContext(String batchId, ServerPlayer player) {
            this.batchId = batchId;
            this.player = player;
        }
    }
}
