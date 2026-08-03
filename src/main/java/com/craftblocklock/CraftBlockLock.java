package com.craftblocklock;

import com.craftblocklock.command.CraftBlockLockCommands;
import com.craftblocklock.config.ModConfig;
import com.craftblocklock.lock.LockManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftBlockLock implements ModInitializer {
    public static final String MOD_ID = "craftblocklock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ModConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = ModConfig.load();
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (CONFIG.blockLockEnabled && level instanceof ServerLevel serverLevel) {
                LockManager.unlockPlacementAt(serverLevel, pos, state);
            }
        });
        CraftBlockLockCommands.register();
        LOGGER.info("Craft & Block Lock initialized.");
    }

    public static void reloadConfig() {
        CONFIG = ModConfig.load();
    }
}
