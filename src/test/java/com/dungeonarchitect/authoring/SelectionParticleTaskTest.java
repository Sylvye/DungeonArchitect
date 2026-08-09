package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SelectionParticleTaskTest {
    @Test
    void wandShowsCurrentSelectionAndSelectedComponentSeparately() {
        SelectionBounds current = SelectionBounds.between(new IntVector3(1, 1, 1), new IntVector3(2, 2, 2));
        SelectionBounds componentBounds = SelectionBounds.between(new IntVector3(4, 4, 4), new IntVector3(5, 5, 5));
        var selected = new AuthoringManager.ComponentSelection("door", "door_a", componentBounds, componentBounds);

        List<SelectionParticleTask.StyledOutline> outlines = SelectionParticleTask.buildOutlines(true, false, Optional.of(current), Optional.of(selected), List.of(selected), Optional.empty());

        assertEquals(2, outlines.size());
        assertEquals("current", outlines.get(0).key());
        assertEquals(current, outlines.get(0).bounds());
        assertEquals(Particle.WAX_OFF, outlines.get(0).particle());
        assertNull(outlines.get(0).dust());
        assertEquals("selected:door:door_a", outlines.get(1).key());
        assertEquals(componentBounds, outlines.get(1).bounds());
        assertNotNull(outlines.get(1).dust());
    }

    @Test
    void selectorOverlayDoesNotDuplicateSelectedComponentAndRoomBoundsStayWaxOn() {
        SelectionBounds selectedBounds = SelectionBounds.between(new IntVector3(1, 1, 1), new IntVector3(1, 2, 1));
        SelectionBounds otherBounds = SelectionBounds.between(new IntVector3(3, 1, 3), new IntVector3(4, 2, 3));
        SelectionBounds roomBounds = SelectionBounds.between(new IntVector3(0, 0, 0), new IntVector3(9, 9, 9));
        var selected = new AuthoringManager.ComponentSelection("door", "door_a", selectedBounds, selectedBounds);
        var other = new AuthoringManager.ComponentSelection("feature", "slot_a", otherBounds, otherBounds);

        List<SelectionParticleTask.StyledOutline> outlines = SelectionParticleTask.buildOutlines(false, true, Optional.empty(), Optional.of(selected), List.of(selected, other), Optional.of(roomBounds));

        assertEquals(3, outlines.size());
        assertEquals("selected:door:door_a", outlines.get(0).key());
        assertEquals("component:feature:slot_a", outlines.get(1).key());
        assertEquals("bounds", outlines.get(2).key());
        assertEquals(Particle.WAX_ON, outlines.get(2).particle());
    }
}
