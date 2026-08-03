package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
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
            OperationKeys.mark(result, OperationKeys.brewing(ingredient, input, result));
        }
        return result;
    }
}
