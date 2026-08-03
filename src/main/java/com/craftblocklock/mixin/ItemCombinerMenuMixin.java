package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
abstract class ItemCombinerMenuMixin extends AbstractContainerMenu {
    @Shadow @Final protected ResultContainer resultSlots;

    private ItemCombinerMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$denyLockedRecipeResult(
        Player player,
        boolean hasItem,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!CraftBlockLock.CONFIG.recipeLockEnabled || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        RecipeHolder<?> recipe = resultSlots.getRecipeUsed();
        if (recipe != null && LockManager.isRecipeLocked(serverPlayer, recipe)) {
            LockManager.showRecipeLocked(serverPlayer);
            callback.setReturnValue(false);
        }
    }
}
