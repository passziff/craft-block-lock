package com.craftblocklock.lock;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class OperationKeys {
    private static final String RECIPE_TAG = "craftblocklock_recipe";
    private static final String BATCH_TAG = "craftblocklock_batch";
    private static final String PREVIEW_TAG = "craftblocklock_preview_recipe";

    private OperationKeys() {
    }

    public static String brewing(ItemStack ingredient, ItemStack input, ItemStack result) {
        return "brewing|" + itemAndPotion(input) + "|" + itemId(ingredient) + "|" + itemAndPotion(result);
    }

    public static void mark(ItemStack stack, String recipeKey) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putString(RECIPE_TAG, recipeKey);
                tag.putString(BATCH_TAG, UUID.randomUUID().toString());
            });
        }
    }

    public static Optional<String> read(ItemStack stack) {
        return readProvenance(stack).map(Provenance::recipeKey);
    }

    public static void markPreview(ItemStack stack, String recipeKey) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PREVIEW_TAG, recipeKey));
        }
    }

    public static Optional<String> readLockKey(ItemStack stack) {
        Optional<String> operation = read(stack);
        if (operation.isPresent() || stack.isEmpty()) {
            return operation;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getString(PREVIEW_TAG).filter(value -> !value.isBlank());
    }

    public static Optional<Provenance> readProvenance(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Optional<String> recipe = tag.getString(RECIPE_TAG).filter(value -> !value.isBlank());
        Optional<String> batch = tag.getString(BATCH_TAG).filter(value -> !value.isBlank());
        return recipe.flatMap(recipeKey -> batch.map(batchId -> new Provenance(recipeKey, batchId)));
    }

    public static void clear(ItemStack stack) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(RECIPE_TAG));
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BATCH_TAG));
        }
    }

    public static void clearPreview(ItemStack stack) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(PREVIEW_TAG));
        }
    }

    public record Provenance(String recipeKey, String batchId) {
    }

    private static String itemAndPotion(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        String potion = contents.potion().flatMap(Holder::unwrapKey)
            .map(key -> key.identifier().toString())
            .orElse("none");
        return itemId(stack) + "@" + potion;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
