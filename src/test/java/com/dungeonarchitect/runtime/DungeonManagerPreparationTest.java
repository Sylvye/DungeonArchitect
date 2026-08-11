package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonManagerPreparationTest {
    @Test
    void collectsUniqueChunksAcrossOverlappingRoomBounds() {
        DungeonGraph graph = new DungeonGraph(List.of(
            node(0, new IntVector3(0, 80, 0), new IntVector3(20, 5, 20)),
            node(1, new IntVector3(16, 80, 0), new IntVector3(20, 5, 20))
        ), List.of());

        assertEquals(Set.of(
            new DungeonManager.ChunkCoordinate(0, 0),
            new DungeonManager.ChunkCoordinate(0, 1),
            new DungeonManager.ChunkCoordinate(1, 0),
            new DungeonManager.ChunkCoordinate(1, 1),
            new DungeonManager.ChunkCoordinate(2, 0),
            new DungeonManager.ChunkCoordinate(2, 1)
        ), DungeonManager.chunksFor(graph));
    }

    @Test
    void indexesEachEdgeAtBothConnectedNodes() {
        DungeonEdge first = new DungeonEdge(0, "east", 1, "west");
        DungeonEdge second = new DungeonEdge(1, "east", 2, "west");
        DungeonGraph graph = new DungeonGraph(List.of(
            node(0, IntVector3.ZERO, new IntVector3(3, 3, 3)),
            node(1, new IntVector3(4, 0, 0), new IntVector3(3, 3, 3)),
            node(2, new IntVector3(8, 0, 0), new IntVector3(3, 3, 3))
        ), List.of(first, second));

        var indexed = DungeonManager.indexEdges(graph);

        assertEquals(List.of(first), indexed.get(0));
        assertEquals(List.of(first, second), indexed.get(1));
        assertEquals(List.of(second), indexed.get(2));
    }

    @Test
    void adaptiveBudgetAlwaysAllowsOneRoomThenHonorsDeadline() {
        assertTrue(DungeonManager.canPlaceAnotherRoom(0, 20, 10));
        assertTrue(DungeonManager.canPlaceAnotherRoom(1, 9, 10));
        assertFalse(DungeonManager.canPlaceAnotherRoom(1, 10, 10));
    }

    private DungeonNode node(int index, IntVector3 origin, IntVector3 size) {
        return new DungeonNode(index, "room_" + index, index == 0 ? RoomCategory.START : RoomCategory.GENERIC, index, new RoomTransform(origin, Rotation.NONE, size));
    }
}
