package com.dungeonarchitect.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record DoorTemplate(
    String id,
    IntVector3 size,
    Set<String> tags,
    List<RoomMarker> markers,
    List<RoomFeatureSlot> featureSlots,
    DoorGateway gateway,
    Path structureFile
) {
    public DoorTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Door id is required");
        }
        if (id.equalsIgnoreCase(DoorSlotEntry.EMPTY)) {
            throw new IllegalArgumentException("empty is reserved");
        }
        id = id.toLowerCase(java.util.Locale.ROOT);
        if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Door size must be positive");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        markers = markers == null ? List.of() : List.copyOf(markers);
        featureSlots = featureSlots == null ? List.of() : List.copyOf(featureSlots);
    }
}
