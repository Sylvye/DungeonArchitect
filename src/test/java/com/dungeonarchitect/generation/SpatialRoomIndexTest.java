package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialRoomIndexTest {
    @Test
    void detectsOverlappingBoundsAcrossBuckets() {
        SpatialRoomIndex index = new SpatialRoomIndex();
        index.add(1, box(0, 0, 0, 40, 10, 40));

        assertTrue(index.intersects(box(35, 5, 35, 50, 12, 50)));
    }

    @Test
    void allowsAdjacentNonOverlappingBounds() {
        SpatialRoomIndex index = new SpatialRoomIndex();
        index.add(1, box(0, 0, 0, 4, 4, 4));

        assertFalse(index.intersects(box(5, 0, 0, 9, 4, 4)));
    }

    @Test
    void detectsInclusiveFaceOverlap() {
        SpatialRoomIndex index = new SpatialRoomIndex();
        index.add(1, box(0, 0, 0, 4, 4, 4));

        assertTrue(index.intersects(box(4, 0, 0, 8, 4, 4)));
    }

    @Test
    void allowsVerticallyStackedAdjacentBounds() {
        SpatialRoomIndex index = new SpatialRoomIndex();
        index.add(1, box(0, 0, 0, 4, 4, 4));

        assertFalse(index.intersects(box(0, 5, 0, 4, 9, 4)));
    }

    @Test
    void removeClearsIndexedBounds() {
        SpatialRoomIndex index = new SpatialRoomIndex();
        BoundingBox3i bounds = box(0, 0, 0, 4, 4, 4);
        index.add(1, bounds);
        index.remove(1);

        assertFalse(index.intersects(bounds));
    }

    private BoundingBox3i box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new BoundingBox3i(new IntVector3(minX, minY, minZ), new IntVector3(maxX, maxY, maxZ));
    }
}
