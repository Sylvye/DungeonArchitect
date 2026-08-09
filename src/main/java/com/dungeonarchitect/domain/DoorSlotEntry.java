package com.dungeonarchitect.domain;

public record DoorSlotEntry(String doorId, int weight) {
    public static final String EMPTY = "empty";

    public DoorSlotEntry {
        if (doorId == null || doorId.isBlank()) {
            throw new IllegalArgumentException("Door id is required");
        }
        doorId = doorId.toLowerCase(java.util.Locale.ROOT);
        if (weight <= 0) {
            throw new IllegalArgumentException("Door weight must be positive");
        }
    }
}
