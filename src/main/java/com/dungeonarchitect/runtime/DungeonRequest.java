package com.dungeonarchitect.runtime;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record DungeonRequest(int roomCount, long seed, Set<UUID> playerIds) {
    public DungeonRequest(int roomCount, long seed, Collection<UUID> playerIds) {
        this(roomCount, seed, Set.copyOf(playerIds));
    }
}
