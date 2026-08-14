package com.dungeonarchitect.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.dungeonarchitect.loot.LootBinding;

public record RoomTemplate(
    String id,
    RoomCategory category,
    int weight,
    int minimumConnections,
    Set<String> tags,
    IntVector3 size,
    IntVector3 spawn,
    List<DoorSocket> doors,
    List<RoomMarker> markers,
    List<RoomFeatureSlot> featureSlots,
    Map<String, LootBinding> lootBindings,
    Path structureFile
) {
    public RoomTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Room id is required");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Room weight must be positive");
        }
        if (minimumConnections < 0) {
            throw new IllegalArgumentException("Minimum room connections cannot be negative");
        }
        tags = Set.copyOf(tags);
        doors = List.copyOf(doors);
        markers = List.copyOf(markers);
        featureSlots = List.copyOf(featureSlots);
        lootBindings = lootBindings == null ? Map.of() : Map.copyOf(lootBindings);
    }

    public RoomTemplate(
        String id,
        RoomCategory category,
        int weight,
        Set<String> tags,
        IntVector3 size,
        IntVector3 spawn,
        List<DoorSocket> doors,
        List<RoomMarker> markers,
        List<RoomFeatureSlot> featureSlots,
        Path structureFile
    ) {
        this(id, category, weight, 0, tags, size, spawn, doors, markers, featureSlots, Map.of(), structureFile);
    }

    public RoomTemplate(String id, RoomCategory category, int weight, int minimumConnections, Set<String> tags, IntVector3 size, IntVector3 spawn, List<DoorSocket> doors, List<RoomMarker> markers, List<RoomFeatureSlot> featureSlots, Path structureFile) {
        this(id, category, weight, minimumConnections, tags, size, spawn, doors, markers, featureSlots, Map.of(), structureFile);
    }
}
