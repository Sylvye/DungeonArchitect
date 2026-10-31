package com.dungeonarchitect.minigame;

import java.util.*;

public final class TeamAssignments {
    private TeamAssignments() { }
    public static Map<UUID, Integer> balanced(Collection<UUID> players, int teamCount, long seed) {
        if (teamCount < 2 || teamCount > 8 || players.size() < teamCount || players.size() > teamCount * 4) throw new IllegalArgumentException("Use 2–8 teams, with 1–4 players per team");
        List<UUID> shuffled = new ArrayList<>(new TreeSet<>(players));
        if (shuffled.size() != players.size()) throw new IllegalArgumentException("Duplicate players");
        Collections.shuffle(shuffled, new Random(seed));
        Map<UUID, Integer> teams = new LinkedHashMap<>();
        for (int i = 0; i < shuffled.size(); i++) teams.put(shuffled.get(i), i % teamCount + 1);
        return teams;
    }
    public static void validate(Map<UUID, Integer> teams) {
        Map<Integer, Long> counts = teams.values().stream().collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.stream.Collectors.counting()));
        if (counts.size() < 2 || counts.size() > 8 || counts.keySet().stream().anyMatch(t -> t < 1 || t > 8)
            || counts.values().stream().anyMatch(n -> n > 4)) throw new IllegalArgumentException("Use 2–8 nonempty teams, with 1–4 players per team");
    }
}
