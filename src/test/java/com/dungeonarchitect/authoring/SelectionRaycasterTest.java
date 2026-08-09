package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SelectionRaycasterTest {
    @Test
    void choosesNearestHit() {
        Target far = new Target("far", SelectionBounds.between(new IntVector3(0, 0, 8), new IntVector3(0, 0, 8)));
        Target near = new Target("near", SelectionBounds.between(new IntVector3(0, 0, 2), new IntVector3(0, 0, 2)));

        var hit = SelectionRaycaster.firstHit(List.of(far, near), Target::bounds, new Vector(0.5, 0.5, 0), new Vector(0, 0, 1), 64);

        assertEquals(near, hit.orElseThrow().value());
    }

    @Test
    void missesBoundsOutsideRay() {
        Target offAxis = new Target("off_axis", SelectionBounds.between(new IntVector3(5, 0, 2), new IntVector3(5, 0, 2)));

        var hit = SelectionRaycaster.firstHit(List.of(offAxis), Target::bounds, new Vector(0.5, 0.5, 0), new Vector(0, 0, 1), 64);

        assertTrue(hit.isEmpty());
    }

    @Test
    void handlesOneBlockMarkerBounds() {
        Target marker = new Target("marker", SelectionBounds.between(new IntVector3(1, 1, 1), new IntVector3(1, 1, 1)));

        var hit = SelectionRaycaster.firstHit(List.of(marker), Target::bounds, new Vector(1.5, 1.5, 0), new Vector(0, 0, 1), 64);

        assertEquals(marker, hit.orElseThrow().value());
        assertEquals(1.0, hit.orElseThrow().distance(), 1.0E-9);
    }

    @Test
    void respectsMaxRange() {
        Target distant = new Target("distant", SelectionBounds.between(new IntVector3(0, 0, 10), new IntVector3(0, 0, 10)));

        var hit = SelectionRaycaster.firstHit(List.of(distant), Target::bounds, new Vector(0.5, 0.5, 0), new Vector(0, 0, 1), 5);

        assertTrue(hit.isEmpty());
    }

    @Test
    void createsRayParticlePoints() {
        List<Vector> points = SelectionRaycaster.rayPoints(new Vector(1, 2, 3), new Vector(0, 0, 2), 2.0, 1.0);

        assertEquals(List.of(new Vector(1, 2, 3), new Vector(1, 2, 4), new Vector(1, 2, 5)), points);
    }

    @Test
    void selectorUsesBreezeRod() {
        assertEquals(org.bukkit.Material.BREEZE_ROD, AuthoringManager.SELECTOR_MATERIAL);
    }

    private record Target(String id, SelectionBounds bounds) {
    }
}
