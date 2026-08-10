package com.dungeonarchitect.door;

import com.dungeonarchitect.authoring.SelectionBounds;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.List;

public final class BoundaryFacing {
    private BoundaryFacing() {
    }

    public static Direction3 infer(SelectionBounds child, SelectionBounds parent, String label) {
        if (!parent.contains(child.min()) || !parent.contains(child.max())) {
            throw new IllegalArgumentException(label + " must be inside bounds");
        }
        IntVector3 min = child.min();
        IntVector3 max = child.max();
        IntVector3 parentMin = parent.min();
        IntVector3 parentMax = parent.max();
        IntVector3 size = child.size();
        List<FaceCoverage> matches = new ArrayList<>();
        if (min.z() == parentMin.z()) {
            matches.add(new FaceCoverage(Direction3.NORTH, size.x() * size.y()));
        }
        if (max.z() == parentMax.z()) {
            matches.add(new FaceCoverage(Direction3.SOUTH, size.x() * size.y()));
        }
        if (max.x() == parentMax.x()) {
            matches.add(new FaceCoverage(Direction3.EAST, size.z() * size.y()));
        }
        if (min.x() == parentMin.x()) {
            matches.add(new FaceCoverage(Direction3.WEST, size.z() * size.y()));
        }
        if (max.y() == parentMax.y()) {
            matches.add(new FaceCoverage(Direction3.UP, size.x() * size.z()));
        }
        if (min.y() == parentMin.y()) {
            matches.add(new FaceCoverage(Direction3.DOWN, size.x() * size.z()));
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(label + " must touch a bounds face");
        }
        FaceCoverage best = matches.stream()
            .max(java.util.Comparator.comparingInt(FaceCoverage::coverage))
            .orElseThrow();
        long tied = matches.stream().filter(match -> match.coverage() == best.coverage()).count();
        if (tied > 1) {
            throw new IllegalArgumentException(label + " must have one dominant bounds face");
        }
        return best.direction();
    }

    private record FaceCoverage(Direction3 direction, int coverage) {
    }
}
