package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoomStructurePlacerTest {
    @Test
    void structureIntegrityAlwaysPastesEveryBlock() {
        assertEquals(1.0f, RoomStructurePlacer.STRUCTURE_INTEGRITY);
    }

    @Test
    void compensatesPaperRotationOriginToMatchNormalizedTransform() {
        IntVector3 size = new IntVector3(5, 4, 7);
        IntVector3 origin = new IntVector3(10, 80, 20);

        assertEquals(origin, RoomStructurePlacer.pasteOrigin(new RoomTransform(origin, Rotation.NONE, size)));
        assertEquals(new IntVector3(16, 80, 20), RoomStructurePlacer.pasteOrigin(new RoomTransform(origin, Rotation.CLOCKWISE_90, size)));
        assertEquals(new IntVector3(14, 80, 26), RoomStructurePlacer.pasteOrigin(new RoomTransform(origin, Rotation.CLOCKWISE_180, size)));
        assertEquals(new IntVector3(10, 80, 24), RoomStructurePlacer.pasteOrigin(new RoomTransform(origin, Rotation.COUNTERCLOCKWISE_90, size)));
    }
}
