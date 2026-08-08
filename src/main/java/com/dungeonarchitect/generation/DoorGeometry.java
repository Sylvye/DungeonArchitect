package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;

import java.util.Comparator;
import java.util.List;

public final class DoorGeometry {
    private DoorGeometry() {
    }

    public static BoundingBox3i localBounds(DoorSocket door) {
        IntVector3 maxOffset = switch (door.facing()) {
            case NORTH, SOUTH -> new IntVector3(door.width() - 1, door.height() - 1, 0);
            case EAST, WEST -> new IntVector3(0, door.height() - 1, door.width() - 1);
            case UP, DOWN -> new IntVector3(door.width() - 1, 0, door.height() - 1);
        };
        return new BoundingBox3i(door.position(), door.position().add(maxOffset));
    }

    public static BoundingBox3i transformedBounds(DoorSocket door, RoomTransform transform) {
        List<IntVector3> points = localBounds(door).corners().stream()
            .map(transform::transformLocal)
            .toList();
        int minX = points.stream().min(Comparator.comparingInt(IntVector3::x)).orElseThrow().x();
        int minY = points.stream().min(Comparator.comparingInt(IntVector3::y)).orElseThrow().y();
        int minZ = points.stream().min(Comparator.comparingInt(IntVector3::z)).orElseThrow().z();
        int maxX = points.stream().max(Comparator.comparingInt(IntVector3::x)).orElseThrow().x();
        int maxY = points.stream().max(Comparator.comparingInt(IntVector3::y)).orElseThrow().y();
        int maxZ = points.stream().max(Comparator.comparingInt(IntVector3::z)).orElseThrow().z();
        return new BoundingBox3i(new IntVector3(minX, minY, minZ), new IntVector3(maxX, maxY, maxZ));
    }

    public static BoundingBox3i relativeBounds(DoorSocket door, Rotation rotation, IntVector3 templateSize) {
        return transformedBounds(door, new RoomTransform(IntVector3.ZERO, rotation, templateSize));
    }

    public static BoundingBox3i shifted(BoundingBox3i bounds, IntVector3 offset) {
        return new BoundingBox3i(bounds.min().add(offset), bounds.max().add(offset));
    }

    public static boolean sameAperture(DoorSocket first, DoorSocket second) {
        return first.width() == second.width() && first.height() == second.height();
    }

    public static String describe(DoorSocket door, RoomTransform transform) {
        Direction3 facing = transform.transformFacing(door.facing());
        BoundingBox3i bounds = transformedBounds(door, transform);
        return "id=" + door.id()
            + " facing=" + facing
            + " size=" + door.width() + "x" + door.height()
            + " bounds=" + bounds
            + " center=" + center(bounds);
    }

    public static String delta(BoundingBox3i expected, BoundingBox3i actual) {
        return actual.min().subtract(expected.min()).toString();
    }

    private static String center(BoundingBox3i bounds) {
        return "("
            + (bounds.min().x() + bounds.max().x()) / 2.0
            + ","
            + (bounds.min().y() + bounds.max().y()) / 2.0
            + ","
            + (bounds.min().z() + bounds.max().z()) / 2.0
            + ")";
    }
}
