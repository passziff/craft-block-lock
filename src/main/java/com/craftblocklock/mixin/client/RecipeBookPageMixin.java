package com.craftblocklock.mixin.client;

import com.craftblocklock.client.ClientFeedback;
import com.craftblocklock.client.ClientLockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
abstract class RecipeBookPageMixin {
    @Shadow private Minecraft minecraft;
    @Shadow private @Nullable RecipeDisplayId lastClickedRecipe;
    @Shadow private @Nullable RecipeCollection lastClickedRecipeCollection;

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void craftblocklock$stopLockedRecipeSelection(
        MouseButtonEvent event,
        int x,
        int y,
        int width,
        int height,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (this.lastClickedRecipe == null || !ClientLockState.isRecipeDisplayLocked(this.lastClickedRecipe)) {
            return;
        }

        this.lastClickedRecipe = null;
        this.lastClickedRecipeCollection = null;
        if (this.minecraft.player != null) {
            ClientFeedback.showRecipeLocked(this.minecraft.player);
        }
        callback.setReturnValue(true);
    }
}
