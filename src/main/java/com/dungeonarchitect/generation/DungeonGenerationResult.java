package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.DungeonGraph;

import java.util.List;

public record DungeonGenerationResult(DungeonGraph graph, List<String> errors) {
    public static DungeonGenerationResult success(DungeonGraph graph) {
        return new DungeonGenerationResult(graph, List.of());
    }

    public static DungeonGenerationResult failure(String error) {
        return new DungeonGenerationResult(null, List.of(error));
    }

    public boolean successful() {
        return graph != null && errors.isEmpty();
    }
}
