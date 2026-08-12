package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonEntityOwnershipTest {
    @Test
    void acceptsOnlyCurrentWellFormedDungeonOwners() {
        UUID active = UUID.randomUUID();
        UUID destroyed = UUID.randomUUID();

        assertTrue(DungeonEntityOwnership.hasCurrentOwner(active.toString(), Set.of(active)));
        assertFalse(DungeonEntityOwnership.hasCurrentOwner(destroyed.toString(), Set.of(active)));
        assertFalse(DungeonEntityOwnership.hasCurrentOwner("legacy-owner", Set.of(active)));
        assertFalse(DungeonEntityOwnership.hasCurrentOwner(null, Set.of(active)));
    }

    @Test
    void footprintClaimsOnlyEntitiesWithinPlacedRooms() {
        DungeonGraph graph = new DungeonGraph(List.of(
            node(0, new IntVector3(0, 80, 0), new IntVector3(4, 4, 4)),
            node(1, new IntVector3(10, 80, 0), new IntVector3(3, 3, 3))
        ), List.of());
        DungeonFootprint footprint = DungeonFootprint.from(graph);

        assertTrue(footprint.contains(new IntVector3(3, 83, 3)));
        assertTrue(footprint.contains(new IntVector3(10, 80, 0)));
        assertFalse(footprint.contains(new IntVector3(4, 80, 0)));
        assertFalse(footprint.contains(new IntVector3(10, 79, 0)));
    }

    private static DungeonNode node(int index, IntVector3 origin, IntVector3 size) {
        return new DungeonNode(index, "room_" + index, RoomCategory.GENERIC, index, new RoomTransform(origin, Rotation.NONE, size));
    }
}
