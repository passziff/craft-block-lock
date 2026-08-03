package com.craftblocklock.client;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F);
        }
    }
}
