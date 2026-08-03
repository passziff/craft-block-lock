package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultContainer.class)
abstract class ResultContainerMixin {
    @Inject(method = "setItem", at = @At("HEAD"))
    private void craftblocklock$markResultPreview(int slot, ItemStack stack, CallbackInfo callback) {
        if (!CraftBlockLock.CONFIG.recipeLockEnabled) {
            return;
        }

        RecipeHolder<?> recipe = ((RecipeCraftingHolder) this).getRecipeUsed();
        if (recipe == null) {
            return;
        }

        String recipeKey = LockManager.recipeKey(recipe);
        if (!CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            OperationKeys.markPreview(stack, recipeKey);
        }
    }

    @Inject(method = "removeItem", at = @At("RETURN"))
    private void craftblocklock$clearRemovedPreview(
        int slot,
        int count,
        CallbackInfoReturnable<ItemStack> callback
    ) {
        OperationKeys.clearPreview(callback.getReturnValue());
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void craftblocklock$clearRemovedPreviewNoUpdate(
        int slot,
        CallbackInfoReturnable<ItemStack> callback
    ) {
        OperationKeys.clearPreview(callback.getReturnValue());
    }
}
