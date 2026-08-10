package com.dungeonarchitect.door;

import com.dungeonarchitect.authoring.SelectionBounds;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BoundaryFacingTest {
    private static final SelectionBounds PARENT = new SelectionBounds(new IntVector3(0, 0, 0), new IntVector3(20, 9, 20));

    @Test
    void infersHorizontalFaces() {
        assertEquals(Direction3.NORTH, BoundaryFacing.infer(bounds(8, 2, 0, 10, 5, 0), PARENT, "Door slot"));
        assertEquals(Direction3.SOUTH, BoundaryFacing.infer(bounds(8, 2, 20, 10, 5, 20), PARENT, "Door slot"));
        assertEquals(Direction3.EAST, BoundaryFacing.infer(bounds(20, 2, 8, 20, 5, 10), PARENT, "Door slot"));
        assertEquals(Direction3.WEST, BoundaryFacing.infer(bounds(0, 2, 8, 0, 5, 10), PARENT, "Door slot"));
    }

    @Test
    void dominantHorizontalFaceWinsWhenTouchingACorner() {
        assertEquals(Direction3.NORTH, BoundaryFacing.infer(bounds(0, 2, 0, 6, 5, 0), PARENT, "Door slot"));
        assertEquals(Direction3.WEST, BoundaryFacing.infer(bounds(0, 2, 0, 2, 5, 8), PARENT, "Door slot"));
    }

    @Test
    void infersVerticalFaces() {
        assertEquals(Direction3.UP, BoundaryFacing.infer(bounds(8, 9, 8, 10, 9, 10), PARENT, "Door slot"));
        assertEquals(Direction3.DOWN, BoundaryFacing.infer(bounds(8, 0, 8, 10, 0, 10), PARENT, "Door slot"));
    }

    @Test
    void dominantVerticalFaceCanWinWhenTouchingACorner() {
        assertEquals(Direction3.DOWN, BoundaryFacing.infer(bounds(0, 0, 0, 2, 0, 8), PARENT, "Door slot"));
    }

    @Test
    void rejectsNonTouchingAndTiedSelections() {
        assertThrows(IllegalArgumentException.class, () -> BoundaryFacing.infer(bounds(8, 2, 8, 10, 5, 8), PARENT, "Door slot"));
        assertThrows(IllegalArgumentException.class, () -> BoundaryFacing.infer(bounds(0, 2, 0, 2, 5, 2), PARENT, "Door slot"));
        assertThrows(IllegalArgumentException.class, () -> BoundaryFacing.infer(bounds(0, 0, 0, 2, 2, 2), PARENT, "Door slot"));
    }

    private static SelectionBounds bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new SelectionBounds(new IntVector3(minX, minY, minZ), new IntVector3(maxX, maxY, maxZ));
    }
}
