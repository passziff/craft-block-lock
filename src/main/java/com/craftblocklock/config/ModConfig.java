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

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("craftblocklock.json");

    public boolean recipeLockEnabled = true;
    public boolean blockLockEnabled = true;

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

        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            CraftBlockLock.LOGGER.error("Could not write {}.", PATH, exception);
        }
        return config;
    }
}
