package com.dungeonarchitect.door;

import com.dungeonarchitect.authoring.SelectionBounds;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BoundaryFacing {
    private BoundaryFacing() {
    }

    public static Direction3 infer(SelectionBounds child, SelectionBounds parent, String label) {
        List<FaceCoverage> matches = touchingFaces(child, parent, label);
        FaceCoverage best = matches.stream()
            .max(Comparator.comparingInt(FaceCoverage::coverage))
            .orElseThrow();
        long tied = matches.stream().filter(match -> match.coverage() == best.coverage()).count();
        if (tied > 1) {
            throw new AmbiguousFacingException(label, matches.stream().map(FaceCoverage::direction).toList());
        }
        return best.direction();
    }

    public static List<Direction3> validFaces(SelectionBounds child, SelectionBounds parent, String label) {
        return touchingFaces(child, parent, label).stream().map(FaceCoverage::direction).toList();
    }

    public static void requireValidFace(Direction3 facing, SelectionBounds child, SelectionBounds parent, String label) {
        if (facing == null) {
            throw new IllegalArgumentException(label + " facing is required");
        }
        if (!validFaces(child, parent, label).contains(facing)) {
            throw new IllegalArgumentException(label + " facing " + facing + " does not touch the selected bounds");
        }
    }

    private static List<FaceCoverage> touchingFaces(SelectionBounds child, SelectionBounds parent, String label) {
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
        return List.copyOf(matches);
    }

    public static final class AmbiguousFacingException extends IllegalArgumentException {
        private final List<Direction3> validFaces;

        public AmbiguousFacingException(String label, List<Direction3> validFaces) {
            super(label + " must have one dominant bounds face; choose one of " + validFaces);
            this.validFaces = List.copyOf(validFaces);
        }

        public List<Direction3> validFaces() {
            return validFaces;
        }
    }

    private record FaceCoverage(Direction3 direction, int coverage) {
    }
}
