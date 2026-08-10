package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.DungeonEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoorServiceTest {
    @Test
    void graphPlacementOnlyPlacesConnectedDoorSlots() {
        List<DungeonEdge> edges = List.of(new DungeonEdge(0, "north", "arch", 1, "south", "arch"));

        assertTrue(DoorService.shouldPlaceSlot("north", 0, edges));
        assertTrue(DoorService.shouldPlaceSlot("south", 1, edges));
        assertFalse(DoorService.shouldPlaceSlot("east", 0, edges));
    }

    @Test
    void directPlacementWithoutGraphContextKeepsLegacyRollingBehavior() {
        assertTrue(DoorService.shouldPlaceSlot("north", 0, List.of()));
    }
}
