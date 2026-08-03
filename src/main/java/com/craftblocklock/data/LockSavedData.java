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

    private static final Codec<StoredFallingPlacement> FALLING_PLACEMENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("player").forGetter(StoredFallingPlacement::player),
        Codec.STRING.fieldOf("type").forGetter(StoredFallingPlacement::type),
        Codec.STRING.fieldOf("block").forGetter(StoredFallingPlacement::block),
        Codec.STRING.fieldOf("dimension").forGetter(StoredFallingPlacement::dimension),
        Codec.STRING.fieldOf("entity").forGetter(StoredFallingPlacement::entity)
    ).apply(instance, StoredFallingPlacement::new));

    public static final Codec<LockSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .optionalFieldOf("crafted_recipes", Map.of())
            .forGetter(LockSavedData::serializedRecipes),
        PLACEMENT_CODEC.listOf()
            .optionalFieldOf("active_placements", List.of())
            .forGetter(LockSavedData::serializedPlacements),
        FALLING_PLACEMENT_CODEC.listOf()
            .optionalFieldOf("falling_placements", List.of())
            .forGetter(LockSavedData::serializedFallingPlacements)
    ).apply(instance, LockSavedData::new));

    public static final SavedDataType<LockSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("craftblocklock", "locks"),
        LockSavedData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, Set<String>> craftedRecipes = new HashMap<>();
    private final Map<UUID, Map<String, Map<String, StoredPlacement>>> placementsByPlayer = new HashMap<>();
    private final Map<String, Map<Long, StoredPlacement>> placementsByPosition = new HashMap<>();
    private final Map<UUID, StoredFallingPlacement> fallingPlacementsByEntity = new HashMap<>();
    private final Map<UUID, Map<String, Set<UUID>>> fallingEntitiesByPlayer = new HashMap<>();

    public LockSavedData() {
    }

    private LockSavedData(
        Map<String, List<String>> recipes,
        List<StoredPlacement> placements,
        List<StoredFallingPlacement> fallingPlacements
    ) {
        recipes.forEach((playerId, recipeIds) -> parseUuid(playerId)
            .ifPresent(uuid -> craftedRecipes.put(uuid, new HashSet<>(recipeIds))));

        for (StoredPlacement placement : placements) {
            parseUuid(placement.player()).ifPresent(uuid -> addPlacement(uuid, placement));
        }

        for (StoredFallingPlacement placement : fallingPlacements) {
            parseUuid(placement.player()).ifPresent(playerId ->
                parseUuid(placement.entity()).ifPresent(entityId -> addFallingPlacement(playerId, entityId, placement))
            );
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

    public List<StoredPlacement> getAllPlacements() {
        return placementsByPosition.values().stream()
            .flatMap(placements -> placements.values().stream())
            .toList();
    }

    public Optional<StoredPlacement> getPlacementAt(String dimension, long position) {
        return Optional.ofNullable(placementsByPosition.getOrDefault(dimension, Map.of()).get(position));
    }

    public Optional<StoredFallingPlacement> getFallingPlacement(UUID entityId) {
        return Optional.ofNullable(fallingPlacementsByEntity.get(entityId));
    }

    public boolean hasPlacedType(UUID playerId, String typeId) {
        Map<String, Map<String, StoredPlacement>> active = placementsByPlayer.get(playerId);
        if (active != null && active.containsKey(typeId)) {
            return true;
        }
        Map<String, Set<UUID>> falling = fallingEntitiesByPlayer.get(playerId);
        return falling != null && falling.containsKey(typeId);
    }

    public Set<String> getPlacedTypes(UUID playerId) {
        Set<String> types = new HashSet<>(placementsByPlayer.getOrDefault(playerId, Map.of()).keySet());
        types.addAll(fallingEntitiesByPlayer.getOrDefault(playerId, Map.of()).keySet());
        return Set.copyOf(types);
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

    public Optional<StoredFallingPlacement> movePlacementToFalling(String dimension, long position, UUID entityId) {
        StoredPlacement placement = removePlacementAtInternal(dimension, position);
        if (placement == null) {
            return Optional.empty();
        }

        Optional<UUID> playerId = parseUuid(placement.player());
        if (playerId.isEmpty()) {
            setDirty();
            return Optional.empty();
        }

        StoredFallingPlacement fallingPlacement = new StoredFallingPlacement(
            placement.player(), placement.type(), placement.block(), placement.dimension(), entityId.toString()
        );
        addFallingPlacement(playerId.get(), entityId, fallingPlacement);
        setDirty();
        return Optional.of(fallingPlacement);
    }

    public Optional<StoredFallingPlacement> removeFallingPlacement(UUID entityId) {
        StoredFallingPlacement placement = fallingPlacementsByEntity.remove(entityId);
        if (placement == null) {
            return Optional.empty();
        }

        removeFromFallingPlayerIndex(placement, entityId);
        setDirty();
        return Optional.of(placement);
    }

    public void removePlacement(UUID playerId, String typeId) {
        Map<String, Map<String, StoredPlacement>> playerPlacements = placementsByPlayer.get(playerId);
        Map<String, StoredPlacement> removed = playerPlacements == null ? null : playerPlacements.remove(typeId);
        boolean changed = false;
        if (removed != null) {
            removed.values().forEach(placement ->
                removeFromPositionIndex(placement)
            );
            if (playerPlacements.isEmpty()) {
                placementsByPlayer.remove(playerId);
            }
            changed = true;
        }

        Map<String, Set<UUID>> fallingTypes = fallingEntitiesByPlayer.get(playerId);
        Set<UUID> fallingEntities = fallingTypes == null ? null : fallingTypes.remove(typeId);
        if (fallingEntities != null) {
            fallingEntities.forEach(fallingPlacementsByEntity::remove);
            if (fallingTypes.isEmpty()) {
                fallingEntitiesByPlayer.remove(playerId);
            }
            changed = true;
        }

        if (changed) {
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
        List<StoredPlacement> placements = removed == null ? List.of() : removed.values().stream()
            .flatMap(typePlacements -> typePlacements.values().stream())
            .toList();
        placements.forEach(placement ->
            removeFromPositionIndex(placement)
        );
        Map<String, Set<UUID>> removedFalling = fallingEntitiesByPlayer.remove(playerId);
        int fallingCount = 0;
        if (removedFalling != null) {
            Set<UUID> entityIds = new HashSet<>();
            removedFalling.values().forEach(entityIds::addAll);
            entityIds.forEach(fallingPlacementsByEntity::remove);
            fallingCount = entityIds.size();
        }

        int removedCount = placements.size() + fallingCount;
        if (removedCount > 0) {
            setDirty();
        }
        return removedCount;
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

    private List<StoredFallingPlacement> serializedFallingPlacements() {
        return List.copyOf(fallingPlacementsByEntity.values());
    }

    private void addPlacement(UUID playerId, StoredPlacement placement) {
        String key = positionKey(placement.dimension(), placement.position());
        StoredPlacement oldPlacement = placementsByPosition
            .computeIfAbsent(placement.dimension(), ignored -> new HashMap<>())
            .put(placement.position(), placement);
        if (oldPlacement != null) {
            removeFromPlayerIndex(oldPlacement, key);
        }

        placementsByPlayer
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(placement.type(), ignored -> new HashMap<>())
            .put(key, placement);
    }

    private void addFallingPlacement(UUID playerId, UUID entityId, StoredFallingPlacement placement) {
        StoredFallingPlacement oldPlacement = fallingPlacementsByEntity.put(entityId, placement);
        if (oldPlacement != null) {
            removeFromFallingPlayerIndex(oldPlacement, entityId);
        }

        fallingEntitiesByPlayer
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(placement.type(), ignored -> new HashSet<>())
            .add(entityId);
    }

    private StoredPlacement removePlacementAtInternal(String dimension, long position) {
        String key = positionKey(dimension, position);
        Map<Long, StoredPlacement> dimensionPlacements = placementsByPosition.get(dimension);
        StoredPlacement placement = dimensionPlacements == null ? null : dimensionPlacements.remove(position);
        if (placement != null) {
            removeFromPlayerIndex(placement, key);
        }
        if (dimensionPlacements != null && dimensionPlacements.isEmpty()) {
            placementsByPosition.remove(dimension);
        }
        return placement;
    }

    private void removeFromPositionIndex(StoredPlacement placement) {
        Map<Long, StoredPlacement> dimensionPlacements = placementsByPosition.get(placement.dimension());
        if (dimensionPlacements == null) {
            return;
        }
        dimensionPlacements.remove(placement.position(), placement);
        if (dimensionPlacements.isEmpty()) {
            placementsByPosition.remove(placement.dimension());
        }
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

    private void removeFromFallingPlayerIndex(StoredFallingPlacement placement, UUID entityId) {
        parseUuid(placement.player()).ifPresent(playerId -> {
            Map<String, Set<UUID>> playerPlacements = fallingEntitiesByPlayer.get(playerId);
            if (playerPlacements == null) {
                return;
            }

            Set<UUID> typePlacements = playerPlacements.get(placement.type());
            if (typePlacements != null) {
                typePlacements.remove(entityId);
                if (typePlacements.isEmpty()) {
                    playerPlacements.remove(placement.type());
                }
            }
            if (playerPlacements.isEmpty()) {
                fallingEntitiesByPlayer.remove(playerId);
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

    public record StoredFallingPlacement(String player, String type, String block, String dimension, String entity) {
    }
}
