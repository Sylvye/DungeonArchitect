package com.dungeonarchitect.generation;

public record DungeonGenerationRequest(int roomCount, long seed, TeamSpread spread) {
    public DungeonGenerationRequest(int roomCount, long seed) { this(roomCount, seed, null); }
    public DungeonGenerationRequest {
        if (roomCount <= 0) {
            throw new IllegalArgumentException("roomCount must be positive");
        }
        if (spread != null && roomCount < spread.teams() + 1) throw new IllegalArgumentException("Room count includes the root and one room per team; increase it to at least " + (spread.teams() + 1));
    }
}
