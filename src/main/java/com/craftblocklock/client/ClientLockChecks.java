package com.craftblocklock.client;

import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class ClientLockChecks {
    private ClientLockChecks() {
    }

    public static boolean isLockedResult(Slot slot) {
        if (slot == null || !slot.hasItem()) {
            return false;
        }

        if (slot.container instanceof RecipeCraftingHolder holder) {
            RecipeHolder<?> recipe = holder.getRecipeUsed();
            if (recipe != null && ClientLockState.isRecipeLocked(LockManager.recipeKey(recipe))) {
                return true;
            }
        }

        ItemStack stack = slot.getItem();
        return !slot.mayPlace(stack)
            && OperationKeys.readLockKey(stack).map(ClientLockState::isRecipeLocked).orElse(false);
    }
}
