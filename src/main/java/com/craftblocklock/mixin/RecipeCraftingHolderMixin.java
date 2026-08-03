package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeCraftingHolder.class)
interface RecipeCraftingHolderMixin {
    @Inject(method = "awardUsedRecipes", at = @At("HEAD"))
    private void craftblocklock$lockUsedRecipe(Player player, List<ItemStack> inputs, CallbackInfo callback) {
        if (!CraftBlockLock.CONFIG.recipeLockEnabled || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        RecipeHolder<?> recipe = ((RecipeCraftingHolder) this).getRecipeUsed();
        if (recipe != null) {
            LockManager.lockRecipe(serverPlayer, recipe);
        }
    }
}
