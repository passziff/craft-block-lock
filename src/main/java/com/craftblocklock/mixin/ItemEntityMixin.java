package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$enforceTaggedWorldOutput(Player player, CallbackInfo callback) {
        if (!CraftBlockLock.CONFIG.recipeLockEnabled || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack stack = getItem();
        if (!LockManager.mayAcquireProvenance(serverPlayer, stack)) {
            LockManager.showRecipeLocked(serverPlayer);
            callback.cancel();
            return;
        }
        LockManager.consumeProvenance(serverPlayer, stack);
    }
}
