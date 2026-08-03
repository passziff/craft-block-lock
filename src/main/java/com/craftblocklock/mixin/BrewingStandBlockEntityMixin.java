package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
    private static final ThreadLocal<String> craftblocklock$batch = new ThreadLocal<>();

    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void craftblocklock$beginBrewingBatch(
        Level level,
        BlockPos pos,
        NonNullList<ItemStack> items,
        CallbackInfo callback
    ) {
        if (CraftBlockLock.CONFIG.recipeLockEnabled) {
            craftblocklock$batch.set(UUID.randomUUID().toString());
        }
    }

    @Redirect(
        method = "doBrew",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;mix(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private static ItemStack craftblocklock$tagBrewingResult(
        PotionBrewing brewing,
        ItemStack ingredient,
        ItemStack input
    ) {
        ItemStack result = brewing.mix(ingredient, input);
        if (CraftBlockLock.CONFIG.recipeLockEnabled) {
            String batchId = craftblocklock$batch.get();
            if (batchId == null) {
                LockManager.markOperation(result, OperationKeys.brewing(ingredient, input, result));
            } else {
                LockManager.markOperation(result, OperationKeys.brewing(ingredient, input, result), batchId);
            }
        }
        return result;
    }

    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void craftblocklock$finishBrewingBatch(
        Level level,
        BlockPos pos,
        NonNullList<ItemStack> items,
        CallbackInfo callback
    ) {
        craftblocklock$batch.remove();
    }
}
