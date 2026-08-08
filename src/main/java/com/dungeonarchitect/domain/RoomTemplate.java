package com.dungeonarchitect.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record RoomTemplate(
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
    public RoomTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Room id is required");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Room weight must be positive");
        }
        tags = Set.copyOf(tags);
        doors = List.copyOf(doors);
        markers = List.copyOf(markers);
        featureSlots = List.copyOf(featureSlots);
    }
}
