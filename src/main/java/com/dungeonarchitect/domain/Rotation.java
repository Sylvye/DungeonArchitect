package com.dungeonarchitect.domain;

public enum Rotation {
    NONE,
    CLOCKWISE_90,
    CLOCKWISE_180,
    COUNTERCLOCKWISE_90;

    public IntVector3 rotate(IntVector3 point, IntVector3 size) {
        return switch (this) {
            case NONE -> point;
            case CLOCKWISE_90 -> new IntVector3(size.z() - 1 - point.z(), point.y(), point.x());
            case CLOCKWISE_180 -> new IntVector3(size.x() - 1 - point.x(), point.y(), size.z() - 1 - point.z());
            case COUNTERCLOCKWISE_90 -> new IntVector3(point.z(), point.y(), size.x() - 1 - point.x());
        };
    }

    public IntVector3 rotateSize(IntVector3 size) {
        return switch (this) {
            case NONE, CLOCKWISE_180 -> size;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new IntVector3(size.z(), size.y(), size.x());
        };
    }

}
