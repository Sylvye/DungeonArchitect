package com.dungeonarchitect.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomFeatureSlotTest {
    @Test
    void preservesVirtualEmptyEntry() {
        RoomFeatureSlot slot = new RoomFeatureSlot(
            "slot",
            new IntVector3(0, 0, 0),
            new IntVector3(3, 3, 3),
            Direction3.NORTH,
            List.of(new FeatureSlotEntry("chest", 2))
        );

        assertEquals(2, slot.entries().size());
        assertTrue(slot.entries().stream().anyMatch(entry -> entry.featureId().equals(FeatureSlotEntry.EMPTY)));
        assertTrue(slot.entries().stream().anyMatch(entry -> entry.featureId().equals("chest") && entry.weight() == 2));
    }
}
