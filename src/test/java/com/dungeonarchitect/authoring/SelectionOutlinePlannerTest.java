package com.dungeonarchitect.authoring;

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
}
