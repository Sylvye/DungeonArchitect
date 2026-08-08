package com.dungeonarchitect.domain;

import java.util.List;

public record BoundingBox3i(IntVector3 min, IntVector3 max) {
    public BoundingBox3i {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Invalid bounding box: " + min + " to " + max);
        }
    }

    public static BoundingBox3i fromMinAndSize(IntVector3 min, IntVector3 size) {
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }
        return new BoundingBox3i(min, min.add(size).subtract(new IntVector3(1, 1, 1)));
    }

    public IntVector3 size() {
        return new IntVector3(max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1);
    }

    public boolean contains(IntVector3 point) {
        return point.x() >= min.x() && point.x() <= max.x()
            && point.y() >= min.y() && point.y() <= max.y()
            && point.z() >= min.z() && point.z() <= max.z();
    }

    public boolean intersects(BoundingBox3i other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x()
            && min.y() <= other.max.y() && max.y() >= other.min.y()
            && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    public List<IntVector3> corners() {
        return List.of(
            new IntVector3(min.x(), min.y(), min.z()),
            new IntVector3(min.x(), min.y(), max.z()),
            new IntVector3(min.x(), max.y(), min.z()),
            new IntVector3(min.x(), max.y(), max.z()),
            new IntVector3(max.x(), min.y(), min.z()),
            new IntVector3(max.x(), min.y(), max.z()),
            new IntVector3(max.x(), max.y(), min.z()),
            new IntVector3(max.x(), max.y(), max.z())
        );
    }
}
