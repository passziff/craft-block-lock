package com.craftblocklock.mixin.client;

import com.craftblocklock.client.ClientLockState;
import com.craftblocklock.client.LockedRecipeRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
abstract class OverlayRecipeButtonMixin extends AbstractWidget {
    @Shadow @Final private RecipeDisplayId recipe;

    protected OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void craftblocklock$renderLockedRecipe(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        if (ClientLockState.lockedRecipeVisualsEnabled()
            && ClientLockState.isRecipeDisplayLocked(this.recipe)) {
            LockedRecipeRenderer.render(graphics, this.getX() + 2, this.getY() + 2, 20, 20);
            this.setTooltip(Tooltip.create(
                Component.translatable("tooltip.craftblocklock.recipe_locked").withStyle(ChatFormatting.RED)
            ));
        } else {
            this.setTooltip(null);
        }
    }
}
