package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class SelectionOutlinePlannerTest {
    @Test
    void overlappingOutlinesReceiveStableOffsetsWithoutChangingBounds() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(1, 2, 3), new IntVector3(4, 5, 6));
        List<SelectionOutlinePlanner.Outline> outlines = List.of(
            new SelectionOutlinePlanner.Outline("current", bounds),
            new SelectionOutlinePlanner.Outline("bounds", bounds)
        );

        var first = SelectionOutlinePlanner.plan(outlines);
        var second = SelectionOutlinePlanner.plan(outlines);

        assertEquals(first, second);
        assertEquals(SelectionOutlinePlanner.Offset.ZERO, first.get(0).offset());
        assertNotEquals(SelectionOutlinePlanner.Offset.ZERO, first.get(1).offset());
        assertEquals(bounds, first.get(1).bounds());
    }

    @Test
    void offsetOutlinePointsDoNotShareCoordinates() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(0, 0, 0), new IntVector3(0, 0, 0));

        Vector unoffset = SelectionOutlinePlanner.outlinePoints(bounds, 1.0, SelectionOutlinePlanner.Offset.ZERO).getFirst();
        Vector offset = SelectionOutlinePlanner.outlinePoints(bounds, 1.0, new SelectionOutlinePlanner.Offset(0.035, 0, 0)).getFirst();

        assertEquals(new Vector(0, 0, 0), unoffset);
        assertEquals(new Vector(0.035, 0, 0), offset);
    }

    @Test
    void facingLineStartsAtFacingFaceAndExtendsOutward() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(10, 80, 20), new IntVector3(12, 83, 22));

        List<Vector> north = SelectionOutlinePlanner.facingLinePoints(bounds, Direction3.NORTH, 1.0, 2.0, SelectionOutlinePlanner.Offset.ZERO);
        List<Vector> east = SelectionOutlinePlanner.facingLinePoints(bounds, Direction3.EAST, 1.0, 2.0, new SelectionOutlinePlanner.Offset(0.035, 0, 0));

        assertEquals(new Vector(11.5, 82.0, 20.0), north.getFirst());
        assertEquals(new Vector(11.5, 82.0, 18.0), north.getLast());
        assertEquals(new Vector(13.035, 82.0, 21.5), east.getFirst());
        assertEquals(new Vector(15.035, 82.0, 21.5), east.getLast());
    }

    @Test
    void verticalFacingLinesStartAtVerticalFaceAndExtendOutward() {
        SelectionBounds bounds = SelectionBounds.between(new IntVector3(10, 80, 20), new IntVector3(12, 83, 22));

        List<Vector> up = SelectionOutlinePlanner.facingLinePoints(bounds, Direction3.UP, 1.0, 2.0, SelectionOutlinePlanner.Offset.ZERO);
        List<Vector> down = SelectionOutlinePlanner.facingLinePoints(bounds, Direction3.DOWN, 1.0, 2.0, SelectionOutlinePlanner.Offset.ZERO);

        assertEquals(new Vector(11.5, 84.0, 21.5), up.getFirst());
        assertEquals(new Vector(11.5, 86.0, 21.5), up.getLast());
        assertEquals(new Vector(11.5, 80.0, 21.5), down.getFirst());
        assertEquals(new Vector(11.5, 78.0, 21.5), down.getLast());
    }
}
