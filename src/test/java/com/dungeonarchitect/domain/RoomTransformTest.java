package com.dungeonarchitect.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomTransformTest {
    @Test
    void rotatesLocalCoordinatesAroundRoomOrigin() {
        IntVector3 size = new IntVector3(4, 3, 6);

        assertEquals(new IntVector3(5, 1, 2), Rotation.CLOCKWISE_90.rotate(new IntVector3(2, 1, 0), size));
        assertEquals(new IntVector3(1, 1, 0), Rotation.CLOCKWISE_180.rotate(new IntVector3(2, 1, 5), size));
        assertEquals(new IntVector3(1, 1, 1), Rotation.COUNTERCLOCKWISE_90.rotate(new IntVector3(2, 1, 1), size));
    }

    @Test
    void rotatesHorizontalDirections() {
        assertEquals(Direction3.EAST, Direction3.NORTH.rotateY(Rotation.CLOCKWISE_90));
        assertEquals(Direction3.SOUTH, Direction3.NORTH.rotateY(Rotation.CLOCKWISE_180));
        assertEquals(Direction3.WEST, Direction3.NORTH.rotateY(Rotation.COUNTERCLOCKWISE_90));
        assertEquals(Direction3.UP, Direction3.UP.rotateY(Rotation.CLOCKWISE_90));
    }

    @Test
    void calculatesTransformedBounds() {
        RoomTransform transform = new RoomTransform(new IntVector3(10, 80, -5), Rotation.CLOCKWISE_90, new IntVector3(4, 3, 6));

        assertEquals(new IntVector3(6, 3, 4), transform.transformedSize());
        assertEquals(new BoundingBox3i(new IntVector3(10, 80, -5), new IntVector3(15, 82, -2)), transform.transformedBounds());
    }

    @Test
    void detectsInclusiveBoxCollision() {
        BoundingBox3i a = BoundingBox3i.fromMinAndSize(new IntVector3(0, 0, 0), new IntVector3(4, 4, 4));
        BoundingBox3i b = BoundingBox3i.fromMinAndSize(new IntVector3(3, 0, 0), new IntVector3(4, 4, 4));
        BoundingBox3i c = BoundingBox3i.fromMinAndSize(new IntVector3(4, 0, 0), new IntVector3(4, 4, 4));

        assertTrue(a.intersects(b));
        assertFalse(a.intersects(c));
    }
}
