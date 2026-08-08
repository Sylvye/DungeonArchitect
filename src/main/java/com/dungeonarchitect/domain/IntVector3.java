package com.dungeonarchitect.domain;

public record IntVector3(int x, int y, int z) {
    public static final IntVector3 ZERO = new IntVector3(0, 0, 0);

    public IntVector3 add(IntVector3 other) {
        return new IntVector3(x + other.x, y + other.y, z + other.z);
    }

    public IntVector3 subtract(IntVector3 other) {
        return new IntVector3(x - other.x, y - other.y, z - other.z);
    }

    public IntVector3 multiply(int scalar) {
        return new IntVector3(x * scalar, y * scalar, z * scalar);
    }
}
