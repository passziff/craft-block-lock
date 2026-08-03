package com.craftblocklock.mixin.client;

import com.craftblocklock.client.LockedRecipeWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
abstract class AbstractWidgetMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void craftblocklock$skipClickSoundForLockedRecipes(SoundManager soundManager, CallbackInfo callback) {
        if ((Object) this instanceof LockedRecipeWidget widget && widget.craftblocklock$isLockedRecipe()) {
            callback.cancel();
        }
    }
}
