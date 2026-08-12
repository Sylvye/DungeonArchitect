package com.dungeonarchitect.domain;

import java.util.Locale;

public enum Direction3 {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    private final IntVector3 vector;

    Direction3(int x, int y, int z) {
        this.vector = new IntVector3(x, y, z);
    }

    public IntVector3 vector() {
        return vector;
    }

    public static Direction3 parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Direction is required");
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "n", "north" -> NORTH;
            case "e", "east" -> EAST;
            case "s", "south" -> SOUTH;
            case "w", "west" -> WEST;
            case "u", "up" -> UP;
            case "d", "down" -> DOWN;
            default -> throw new IllegalArgumentException("Direction must be N, E, S, W, up, or down");
        };
    }

    public Direction3 opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    public Direction3 rotateY(Rotation rotation) {
        if (this == UP || this == DOWN) {
            return this;
        }
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
                default -> this;
            };
            case CLOCKWISE_180 -> opposite();
            case COUNTERCLOCKWISE_90 -> switch (this) {
                case NORTH -> WEST;
                case WEST -> SOUTH;
                case SOUTH -> EAST;
                case EAST -> NORTH;
                default -> this;
            };
        };
    }
}
