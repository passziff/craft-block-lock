package com.craftblocklock.client;

import java.util.Set;

public final class ClientLockState {
    private static Set<String> blockTypes = Set.of();
    private static Set<String> recipeKeys = Set.of();
    private static boolean recipeLockEnabled;
    private static boolean blockLockEnabled;
    private static boolean messagesEnabled;
    private static boolean soundsEnabled;

    private ClientLockState() {
    }

    public static boolean isBlockLocked(String blockType) {
        return blockLockEnabled && blockTypes.contains(blockType);
    }

    public static boolean isRecipeLocked(String recipeKey) {
        return recipeLockEnabled && recipeKeys.contains(recipeKey);
    }

    public static boolean messagesEnabled() {
        return messagesEnabled;
    }

    public static boolean soundsEnabled() {
        return soundsEnabled;
    }

    public static void replace(
        Set<String> recipes,
        Set<String> blocks,
        boolean recipeEnabled,
        boolean blockEnabled,
        boolean messages,
        boolean sounds
    ) {
        recipeKeys = Set.copyOf(recipes);
        blockTypes = Set.copyOf(blocks);
        recipeLockEnabled = recipeEnabled;
        blockLockEnabled = blockEnabled;
        messagesEnabled = messages;
        soundsEnabled = sounds;
    }

    public static void clear() {
        blockTypes = Set.of();
        recipeKeys = Set.of();
        recipeLockEnabled = false;
        blockLockEnabled = false;
    }
}
