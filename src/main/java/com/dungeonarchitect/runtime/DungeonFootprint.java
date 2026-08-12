package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.IntVector3;

import java.util.List;

/** Immutable room bounds used to associate spawned entities with a dungeon. */
final class DungeonFootprint {
    private final List<BoundingBox3i> roomBounds;

    private DungeonFootprint(List<BoundingBox3i> roomBounds) {
        this.roomBounds = List.copyOf(roomBounds);
    }

    static DungeonFootprint from(DungeonGraph graph) {
        return new DungeonFootprint(graph.nodes().stream()
            .map(node -> node.transform().transformedBounds())
            .toList());
    }

    boolean contains(IntVector3 position) {
        return roomBounds.stream().anyMatch(bounds -> bounds.contains(position));
    }
}
