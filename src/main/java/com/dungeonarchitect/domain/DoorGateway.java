package com.dungeonarchitect.domain;

public record DoorGateway(IntVector3 position, IntVector3 size, Direction3 facing) {
    public DoorGateway {
        if (position == null) {
            throw new IllegalArgumentException("Gateway position is required");
        }
        if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Gateway size must be positive");
        }
        if (facing == null || facing == Direction3.UP || facing == Direction3.DOWN) {
            throw new IllegalArgumentException("Gateway facing must be horizontal");
        }
    }
}
