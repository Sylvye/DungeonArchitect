package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SelectionBoundsTest {
    @Test
    void normalizesCornersAndKeepsBothClickedBlocksInclusive() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(10, 70, -2), new IntVector3(7, 72, 3));

        assertEquals(new IntVector3(7, 70, -2), bounds.min());
        assertEquals(new IntVector3(10, 72, 3), bounds.max());
        assertEquals(new IntVector3(4, 3, 6), bounds.size());
        assertEquals(new IntVector3(11, 73, 4), bounds.visualMax());
        assertTrue(bounds.contains(new IntVector3(10, 72, 3)));
    }

    @Test
    void convertsWorldSelectionIntoLocalRoomSelection() {
        SelectionBounds room = SelectionBounds.between(new IntVector3(100, 50, 100), new IntVector3(109, 55, 109));
        SelectionBounds door = SelectionBounds.between(new IntVector3(103, 51, 100), new IntVector3(105, 53, 100));

        SelectionBounds local = door.toLocal(room);

        assertEquals(new IntVector3(3, 1, 0), local.min());
        assertEquals(new IntVector3(5, 3, 0), local.max());
        assertEquals(new IntVector3(3, 3, 1), local.size());
    }

    @Test
    void exposesExactPaperCaptureSizeForInclusiveBounds() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(2, 4, 6), new IntVector3(6, 10, 12));

        assertEquals(5, bounds.blockVectorSize().getBlockX());
        assertEquals(7, bounds.blockVectorSize().getBlockY());
        assertEquals(7, bounds.blockVectorSize().getBlockZ());
    }

    @Test
    void rejectsSelectionsOutsideParentBounds() {
        SelectionBounds room = SelectionBounds.between(new IntVector3(0, 0, 0), new IntVector3(4, 4, 4));
        SelectionBounds outside = SelectionBounds.between(new IntVector3(4, 1, 1), new IntVector3(5, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> outside.toLocal(room));
    }
}
