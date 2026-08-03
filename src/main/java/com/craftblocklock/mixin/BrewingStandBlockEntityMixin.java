package com.craftblocklock.mixin;
import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
    @Unique
    private static final int craftblocklock$ingredientSlot = 3;
    @Unique
    private static final int craftblocklock$firstBottleSlot = 0;
    @Unique
    private static final int craftblocklock$bottleSlotCount = 3;
    @Unique
    private static final ThreadLocal<BrewingTickContext> craftblocklock$tick = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<BrewingBatchContext> craftblocklock$batch = new ThreadLocal<>();

    @Shadow
    private static boolean isBrewable(PotionBrewing brewing, NonNullList<ItemStack> items) {
        throw new AssertionError();
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void craftblocklock$beginBrewingTick(
        Level level,
        BlockPos pos,
        BlockState state,
        BrewingStandBlockEntity stand,
        CallbackInfo callback
    ) {
        craftblocklock$tick.remove();
        if (CraftBlockLock.CONFIG.recipeLockEnabled) {
            craftblocklock$tick.set(new BrewingTickContext(
                level,
                pos.immutable(),
                craftblocklock$findPlayerUsing(level, stand)
            ));
        }
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
        NonNullList<ItemStack> items
    ) {
        boolean brewable = isBrewable(brewing, items);
        if (!brewable || !CraftBlockLock.CONFIG.recipeLockEnabled) {
            return brewable;
        }

        BrewingTickContext context = craftblocklock$tick.get();
        if (context == null || context.player == null) {
            return true;
        }

        ItemStack ingredient = items.get(craftblocklock$ingredientSlot);
        for (int slot = craftblocklock$firstBottleSlot;
             slot < craftblocklock$bottleSlotCount;
             slot++) {
            ItemStack input = items.get(slot);
            if (input.isEmpty()) {
                continue;
            }

            ItemStack result = brewing.mix(ingredient, input);
            if (!ItemStack.isSameItemSameComponents(input, result)
                && LockManager.isRecipeLocked(
                    context.player,
                    OperationKeys.brewing(ingredient, input, result)
                )) {
                return false;
            }
        }

        return true;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void craftblocklock$finishBrewingTick(
        Level level,
        BlockPos pos,
        BlockState state,
        BrewingStandBlockEntity stand,
        CallbackInfo callback
    ) {
        craftblocklock$tick.remove();
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

        BrewingTickContext tickContext = craftblocklock$tick.get();
        ServerPlayer player = tickContext != null
            && tickContext.level == level
            && tickContext.pos.equals(pos)
            ? tickContext.player
            : craftblocklock$findPlayerUsing(level, pos);
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
    private static ServerPlayer craftblocklock$findPlayerUsing(
        Level level,
        BrewingStandBlockEntity stand
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof BrewingStandMenu menu
                && menu.getSlot(0).container == stand) {
                return player;
            }
        }
        return null;
    }

    @Unique
    private static ServerPlayer craftblocklock$findPlayerUsing(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof BrewingStandMenu menu
                && menu.getSlot(0).container instanceof BrewingStandBlockEntity stand
                && stand.getBlockPos().equals(pos)) {
                return player;
            }
        }
        return null;
    }

    @Unique
    private static final class BrewingTickContext {
        private final Level level;
        private final BlockPos pos;
        private final ServerPlayer player;

        private BrewingTickContext(Level level, BlockPos pos, ServerPlayer player) {
            this.level = level;
            this.pos = pos;
            this.player = player;
        }
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
