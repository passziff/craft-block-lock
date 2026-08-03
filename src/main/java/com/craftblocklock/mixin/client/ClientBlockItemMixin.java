package com.craftblocklock.mixin.client;

import com.craftblocklock.client.ClientLockState;
import com.craftblocklock.client.ClientFeedback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class ClientBlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$preventPredictedPlacement(
        BlockPlaceContext context,
        CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (!context.getLevel().isClientSide()) {
            return;
        }

        String typeId = BuiltInRegistries.ITEM.getKey((Item) (Object) this).toString();
        if (ClientLockState.isBlockLocked(typeId)) {
            if (context.getPlayer() != null) {
                ClientFeedback.showBlockLocked(context.getPlayer(), context.getItemInHand());
            }
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
