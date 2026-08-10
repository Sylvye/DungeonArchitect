package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.door.DoorTemplateMatcher;

import java.util.Comparator;
import java.util.List;

public final class DoorGeometry {
    private DoorGeometry() {
    }

    public static BoundingBox3i localBounds(DoorSocket door) {
        return new BoundingBox3i(door.position(), door.position().add(door.size()).subtract(new IntVector3(1, 1, 1)));
    }

    public static BoundingBox3i localBounds(DoorGateway gateway) {
        return new BoundingBox3i(gateway.position(), gateway.position().add(gateway.size()).subtract(new IntVector3(1, 1, 1)));
    }

    public static BoundingBox3i transformedBounds(DoorSocket door, RoomTransform transform) {
        return transformedBounds(localBounds(door), transform);
    }

    public static BoundingBox3i transformedBounds(DoorGateway gateway, RoomTransform transform) {
        return transformedBounds(localBounds(gateway), transform);
    }

    public static RoomTransform doorTransform(DoorSocket slot, DoorTemplate door, RoomTransform roomTransform) {
        Direction3 transformedSlotFacing = roomTransform.transformFacing(slot.facing());
        BoundingBox3i slotBounds = transformedBounds(slot, roomTransform);
        DoorSocket transformedSlot = new DoorSocket(slot.id(), slotBounds.min(), slotBounds.size(), transformedSlotFacing, slot.tags(), slot.entries());
        DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(transformedSlot, door);
        if (!match.matched()) {
            throw new IllegalArgumentException("Door " + door.id() + " does not match slot " + slot.id() + ": " + match.reason());
        }
        Rotation rotation = match.rotation();
        BoundingBox3i relativeDoorBounds = transformedBounds(BoundingBox3i.fromMinAndSize(IntVector3.ZERO, door.size()), new RoomTransform(IntVector3.ZERO, rotation, door.size()));
        BoundingBox3i relativeGatewayBounds = transformedBounds(door.gateway(), new RoomTransform(IntVector3.ZERO, rotation, door.size()));
        IntVector3 origin = alignedOrigin(slotBounds, transformedSlotFacing, relativeDoorBounds, relativeGatewayBounds);
        return new RoomTransform(origin, rotation, door.size());
    }

    public static BoundingBox3i gatewayBounds(DoorSocket slot, DoorTemplate door, RoomTransform roomTransform) {
        return transformedBounds(door.gateway(), doorTransform(slot, door, roomTransform));
    }

    public static Direction3 gatewayFacing(DoorTemplate door, RoomTransform doorTransform) {
        return doorTransform.transformFacing(door.gateway().facing());
    }

    private static BoundingBox3i transformedBounds(BoundingBox3i localBounds, RoomTransform transform) {
        List<IntVector3> points = localBounds.corners().stream()
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
        return first.size().equals(second.size());
    }

    public static String describe(DoorSocket door, RoomTransform transform) {
        Direction3 facing = transform.transformFacing(door.facing());
        BoundingBox3i bounds = transformedBounds(door, transform);
        return "id=" + door.id()
            + " facing=" + facing
            + " size=" + door.size()
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

    private static IntVector3 alignedOrigin(BoundingBox3i slotBounds, Direction3 facing, BoundingBox3i relativeDoorBounds, BoundingBox3i relativeGatewayBounds) {
        return switch (facing) {
            case NORTH -> new IntVector3(centeredAxisOrigin(slotBounds.min().x(), slotBounds.max().x(), relativeDoorBounds.min().x(), relativeDoorBounds.max().x(), relativeGatewayBounds.min().x(), relativeGatewayBounds.max().x()), centeredAxisOrigin(slotBounds.min().y(), slotBounds.max().y(), relativeDoorBounds.min().y(), relativeDoorBounds.max().y(), relativeGatewayBounds.min().y(), relativeGatewayBounds.max().y()), slotBounds.min().z() - relativeGatewayBounds.min().z());
            case SOUTH -> new IntVector3(centeredAxisOrigin(slotBounds.min().x(), slotBounds.max().x(), relativeDoorBounds.min().x(), relativeDoorBounds.max().x(), relativeGatewayBounds.min().x(), relativeGatewayBounds.max().x()), centeredAxisOrigin(slotBounds.min().y(), slotBounds.max().y(), relativeDoorBounds.min().y(), relativeDoorBounds.max().y(), relativeGatewayBounds.min().y(), relativeGatewayBounds.max().y()), slotBounds.max().z() - relativeGatewayBounds.max().z());
            case EAST -> new IntVector3(slotBounds.max().x() - relativeGatewayBounds.max().x(), centeredAxisOrigin(slotBounds.min().y(), slotBounds.max().y(), relativeDoorBounds.min().y(), relativeDoorBounds.max().y(), relativeGatewayBounds.min().y(), relativeGatewayBounds.max().y()), centeredAxisOrigin(slotBounds.min().z(), slotBounds.max().z(), relativeDoorBounds.min().z(), relativeDoorBounds.max().z(), relativeGatewayBounds.min().z(), relativeGatewayBounds.max().z()));
            case WEST -> new IntVector3(slotBounds.min().x() - relativeGatewayBounds.min().x(), centeredAxisOrigin(slotBounds.min().y(), slotBounds.max().y(), relativeDoorBounds.min().y(), relativeDoorBounds.max().y(), relativeGatewayBounds.min().y(), relativeGatewayBounds.max().y()), centeredAxisOrigin(slotBounds.min().z(), slotBounds.max().z(), relativeDoorBounds.min().z(), relativeDoorBounds.max().z(), relativeGatewayBounds.min().z(), relativeGatewayBounds.max().z()));
            case UP -> new IntVector3(centeredAxisOrigin(slotBounds.min().x(), slotBounds.max().x(), relativeDoorBounds.min().x(), relativeDoorBounds.max().x(), relativeGatewayBounds.min().x(), relativeGatewayBounds.max().x()), slotBounds.max().y() - relativeGatewayBounds.max().y(), centeredAxisOrigin(slotBounds.min().z(), slotBounds.max().z(), relativeDoorBounds.min().z(), relativeDoorBounds.max().z(), relativeGatewayBounds.min().z(), relativeGatewayBounds.max().z()));
            case DOWN -> new IntVector3(centeredAxisOrigin(slotBounds.min().x(), slotBounds.max().x(), relativeDoorBounds.min().x(), relativeDoorBounds.max().x(), relativeGatewayBounds.min().x(), relativeGatewayBounds.max().x()), slotBounds.min().y() - relativeGatewayBounds.min().y(), centeredAxisOrigin(slotBounds.min().z(), slotBounds.max().z(), relativeDoorBounds.min().z(), relativeDoorBounds.max().z(), relativeGatewayBounds.min().z(), relativeGatewayBounds.max().z()));
        };
    }

    private static int centeredAxisOrigin(int slotMin, int slotMax, int doorMin, int doorMax, int gatewayMin, int gatewayMax) {
        int gatewaySize = gatewayMax - gatewayMin + 1;
        int desiredGatewayMin = slotMin + ((slotMax - slotMin + 1) - gatewaySize) / 2;
        int origin = desiredGatewayMin - gatewayMin;
        int minOrigin = slotMin - doorMin;
        int maxOrigin = slotMax - doorMax;
        return Math.max(minOrigin, Math.min(maxOrigin, origin));
    }
}
