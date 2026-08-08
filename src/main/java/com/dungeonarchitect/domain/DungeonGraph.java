package com.dungeonarchitect.domain;

import java.util.List;

public record DungeonGraph(List<DungeonNode> nodes, List<DungeonEdge> edges) {
    public DungeonGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public DungeonNode startNode() {
        return nodes.stream()
            .filter(node -> node.category() == RoomCategory.START)
            .findFirst()
            .orElse(nodes.getFirst());
    }
}
