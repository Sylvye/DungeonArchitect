package com.dungeonarchitect.domain;

public record RoomFeatureSlot(String id, String poolId, String featureName, IntVector3 position, Direction3 facing) {
    public RoomFeatureSlot(String id, String poolId, IntVector3 position, Direction3 facing) {
        this(id, poolId, poolId, position, facing);
    }

    public RoomFeatureSlot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feature slot id is required");
        }
        if (poolId == null || poolId.isBlank()) {
            poolId = "default";
        }
        if (featureName == null || featureName.isBlank()) {
            featureName = poolId;
        }
    }
}
