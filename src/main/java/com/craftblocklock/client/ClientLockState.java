package com.craftblocklock.client;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public final class ClientLockState {
    private static Set<String> blockTypes = Set.of();
    private static Set<String> recipeKeys = Set.of();
    private static Set<Integer> recipeDisplayIds = Set.of();
    private static boolean recipeLockEnabled;
    private static boolean blockLockEnabled;
    private static boolean creativeModeBypass;
    private static boolean messagesEnabled;
    private static boolean soundsEnabled;
    private static boolean lockedRecipeVisualsEnabled;

    private ClientLockState() {
    }

    public static boolean isBlockLocked(String blockType) {
        return locksApply() && blockLockEnabled && blockTypes.contains(blockType);
    }

    public static boolean isRecipeLocked(String recipeKey) {
        return locksApply() && recipeLockEnabled && recipeKeys.contains(recipeKey);
    }

    public static boolean isRecipeDisplayLocked(RecipeDisplayId displayId) {
        return locksApply() && recipeLockEnabled && recipeDisplayIds.contains(displayId.index());
    }

    public static boolean messagesEnabled() {
        return messagesEnabled;
    }

    public static boolean soundsEnabled() {
        return soundsEnabled;
    }

    public static boolean lockedRecipeVisualsEnabled() {
        return lockedRecipeVisualsEnabled;
    }

    public static void replace(
        Set<String> recipes,
        Set<Integer> recipeDisplays,
        Set<String> blocks,
        boolean recipeEnabled,
        boolean blockEnabled,
        boolean creativeBypass,
        boolean messages,
        boolean sounds,
        boolean visuals
    ) {
        recipeKeys = Set.copyOf(recipes);
        recipeDisplayIds = Set.copyOf(recipeDisplays);
        blockTypes = Set.copyOf(blocks);
        recipeLockEnabled = recipeEnabled;
        blockLockEnabled = blockEnabled;
        creativeModeBypass = creativeBypass;
        messagesEnabled = messages;
        soundsEnabled = sounds;
        lockedRecipeVisualsEnabled = visuals;
    }

    public static void clear() {
        blockTypes = Set.of();
        recipeKeys = Set.of();
        recipeDisplayIds = Set.of();
        recipeLockEnabled = false;
        blockLockEnabled = false;
        creativeModeBypass = false;
        messagesEnabled = false;
        soundsEnabled = false;
        lockedRecipeVisualsEnabled = false;
    }

    private static boolean locksApply() {
        return !creativeModeBypass
            || Minecraft.getInstance().player == null
            || !Minecraft.getInstance().player.isCreative();
    }
}
