package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
    private static void craftblocklock$limitOutputToOneOperation(
        NonNullList<ItemStack> items,
        int maxStackSize,
        ItemStack burnResult,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (CraftBlockLock.CONFIG.recipeLockEnabled && !items.get(2).isEmpty()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void craftblocklock$tagFurnaceOutput(RecipeHolder<?> recipe, CallbackInfo callback) {
        if (CraftBlockLock.CONFIG.recipeLockEnabled && recipe != null) {
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) (Object) this;
            OperationKeys.mark(furnace.getItem(2), LockManager.recipeKey(recipe));
        }
    }
}
