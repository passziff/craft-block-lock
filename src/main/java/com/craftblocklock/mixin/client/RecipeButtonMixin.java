package com.craftblocklock.mixin.client;

import com.craftblocklock.client.ClientLockState;
import com.craftblocklock.client.LockedRecipeWidget;
import com.craftblocklock.client.LockedRecipeRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
abstract class RecipeButtonMixin implements LockedRecipeWidget {
    @Override
    public boolean craftblocklock$isLockedRecipe() {
        RecipeButton button = (RecipeButton) (Object) this;
        return ClientLockState.isRecipeDisplayLocked(button.getCurrentRecipe());
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void craftblocklock$renderLockedRecipe(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        RecipeButton button = (RecipeButton) (Object) this;
        if (ClientLockState.lockedRecipeVisualsEnabled()
            && ClientLockState.isRecipeDisplayLocked(button.getCurrentRecipe())) {
            LockedRecipeRenderer.render(graphics, button.getX() + 2, button.getY() + 2, 21, 21);
        }
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void craftblocklock$addLockedRecipeTooltip(
        ItemStack stack,
        CallbackInfoReturnable<List<Component>> callback
    ) {
        RecipeButton button = (RecipeButton) (Object) this;
        if (!ClientLockState.lockedRecipeVisualsEnabled()
            || !ClientLockState.isRecipeDisplayLocked(button.getCurrentRecipe())) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(callback.getReturnValue());
        tooltip.add(Component.translatable("tooltip.craftblocklock.recipe_locked").withStyle(ChatFormatting.RED));
        callback.setReturnValue(tooltip);
    }
}
