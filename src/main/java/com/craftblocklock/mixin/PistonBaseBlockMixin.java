package com.craftblocklock.mixin;

import com.craftblocklock.lock.LockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void craftblocklock$beginMove(
        Level level,
        BlockPos pistonPos,
        Direction direction,
        boolean extending,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LockManager.beginPistonMove(serverLevel, pistonPos, direction, extending);
        }
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private void craftblocklock$finishMove(
        Level level,
        BlockPos pistonPos,
        Direction direction,
        boolean extending,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (level instanceof ServerLevel serverLevel) {
            LockManager.finishPistonMove(serverLevel, callback.getReturnValueZ());
        }
    }
}
