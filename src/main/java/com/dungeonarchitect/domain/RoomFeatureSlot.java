package com.dungeonarchitect.domain;

import java.util.List;

public record RoomFeatureSlot(
    String id,
    IntVector3 position,
    IntVector3 size,
    Direction3 facing,
    List<FeatureSlotEntry> entries
) {
    public RoomFeatureSlot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feature slot id is required");
        }
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Feature slot size must be positive");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public RoomFeatureSlot(String id, IntVector3 position, IntVector3 size, Direction3 facing) {
        this(id, position, size, facing, List.of(new FeatureSlotEntry(FeatureSlotEntry.EMPTY, 1)));
    }

    public RoomFeatureSlot(String id, String ignoredPool, IntVector3 position, Direction3 facing) {
        this(id, position, new IntVector3(1, 1, 1), facing);
    }

    public RoomFeatureSlot(String id, String ignoredPool, String ignoredFeature, IntVector3 position, Direction3 facing) {
        this(id, position, new IntVector3(1, 1, 1), facing);
    }

    public RoomFeatureSlot withEntries(List<FeatureSlotEntry> entries) {
        return new RoomFeatureSlot(id, position, size, facing, entries);
    }
}
