package com.dungeonarchitect.minigame;

import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.generation.SpreadReport;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/** Live view. Mutations go through MinigameService on the server thread. */
public final class MinigameRun {
    private final UUID id = UUID.randomUUID();
    private final long seed;
    private final MinigamePreset preset;
    final Map<UUID, Integer> teams = new LinkedHashMap<>();
    final Map<Integer, Integer> spawnRooms = new LinkedHashMap<>();
    final Map<UUID, Participant> participants = new LinkedHashMap<>();
    final Map<String, Map<String, ItemStack>> kitContents = new LinkedHashMap<>();
    List<String> kits;
    RunState state = RunState.DRAFT;
    DungeonInstance dungeon;
    SpreadReport spread;
    int remaining;
    long seconds;
    boolean autoStart;
    boolean started;
    String message = "";

    MinigameRun(MinigamePreset preset, long seed) { this.preset = preset; this.seed = seed; kits = preset.kits(); }
    public UUID id() { return id; }
    public long seed() { return seed; }
    public MinigamePreset preset() { return preset; }
    public RunState state() { return state; }
    public Map<UUID, Integer> teams() { return Collections.unmodifiableMap(new LinkedHashMap<>(teams)); }
    public Map<Integer, Integer> spawnRooms() { return Collections.unmodifiableMap(new LinkedHashMap<>(spawnRooms)); }
    public List<String> kits() { return List.copyOf(kits); }
    public Optional<DungeonInstance> dungeon() { return Optional.ofNullable(dungeon); }
    public Optional<SpreadReport> spread() { return Optional.ofNullable(spread); }
    public String message() { return message; }
    public boolean eliminated(UUID player) { Participant p = participants.get(player); return p != null && p.eliminated; }
    public boolean frozen() { return started && (state == RunState.PREPARING || state == RunState.COUNTDOWN); }
    public boolean contains(Location location) {
        if (dungeon == null || location.getWorld() == null || !location.getWorld().getName().equals(dungeon.worldName())) return false;
        var position = new com.dungeonarchitect.domain.IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return dungeon.rooms().stream().anyMatch(room -> room.bounds().contains(position));
    }

    static final class Participant {
        final UUID id;
        Location spawn;
        Location frozenAt;
        PlayerSnapshot matchState;
        long disconnectedAt = -1;
        String kit;
        boolean entered, equipped, eliminated, departed;
        Participant(UUID id) { this.id = id; }
    }
}
