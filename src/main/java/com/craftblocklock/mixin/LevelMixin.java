package com.craftblocklock.mixin;

import com.craftblocklock.lock.LockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void craftblocklock$trackChangedBlock(
        BlockPos pos,
        BlockState requestedState,
        int updateFlags,
        int updateLimit,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level) {
            LockManager.onBlockStateChanged(level, pos, level.getBlockState(pos));
        }
    }
}
