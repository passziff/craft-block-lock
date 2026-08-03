package com.craftblocklock.client;

import com.craftblocklock.network.LockSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CraftBlockLockClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LockSyncPayload.TYPE, (payload, context) ->
            ClientLockState.replace(
                payload.recipeKeys(),
                payload.blockTypes(),
                payload.recipeLockEnabled(),
                payload.blockLockEnabled(),
                payload.messagesEnabled(),
                payload.soundsEnabled()
            )
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientLockState.clear());
    }
}
