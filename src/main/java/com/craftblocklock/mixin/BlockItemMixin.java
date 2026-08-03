package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.BrewingStandLockAccess;
import com.craftblocklock.lock.LockManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$denyDuplicatePlacement(
        BlockPlaceContext context,
        CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (!CraftBlockLock.CONFIG.blockLockEnabled || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String typeId = BuiltInRegistries.ITEM.getKey((Item) (Object) this).toString();
        if (!LockManager.mayPlace(player, typeId)) {
            LockManager.showBlockLocked(player, context.getItemInHand());
            // The client predicts block placement and consumes an item locally. The server stack
            // never changed, so a full menu sync is needed to restore the visible item count.
            player.containerMenu.sendAllDataToRemote();
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void craftblocklock$recordPlacement(
        BlockPlaceContext context,
        BlockState placementState,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!callback.getReturnValueZ()
            || !(context.getPlayer() instanceof ServerPlayer player)
            || !(context.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (placementState.is(Blocks.BREWING_STAND)
            && level.getBlockEntity(context.getClickedPos()) instanceof BrewingStandBlockEntity stand) {
            ((BrewingStandLockAccess) stand).craftblocklock$setBrewer(player.getUUID());
        }

        if (!CraftBlockLock.CONFIG.blockLockEnabled) {
            return;
        }

        String typeId = BuiltInRegistries.ITEM.getKey((Item) (Object) this).toString();
        String blockId = BuiltInRegistries.BLOCK.getKey(placementState.getBlock()).toString();
        LockManager.recordPlacement(player, typeId, blockId, level, context.getClickedPos());
    }
}
