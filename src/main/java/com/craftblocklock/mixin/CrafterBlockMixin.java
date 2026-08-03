package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import com.craftblocklock.lock.OperationKeys;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(CrafterBlock.class)
abstract class CrafterBlockMixin {
    @ModifyArgs(
        method = "dispenseFrom",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CrafterBlock;dispenseItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/CrafterBlockEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
            ordinal = 0
        )
    )
    private void craftblocklock$tagCrafterOutput(Args args) {
        if (CraftBlockLock.CONFIG.recipeLockEnabled) {
            ItemStack output = args.get(3);
            RecipeHolder<?> recipe = args.get(5);
            OperationKeys.mark(output, LockManager.recipeKey(recipe));
        }
    }
}
