package com.craftblocklock.config;

import com.craftblocklock.CraftBlockLock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("craftblocklock.json");

    public boolean recipeLockEnabled = true;
    public boolean blockLockEnabled = true;
    public boolean messagesEnabled = true;
    public boolean denialSoundsEnabled = true;
    public boolean lockedRecipeVisualsEnabled = true;
    public List<String> recipeExceptions = new ArrayList<>(List.of(
        "minecraft:blaze_powder",
        "minecraft:ender_eye"
    ));
    public List<String> blockExceptions = new ArrayList<>();

    private ModConfig() {
    }

    public static ModConfig load() {
        ModConfig config = new ModConfig();
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | RuntimeException exception) {
                CraftBlockLock.LOGGER.error("Could not read {}. Using defaults.", PATH, exception);
            }
        }

        config.normalize();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            CraftBlockLock.LOGGER.error("Could not write {}.", PATH, exception);
        }
    }

    public boolean isRecipeException(String recipeKey) {
        return recipeExceptions.contains(recipeKey);
    }

    public boolean isBlockException(String blockItemId) {
        return blockExceptions.contains(blockItemId);
    }

    public boolean addRecipeException(String recipeKey) {
        if (recipeExceptions.contains(recipeKey)) {
            return false;
        }
        recipeExceptions.add(recipeKey);
        save();
        return true;
    }

    public boolean removeRecipeException(String recipeKey) {
        boolean removed = recipeExceptions.remove(recipeKey);
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean addBlockException(String blockItemId) {
        if (blockExceptions.contains(blockItemId)) {
            return false;
        }
        blockExceptions.add(blockItemId);
        save();
        return true;
    }

    public boolean removeBlockException(String blockItemId) {
        boolean removed = blockExceptions.remove(blockItemId);
        if (removed) {
            save();
        }
        return removed;
    }

    private void normalize() {
        recipeExceptions = clean(recipeExceptions);
        blockExceptions = clean(blockExceptions);
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return new ArrayList<>(cleaned);
    }
}
