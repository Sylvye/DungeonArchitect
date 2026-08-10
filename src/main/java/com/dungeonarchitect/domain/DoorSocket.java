package com.dungeonarchitect.domain;

import java.util.List;
import java.util.Set;

public record DoorSocket(
    String id,
    IntVector3 position,
    Direction3 facing,
    SocketType socketType,
    int width,
    int height,
    IntVector3 size,
    Set<String> tags,
    List<DoorSlotEntry> entries
) {
    public DoorSocket {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Door id is required");
        }
        if (position == null) {
            throw new IllegalArgumentException("Door position is required");
        }
        if (facing == null) {
            throw new IllegalArgumentException("Door facing is required");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Door dimensions must be positive");
        }
        if (size == null || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Door slot size must be positive");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public DoorSocket(String id, IntVector3 position, Direction3 facing, SocketType socketType, int width, int height) {
        this(id, position, facing, socketType, width, height, sizeFromFacing(facing, width, height), Set.of(socketType.name().toLowerCase(java.util.Locale.ROOT)), List.of());
    }

    public DoorSocket(String id, IntVector3 position, IntVector3 size, Direction3 facing, Set<String> tags, List<DoorSlotEntry> entries) {
        this(id, position, facing, SocketType.STANDARD, displayWidth(facing, size), displayHeight(facing, size), size, tags, entries);
    }

    public boolean compatibleWith(DoorSocket other) {
        if (!socketType.compatibleWith(other.socketType)) {
            return false;
        }
        if (tags.isEmpty() || other.tags.isEmpty()) {
            return true;
        }
        return tags.stream().anyMatch(tag -> other.tags.stream().anyMatch(tag::equalsIgnoreCase));
    }

    public DoorSocket withEntries(List<DoorSlotEntry> entries) {
        return new DoorSocket(id, position, facing, socketType, width, height, size, tags, entries);
    }

    public DoorSocket withTags(Set<String> tags) {
        return new DoorSocket(id, position, facing, socketType, width, height, size, tags, entries);
    }

    private static IntVector3 sizeFromFacing(Direction3 facing, int width, int height) {
        return switch (facing) {
            case NORTH, SOUTH -> new IntVector3(width, height, 1);
            case EAST, WEST -> new IntVector3(1, height, width);
            case UP, DOWN -> new IntVector3(width, 1, height);
        };
    }

    private static int displayWidth(Direction3 facing, IntVector3 size) {
        return switch (facing) {
            case NORTH, SOUTH -> size.x();
            case EAST, WEST -> size.z();
            case UP, DOWN -> size.x();
        };
    }

    private static int displayHeight(Direction3 facing, IntVector3 size) {
        return switch (facing) {
            case NORTH, SOUTH, EAST, WEST -> size.y();
            case UP, DOWN -> size.z();
        };
    }
}
