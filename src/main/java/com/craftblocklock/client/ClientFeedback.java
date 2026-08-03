package com.craftblocklock.client;

import com.craftblocklock.CraftBlockLock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ClientFeedback {
    private ClientFeedback() {
    }

    public static void showRecipeLocked(Player player) {
        if (ClientLockState.messagesEnabled()) {
            player.sendOverlayMessage(Component.literal("Recipe locked: you have already crafted this recipe."));
        }
        playSound(player);
    }

    public static void showBlockLocked(Player player, ItemStack blockItem) {
        if (ClientLockState.messagesEnabled()) {
            player.sendOverlayMessage(
                Component.literal("Block locked: break your active " + blockItem.getHoverName().getString()
                    + " placement before placing another.")
            );
        }
        playSound(player);
    }

    private static void playSound(Player player) {
        if (ClientLockState.soundsEnabled()) {
            player.playSound(CraftBlockLock.DENY_SOUND, 0.8F, 1.0F);
        }
    }
}
