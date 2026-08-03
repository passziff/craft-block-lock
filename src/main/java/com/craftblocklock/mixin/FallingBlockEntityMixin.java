package com.craftblocklock.mixin;

import com.craftblocklock.lock.LockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin {
    @Inject(method = "fall", at = @At("HEAD"))
    private static void craftblocklock$beginFalling(
        Level level,
        BlockPos pos,
        BlockState state,
        CallbackInfoReturnable<FallingBlockEntity> callback
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LockManager.beginFallingCreation(serverLevel, pos);
        }
    }

    @Inject(method = "fall", at = @At("RETURN"))
    private static void craftblocklock$finishFalling(
        Level level,
        BlockPos pos,
        BlockState state,
        CallbackInfoReturnable<FallingBlockEntity> callback
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LockManager.finishFallingCreation(serverLevel, pos, callback.getReturnValue());
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void craftblocklock$beginTick(CallbackInfo callback) {
        LockManager.beginFallingTick((FallingBlockEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void craftblocklock$finishTick(CallbackInfo callback) {
        LockManager.endFallingTick((FallingBlockEntity) (Object) this);
    }
}
