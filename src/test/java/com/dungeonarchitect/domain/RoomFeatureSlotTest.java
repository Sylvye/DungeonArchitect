package com.dungeonarchitect.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoomFeatureSlotTest {
    @Test
    void doesNotForceVirtualEmptyEntry() {
        RoomFeatureSlot slot = new RoomFeatureSlot(
            "slot",
            new IntVector3(0, 0, 0),
            new IntVector3(3, 3, 3),
            Direction3.NORTH,
            List.of(new FeatureSlotEntry("chest", 2))
        );

        assertEquals(List.of(new FeatureSlotEntry("chest", 2)), slot.entries());
    }

    @Test
    void allowsNoEntries() {
        RoomFeatureSlot slot = new RoomFeatureSlot(
            "slot",
            new IntVector3(0, 0, 0),
            new IntVector3(3, 3, 3),
            Direction3.NORTH,
            List.of()
        );

        assertEquals(List.of(), slot.entries());
    }
}
