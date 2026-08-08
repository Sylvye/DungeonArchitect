package com.dungeonarchitect.generation;

public record DungeonGenerationRequest(int roomCount, long seed) {
    public DungeonGenerationRequest {
        if (roomCount <= 0) {
            throw new IllegalArgumentException("roomCount must be positive");
        }
    }
}
