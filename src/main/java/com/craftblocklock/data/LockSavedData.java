package com.craftblocklock.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class LockSavedData extends SavedData {
    private static final Codec<StoredPlacement> PLACEMENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("player").forGetter(StoredPlacement::player),
        Codec.STRING.fieldOf("type").forGetter(StoredPlacement::type),
        Codec.STRING.fieldOf("block").forGetter(StoredPlacement::block),
        Codec.STRING.fieldOf("dimension").forGetter(StoredPlacement::dimension),
        Codec.LONG.fieldOf("position").forGetter(StoredPlacement::position)
    ).apply(instance, StoredPlacement::new));

    public static final Codec<LockSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .optionalFieldOf("crafted_recipes", Map.of())
            .forGetter(LockSavedData::serializedRecipes),
        PLACEMENT_CODEC.listOf()
            .optionalFieldOf("active_placements", List.of())
            .forGetter(LockSavedData::serializedPlacements)
    ).apply(instance, LockSavedData::new));

    public static final SavedDataType<LockSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("craftblocklock", "locks"),
        LockSavedData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, Set<String>> craftedRecipes = new HashMap<>();
    private final Map<UUID, Map<String, Map<String, StoredPlacement>>> placementsByPlayer = new HashMap<>();
    private final Map<String, StoredPlacement> placementsByPosition = new HashMap<>();

    public LockSavedData() {
    }

    private LockSavedData(Map<String, List<String>> recipes, List<StoredPlacement> placements) {
        recipes.forEach((playerId, recipeIds) -> parseUuid(playerId)
            .ifPresent(uuid -> craftedRecipes.put(uuid, new HashSet<>(recipeIds))));

        for (StoredPlacement placement : placements) {
            parseUuid(placement.player()).ifPresent(uuid -> addPlacement(uuid, placement));
        }
    }

    public static LockSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isRecipeLocked(UUID playerId, String recipeId) {
        return craftedRecipes.getOrDefault(playerId, Set.of()).contains(recipeId);
    }

    public Set<String> getCraftedRecipes(UUID playerId) {
        return Set.copyOf(craftedRecipes.getOrDefault(playerId, Set.of()));
    }

    public void lockRecipe(UUID playerId, String recipeId) {
        if (craftedRecipes.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(recipeId)) {
            setDirty();
        }
    }

    public Optional<StoredPlacement> getPlacement(UUID playerId, String typeId) {
        return getPlacements(playerId, typeId).stream().findFirst();
    }

    public List<StoredPlacement> getPlacements(UUID playerId, String typeId) {
        Map<String, StoredPlacement> placements = placementsByPlayer
            .getOrDefault(playerId, Map.of())
            .get(typeId);
        return placements == null ? List.of() : List.copyOf(placements.values());
    }

    public List<StoredPlacement> getPlacements(UUID playerId) {
        return placementsByPlayer.getOrDefault(playerId, Map.of()).values().stream()
            .flatMap(placements -> placements.values().stream())
            .toList();
    }

    public Optional<StoredPlacement> getPlacementAt(String dimension, long position) {
        return Optional.ofNullable(placementsByPosition.get(positionKey(dimension, position)));
    }

    public Set<String> getPlacedTypes(UUID playerId) {
        return Set.copyOf(placementsByPlayer.getOrDefault(playerId, Map.of()).keySet());
    }

    public void recordPlacement(UUID playerId, String typeId, String blockId, String dimension, long position) {
        removePlacementAtInternal(dimension, position);
        StoredPlacement placement = new StoredPlacement(playerId.toString(), typeId, blockId, dimension, position);
        addPlacement(playerId, placement);
        setDirty();
    }

    public Optional<StoredPlacement> removePlacementAt(String dimension, long position) {
        StoredPlacement placement = removePlacementAtInternal(dimension, position);
        if (placement == null) {
            return Optional.empty();
        }
        setDirty();
        return Optional.of(placement);
    }

    public void removePlacement(UUID playerId, String typeId) {
        Map<String, Map<String, StoredPlacement>> playerPlacements = placementsByPlayer.get(playerId);
        if (playerPlacements == null) {
            return;
        }

        Map<String, StoredPlacement> removed = playerPlacements.remove(typeId);
        if (removed != null) {
            removed.values().forEach(placement ->
                placementsByPosition.remove(positionKey(placement.dimension(), placement.position()), placement)
            );
            if (playerPlacements.isEmpty()) {
                placementsByPlayer.remove(playerId);
            }
            setDirty();
        }
    }

    public int clearRecipes(UUID playerId) {
        Set<String> removed = craftedRecipes.remove(playerId);
        if (removed == null) {
            return 0;
        }
        setDirty();
        return removed.size();
    }

    public int clearPlacements(UUID playerId) {
        Map<String, Map<String, StoredPlacement>> removed = placementsByPlayer.remove(playerId);
        if (removed == null) {
            return 0;
        }
        List<StoredPlacement> placements = removed.values().stream()
            .flatMap(typePlacements -> typePlacements.values().stream())
            .toList();
        placements.forEach(placement ->
            placementsByPosition.remove(positionKey(placement.dimension(), placement.position()), placement)
        );
        setDirty();
        return placements.size();
    }

    private Map<String, List<String>> serializedRecipes() {
        Map<String, List<String>> serialized = new HashMap<>();
        craftedRecipes.forEach((uuid, recipes) -> serialized.put(uuid.toString(), new ArrayList<>(recipes)));
        return serialized;
    }

    private List<StoredPlacement> serializedPlacements() {
        return placementsByPlayer.values().stream()
            .flatMap(types -> types.values().stream())
            .flatMap(placements -> placements.values().stream())
            .toList();
    }

    private void addPlacement(UUID playerId, StoredPlacement placement) {
        String key = positionKey(placement.dimension(), placement.position());
        StoredPlacement oldPlacement = placementsByPosition.put(key, placement);
        if (oldPlacement != null) {
            removeFromPlayerIndex(oldPlacement, key);
        }

        placementsByPlayer
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(placement.type(), ignored -> new HashMap<>())
            .put(key, placement);
    }

    private StoredPlacement removePlacementAtInternal(String dimension, long position) {
        String key = positionKey(dimension, position);
        StoredPlacement placement = placementsByPosition.remove(key);
        if (placement != null) {
            removeFromPlayerIndex(placement, key);
        }
        return placement;
    }

    private void removeFromPlayerIndex(StoredPlacement placement, String positionKey) {
        parseUuid(placement.player()).ifPresent(uuid -> {
            Map<String, Map<String, StoredPlacement>> playerPlacements = placementsByPlayer.get(uuid);
            if (playerPlacements == null) {
                return;
            }

            Map<String, StoredPlacement> typePlacements = playerPlacements.get(placement.type());
            if (typePlacements != null) {
                typePlacements.remove(positionKey, placement);
                if (typePlacements.isEmpty()) {
                    playerPlacements.remove(placement.type());
                }
            }
            if (playerPlacements.isEmpty()) {
                placementsByPlayer.remove(uuid);
            }
        });
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String positionKey(String dimension, long position) {
        return dimension + "|" + position;
    }

    public record StoredPlacement(String player, String type, String block, String dimension, long position) {
    }
}
