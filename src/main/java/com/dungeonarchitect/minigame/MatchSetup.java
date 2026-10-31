package com.dungeonarchitect.minigame;

import java.util.*;

/** Disposable settings, deliberately not registered as a dungeon run. */
public final class MatchSetup {
    private final MinigamePreset preset;
    private final long seed;
    private final Map<UUID, Integer> teams = new LinkedHashMap<>();
    private List<String> kits;
    private int teamCount = 2;

    public MatchSetup(MinigamePreset preset, long seed) { this.preset = preset; this.seed = seed; kits = preset.kits(); }
    public MinigamePreset preset() { return preset; }
    public long seed() { return seed; }
    public Map<UUID, Integer> teams() { return Collections.unmodifiableMap(new LinkedHashMap<>(teams)); }
    public List<String> kits() { return kits; }
    public void kits(List<String> ids) { kits = List.copyOf(new LinkedHashSet<>(ids)); }
    public int teamCount() { return teamCount; }
    public void teamCount(int count) {
        if (count < 2 || count > 8) throw new IllegalArgumentException("Choose 2–8 teams");
        teamCount = count;
    }
    public void assign(UUID player, int team) {
        if (team < 0 || team > 8) throw new IllegalArgumentException("Choose team 1–8, or 0 to remove a player");
        if (team == 0) teams.remove(player); else teams.put(player, team);
    }
    public void balance(Collection<UUID> players) {
        Map<UUID, Integer> balanced = balanced(players, teamCount, seed);
        teams.clear(); teams.putAll(balanced);
    }
    static Map<UUID, Integer> balanced(Collection<UUID> players, int count, long seed) {
        if (players.size() < count) throw new IllegalArgumentException("Select at least " + count + " players, or choose fewer teams");
        if (players.size() > count * 4) throw new IllegalArgumentException(players.size() + " players need at least " + ((players.size() + 3) / 4) + " teams (maximum 8). Increase Teams before balancing.");
        return TeamAssignments.balanced(players, count, seed);
    }
}
