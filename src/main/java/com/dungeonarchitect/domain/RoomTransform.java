package com.dungeonarchitect.domain;

public record RoomTransform(IntVector3 origin, Rotation rotation, IntVector3 templateSize) {
    public IntVector3 transformLocal(IntVector3 local) {
        return origin.add(rotation.rotate(local, templateSize));
    }

    public Direction3 transformFacing(Direction3 facing) {
        return facing.rotateY(rotation);
    }

    public IntVector3 transformedSize() {
        return rotation.rotateSize(templateSize);
    }

    public BoundingBox3i transformedBounds() {
        return BoundingBox3i.fromMinAndSize(origin, transformedSize());
    }
}
