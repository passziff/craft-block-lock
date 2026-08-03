package com.craftblocklock.lock;

import com.craftblocklock.CraftBlockLock;
import com.craftblocklock.data.LockSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.craftblocklock.network.LockSyncPayload;

import java.util.Optional;

public final class LockManager {
    private LockManager() {
    }

    public static boolean isRecipeLocked(ServerPlayer player, String recipeKey) {
        if (CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            return false;
        }
        return LockSavedData.get(player.level().getServer()).isRecipeLocked(player.getUUID(), recipeKey);
    }

    public static boolean isRecipeLocked(ServerPlayer player, RecipeHolder<?> recipe) {
        return isRecipeLocked(player, recipeKey(recipe));
    }

    public static void lockRecipe(ServerPlayer player, String recipeKey) {
        if (CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            return;
        }
        LockSavedData.get(player.level().getServer()).lockRecipe(player.getUUID(), recipeKey);
        syncLockState(player);
    }

    public static void lockRecipe(ServerPlayer player, RecipeHolder<?> recipe) {
        lockRecipe(player, recipeKey(recipe));
    }

    public static boolean mayPlace(ServerPlayer player, String typeId) {
        if (CraftBlockLock.CONFIG.isBlockException(typeId)) {
            return true;
        }
        LockSavedData data = LockSavedData.get(player.level().getServer());
        Optional<LockSavedData.StoredPlacement> saved = data.getPlacement(player.getUUID(), typeId);
        if (saved.isEmpty()) {
            return true;
        }

        LockSavedData.StoredPlacement placement = saved.get();
        Identifier dimensionId = Identifier.tryParse(placement.dimension());
        ServerLevel level = dimensionId == null ? null : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            data.removePlacement(player.getUUID(), typeId);
            return true;
        }

        String currentBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.of(placement.position())).getBlock()).toString();
        if (!currentBlock.equals(placement.block())) {
            data.removePlacement(player.getUUID(), typeId);
            return true;
        }
        return false;
    }

    public static void recordPlacement(ServerPlayer player, String typeId, String blockId, ServerLevel level, BlockPos pos) {
        if (CraftBlockLock.CONFIG.isBlockException(typeId)) {
            return;
        }
        LockSavedData.get(player.level().getServer()).recordPlacement(
            player.getUUID(), typeId, blockId, level.dimension().identifier().toString(), pos.asLong()
        );
        syncLockState(player);
    }

    public static void unlockPlacementAt(ServerLevel level, BlockPos pos, BlockState brokenState) {
        LockSavedData data = LockSavedData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        String blockId = BuiltInRegistries.BLOCK.getKey(brokenState.getBlock()).toString();
        Optional<LockSavedData.StoredPlacement> removed = data.removePlacementAt(dimension, pos.asLong());
        if (removed.isPresent() && !removed.get().block().equals(blockId)) {
            LockSavedData.StoredPlacement placement = removed.get();
            data.recordPlacement(
                java.util.UUID.fromString(placement.player()), placement.type(), placement.block(), placement.dimension(), placement.position()
            );
        }
        removed.flatMap(placement -> {
            try {
                return Optional.ofNullable(level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(placement.player())));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }).ifPresent(LockManager::syncLockState);
    }

    public static boolean mayAcquireProvenance(ServerPlayer player, ItemStack stack) {
        return OperationKeys.read(stack).map(key -> !isRecipeLocked(player, key)).orElse(true);
    }

    public static void markOperation(ItemStack stack, String recipeKey) {
        if (!CraftBlockLock.CONFIG.isRecipeException(recipeKey)) {
            OperationKeys.mark(stack, recipeKey);
        }
    }

    public static void consumeProvenance(ServerPlayer player, ItemStack stack) {
        OperationKeys.readProvenance(stack).ifPresent(provenance -> {
            lockRecipe(player, provenance.recipeKey());
            OperationKeys.clear(stack);
            clearMatchingProvenance(player.getInventory(), provenance);
        });
    }

    public static void showRecipeLocked(ServerPlayer player) {
        if (CraftBlockLock.CONFIG.messagesEnabled) {
            player.sendOverlayMessage(Component.literal("Recipe locked: you have already crafted this recipe."));
        }
        if (CraftBlockLock.CONFIG.denialSoundsEnabled) {
            sendDenialSound(player, CraftBlockLock.DENY_SOUND, 0.8F, 1.0F);
        }
    }

    public static void showBlockLocked(ServerPlayer player, ItemStack blockItem) {
        if (CraftBlockLock.CONFIG.messagesEnabled) {
            player.sendOverlayMessage(
                Component.literal("Block locked: break your active " + blockItem.getHoverName().getString() + " placement before placing another.")
            );
        }
        if (CraftBlockLock.CONFIG.denialSoundsEnabled) {
            sendDenialSound(player, CraftBlockLock.DENY_SOUND, 0.8F, 1.0F);
        }
    }

    public static void syncLockState(ServerPlayer player) {
        LockSavedData data = LockSavedData.get(player.level().getServer());
        ServerPlayNetworking.send(player, new LockSyncPayload(
            data.getCraftedRecipes(player.getUUID()).stream()
                .filter(key -> !CraftBlockLock.CONFIG.isRecipeException(key))
                .collect(java.util.stream.Collectors.toSet()),
            data.getPlacedTypes(player.getUUID()).stream()
                .filter(key -> !CraftBlockLock.CONFIG.isBlockException(key))
                .collect(java.util.stream.Collectors.toSet()),
            CraftBlockLock.CONFIG.recipeLockEnabled,
            CraftBlockLock.CONFIG.blockLockEnabled,
            CraftBlockLock.CONFIG.messagesEnabled,
            CraftBlockLock.CONFIG.denialSoundsEnabled
        ));
    }

    public static void syncAllPlayers(net.minecraft.server.MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(LockManager::syncLockState);
    }

    public static String recipeKey(RecipeHolder<?> recipe) {
        return recipe.id().identifier().toString();
    }

    private static void clearMatchingProvenance(Inventory inventory, OperationKeys.Provenance provenance) {
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (OperationKeys.readProvenance(stack).filter(provenance::equals).isPresent()) {
                OperationKeys.clear(stack);
            }
        }
    }

    private static void sendDenialSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
            SoundSource.PLAYERS,
            player.getX(),
            player.getY(),
            player.getZ(),
            volume,
            pitch,
            player.getRandom().nextLong()
        ));
    }
}
