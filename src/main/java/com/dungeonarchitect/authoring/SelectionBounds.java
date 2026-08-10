package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.util.BlockVector;

public record SelectionBounds(IntVector3 min, IntVector3 max) {
    public SelectionBounds {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Selection min must be <= max");
        }
    }

    public static SelectionBounds between(IntVector3 first, IntVector3 second) {
        return new SelectionBounds(
            new IntVector3(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z())),
            new IntVector3(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()))
        );
    }

    public IntVector3 size() {
        return max.subtract(min).add(new IntVector3(1, 1, 1));
    }

    public IntVector3 visualMax() {
        return max.add(new IntVector3(1, 1, 1));
    }

    public BlockVector blockVectorSize() {
        IntVector3 size = size();
        return new BlockVector(size.x(), size.y(), size.z());
    }

    public BoundingBox3i toBoundingBox() {
        return new BoundingBox3i(min, max);
    }

    public boolean contains(IntVector3 worldPosition) {
        return worldPosition.x() >= min.x() && worldPosition.x() <= max.x()
            && worldPosition.y() >= min.y() && worldPosition.y() <= max.y()
            && worldPosition.z() >= min.z() && worldPosition.z() <= max.z();
    }

    public IntVector3 toLocal(IntVector3 worldPosition) {
        return worldPosition.subtract(min);
    }

    public SelectionBounds toLocal(SelectionBounds parent) {
        if (!parent.contains(min) || !parent.contains(max)) {
            throw new IllegalArgumentException("Selection is outside parent bounds");
        }
        return new SelectionBounds(parent.toLocal(min), parent.toLocal(max));
    }

    public String describe() {
        return "min=" + min + " max=" + max + " size=" + size();
    }
}
