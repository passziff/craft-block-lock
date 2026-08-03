package com.craftblocklock.network;

import com.craftblocklock.CraftBlockLock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public record LockSyncPayload(
    Set<String> recipeKeys,
    Set<Integer> recipeDisplayIds,
    Set<String> blockTypes,
    boolean recipeLockEnabled,
    boolean blockLockEnabled,
    boolean messagesEnabled,
    boolean soundsEnabled,
    boolean lockedRecipeVisualsEnabled
) implements CustomPacketPayload {
    public static final Type<LockSyncPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftBlockLock.MOD_ID, "block_locks")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LockSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public LockSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            return new LockSyncPayload(
                readSet(buffer),
                readIntSet(buffer),
                readSet(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, LockSyncPayload payload) {
            writeSet(buffer, payload.recipeKeys);
            writeIntSet(buffer, payload.recipeDisplayIds);
            writeSet(buffer, payload.blockTypes);
            buffer.writeBoolean(payload.recipeLockEnabled);
            buffer.writeBoolean(payload.blockLockEnabled);
            buffer.writeBoolean(payload.messagesEnabled);
            buffer.writeBoolean(payload.soundsEnabled);
            buffer.writeBoolean(payload.lockedRecipeVisualsEnabled);
        }

        private Set<String> readSet(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Set<String> values = new HashSet<>(size);
            for (int index = 0; index < size; index++) {
                values.add(buffer.readUtf());
            }
            return values;
        }

        private void writeSet(RegistryFriendlyByteBuf buffer, Set<String> values) {
            buffer.writeVarInt(values.size());
            values.forEach(buffer::writeUtf);
        }

        private Set<Integer> readIntSet(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Set<Integer> values = new HashSet<>(size);
            for (int index = 0; index < size; index++) {
                values.add(buffer.readVarInt());
            }
            return values;
        }

        private void writeIntSet(RegistryFriendlyByteBuf buffer, Set<Integer> values) {
            buffer.writeVarInt(values.size());
            values.forEach(buffer::writeVarInt);
        }
    };

    public LockSyncPayload {
        recipeKeys = Set.copyOf(recipeKeys);
        recipeDisplayIds = Set.copyOf(recipeDisplayIds);
        blockTypes = Set.copyOf(blockTypes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
