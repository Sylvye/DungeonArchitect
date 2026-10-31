package com.dungeonarchitect.runtime;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record DungeonRequest(int roomCount, long seed, Set<UUID> playerIds, com.dungeonarchitect.generation.TeamSpread spread) {
    public DungeonRequest { playerIds = Set.copyOf(playerIds); }
    public DungeonRequest(int roomCount, long seed, Set<UUID> playerIds) { this(roomCount, seed, playerIds, null); }
    public DungeonRequest(int roomCount, long seed, Collection<UUID> playerIds) {
        this(roomCount, seed, Set.copyOf(playerIds));
    }
}
