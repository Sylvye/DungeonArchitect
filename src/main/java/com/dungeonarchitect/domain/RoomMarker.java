package com.dungeonarchitect.domain;

public record RoomMarker(String name, String type, IntVector3 position) {
    public RoomMarker {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Marker name is required");
        }
        if (type == null || type.isBlank()) {
            type = "generic";
        }
    }
}
