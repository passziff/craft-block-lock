package com.craftblocklock.lock;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.data.LockSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class LockManager {
    private LockManager() {
    }

    public static boolean isRecipeLocked(ServerPlayer player, String recipeKey) {
        return LockSavedData.get(player.level().getServer()).isRecipeLocked(player.getUUID(), recipeKey);
    }

    public static boolean isRecipeLocked(ServerPlayer player, RecipeHolder<?> recipe) {
        return isRecipeLocked(player, recipeKey(recipe));
    }

    public static void lockRecipe(ServerPlayer player, String recipeKey) {
        LockSavedData.get(player.level().getServer()).lockRecipe(player.getUUID(), recipeKey);
    }

    public static void lockRecipe(ServerPlayer player, RecipeHolder<?> recipe) {
        lockRecipe(player, recipeKey(recipe));
    }

    public static boolean mayPlace(ServerPlayer player, String typeId) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        Optional<LockSavedData.StoredPlacement> saved = data.getPlacement(player.getUUID(), typeId);
        if (saved.isEmpty()) {
            return true;
        }

        LockSavedData.StoredPlacement placement = saved.get();
        Identifier dimensionId = Identifier.tryParse(placement.dimension());
        ServerLevel level = dimensionId == null ? null : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            data.removePlacement(player.getUUID(), typeId);
            return true;
        }

        String currentBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.of(placement.position())).getBlock()).toString();
        if (!currentBlock.equals(placement.block())) {
            data.removePlacement(player.getUUID(), typeId);
            return true;
        }
        return false;
    }

    public static void recordPlacement(ServerPlayer player, String typeId, String blockId, ServerLevel level, BlockPos pos) {
        LockSavedData.get(player.level().getServer()).recordPlacement(
            player.getUUID(), typeId, blockId, level.dimension().identifier().toString(), pos.asLong()
        );
    }

    public static void unlockPlacementAt(ServerLevel level, BlockPos pos, BlockState brokenState) {
        LockSavedData data = LockSavedData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        String blockId = BuiltInRegistries.BLOCK.getKey(brokenState.getBlock()).toString();
        Optional<LockSavedData.StoredPlacement> removed = data.removePlacementAt(dimension, pos.asLong());
        if (removed.isPresent() && !removed.get().block().equals(blockId)) {
            LockSavedData.StoredPlacement placement = removed.get();
            data.recordPlacement(
                java.util.UUID.fromString(placement.player()), placement.type(), placement.block(), placement.dimension(), placement.position()
            );
        }
    }

    public static boolean mayAcquireProvenance(ServerPlayer player, ItemStack stack) {
        return OperationKeys.read(stack).map(key -> !isRecipeLocked(player, key)).orElse(true);
    }

    public static void consumeProvenance(ServerPlayer player, ItemStack stack) {
        OperationKeys.readProvenance(stack).ifPresent(provenance -> {
            lockRecipe(player, provenance.recipeKey());
            OperationKeys.clear(stack);
            clearMatchingProvenance(player.getInventory(), provenance);
        });
    }

    public static void showRecipeLocked(ServerPlayer player) {
        player.sendOverlayMessage(Component.literal("Recipe locked: you have already crafted this recipe."));
    }

    public static void showBlockLocked(ServerPlayer player, ItemStack blockItem) {
        player.sendOverlayMessage(
            Component.literal("Block locked: break your active " + blockItem.getHoverName().getString() + " placement before placing another.")
        );
    }

    public static String recipeKey(RecipeHolder<?> recipe) {
        return recipe.id().identifier().toString();
    }

    private static void clearMatchingProvenance(Inventory inventory, OperationKeys.Provenance provenance) {
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (OperationKeys.readProvenance(stack).filter(provenance::equals).isPresent()) {
                OperationKeys.clear(stack);
            }
        }
    }
}
