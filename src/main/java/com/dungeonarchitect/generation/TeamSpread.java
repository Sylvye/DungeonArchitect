package com.dungeonarchitect.generation;

import java.util.Set;

/** Explicit template selection and constraints for team-spread generation. */
public record TeamSpread(String rootId, Set<String> ordinaryIds, String spawnId, int teams,
                         int minimumDistance, double maximumRatio) {
    public TeamSpread {
        ordinaryIds = Set.copyOf(ordinaryIds);
        if (rootId == null || rootId.isBlank() || spawnId == null || spawnId.isBlank() || ordinaryIds.isEmpty()) throw new IllegalArgumentException("Select a root, ordinary rooms, and spawn template");
        if (rootId.equals(spawnId) || ordinaryIds.contains(rootId) || ordinaryIds.contains(spawnId)) throw new IllegalArgumentException("Root, ordinary, and spawn pools must be separate");
        if (teams < 2 || teams > 8) throw new IllegalArgumentException("Use 2–8 teams");
        if (minimumDistance < 1 || !Double.isFinite(maximumRatio) || maximumRatio < 1) throw new IllegalArgumentException("Invalid spread distances");
    }
}
