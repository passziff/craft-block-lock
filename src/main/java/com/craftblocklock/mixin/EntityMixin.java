package com.craftblocklock.mixin;

import com.craftblocklock.lock.LockManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "remove", at = @At("RETURN"))
    private void craftblocklock$releaseRemovedFallingBlock(Entity.RemovalReason reason, CallbackInfo callback) {
        if (reason.shouldDestroy() && (Object) this instanceof FallingBlockEntity fallingEntity) {
            LockManager.onFallingEntityRemoved(fallingEntity);
        }
    }
}
