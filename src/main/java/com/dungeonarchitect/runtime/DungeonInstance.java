package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonState;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DungeonInstance {
    private final UUID id;
    private final long seed;
    private final DungeonGraph graph;
    private final String worldName;
    private final Set<UUID> playerIds;
    private final List<RoomInstance> rooms;
    private final Instant createdAt;
    private DungeonState state;

    public DungeonInstance(UUID id, long seed, DungeonGraph graph, String worldName, Set<UUID> playerIds, List<RoomInstance> rooms, DungeonState state) {
        this.id = id;
        this.seed = seed;
        this.graph = graph;
        this.worldName = worldName;
        this.playerIds = Set.copyOf(playerIds);
        this.rooms = List.copyOf(rooms);
        this.state = state;
        this.createdAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public DungeonGraph graph() {
        return graph;
    }

    public String worldName() {
        return worldName;
    }

    public Set<UUID> playerIds() {
        return playerIds;
    }

    public List<RoomInstance> rooms() {
        return rooms;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public DungeonState state() {
        return state;
    }

    public void state(DungeonState state) {
        this.state = state;
    }
}
