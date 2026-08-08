package com.dungeonarchitect.domain;

public record DoorSocket(
    String id,
    IntVector3 position,
    Direction3 facing,
    SocketType socketType,
    int width,
    int height
) {
    public DoorSocket {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Door id is required");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Door dimensions must be positive");
        }
    }

    public boolean compatibleWith(DoorSocket other) {
        return socketType.compatibleWith(other.socketType);
    }
}
