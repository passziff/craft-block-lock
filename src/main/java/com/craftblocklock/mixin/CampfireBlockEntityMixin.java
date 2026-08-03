package com.craftblocklock.mixin;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.lock.LockManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CampfireBlockEntity.class)
abstract class CampfireBlockEntityMixin {
    private static final ThreadLocal<RecipeHolder<?>> CRAFTBLOCKLOCK_RECIPE = new ThreadLocal<>();

    @Redirect(
        method = "cookTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;"
        )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<RecipeHolder<CampfireCookingRecipe>> craftblocklock$captureCampfireRecipe(
        RecipeManager.CachedCheck recipeCache,
        RecipeInput input,
        ServerLevel level
    ) {
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = recipeCache.getRecipeFor(input, level);
        CRAFTBLOCKLOCK_RECIPE.set(recipe.orElse(null));
        return recipe;
    }

    @ModifyArg(
        method = "cookTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"
        ),
        index = 4
    )
    private static ItemStack craftblocklock$tagCampfireOutput(ItemStack output) {
        RecipeHolder<?> recipe = CRAFTBLOCKLOCK_RECIPE.get();
        if (CraftBlockLock.CONFIG.recipeLockEnabled && recipe != null) {
            LockManager.markOperation(output, LockManager.recipeKey(recipe));
        }
        return output;
    }

    @Inject(method = "cookTick", at = @At("RETURN"))
    private static void craftblocklock$clearCampfireRecipe(CallbackInfo callback) {
        CRAFTBLOCKLOCK_RECIPE.remove();
    }
}
