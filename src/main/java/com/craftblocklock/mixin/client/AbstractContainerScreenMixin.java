package com.craftblocklock.mixin.client;

import com.craftblocklock.client.ClientFeedback;
import com.craftblocklock.client.ClientLockChecks;
import com.craftblocklock.client.ClientLockState;
import com.craftblocklock.client.LockedRecipeRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow protected @Nullable Slot hoveredSlot;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$stopLockedResultClick(
        @Nullable Slot slot,
        int slotId,
        int button,
        ContainerInput input,
        CallbackInfo callback
    ) {
        if (ClientLockChecks.isLockedResult(slot)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                ClientFeedback.showRecipeLocked(player);
            }
            callback.cancel();
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void craftblocklock$renderLockedResult(
        GuiGraphicsExtractor graphics,
        Slot slot,
        int mouseX,
        int mouseY,
        CallbackInfo callback
    ) {
        if (ClientLockState.lockedRecipeVisualsEnabled() && ClientLockChecks.isLockedResult(slot)) {
            LockedRecipeRenderer.render(graphics, slot.x, slot.y, 16, 16);
        }
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void craftblocklock$addLockedResultTooltip(
        ItemStack stack,
        CallbackInfoReturnable<List<Component>> callback
    ) {
        if (!ClientLockState.lockedRecipeVisualsEnabled() || !ClientLockChecks.isLockedResult(this.hoveredSlot)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(callback.getReturnValue());
        tooltip.add(Component.translatable("tooltip.craftblocklock.recipe_locked").withStyle(ChatFormatting.RED));
        callback.setReturnValue(tooltip);
    }
}
