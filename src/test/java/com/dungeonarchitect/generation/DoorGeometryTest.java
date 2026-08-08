package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DoorGeometryTest {
    @Test
    void computesFullHorizontalDoorApertures() {
        assertEquals(
            new BoundingBox3i(new IntVector3(5, 1, 0), new IntVector3(7, 4, 0)),
            DoorGeometry.localBounds(new DoorSocket("north", new IntVector3(5, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4))
        );
        assertEquals(
            new BoundingBox3i(new IntVector3(0, 1, 5), new IntVector3(0, 4, 7)),
            DoorGeometry.localBounds(new DoorSocket("east", new IntVector3(0, 1, 5), Direction3.EAST, SocketType.STANDARD, 3, 4))
        );
    }
}
