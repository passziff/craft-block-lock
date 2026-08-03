package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class SlotMixin {
    @Shadow @Final public Container container;

    @Shadow public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$denyLockedResult(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!CraftBlockLock.CONFIG.recipeLockEnabled || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (container instanceof RecipeCraftingHolder holder) {
            RecipeHolder<?> recipe = holder.getRecipeUsed();
            if (recipe != null && LockManager.isRecipeLocked(serverPlayer, recipe)) {
                LockManager.showRecipeLocked(serverPlayer);
                callback.setReturnValue(false);
                return;
            }
        }

        if (!LockManager.mayAcquireProvenance(serverPlayer, getItem())) {
            LockManager.showRecipeLocked(serverPlayer);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void craftblocklock$consumeTaggedResult(Player player, ItemStack carried, CallbackInfo callback) {
        if (CraftBlockLock.CONFIG.recipeLockEnabled && player instanceof ServerPlayer serverPlayer) {
            LockManager.consumeProvenance(serverPlayer, carried);
        }
    }
}
