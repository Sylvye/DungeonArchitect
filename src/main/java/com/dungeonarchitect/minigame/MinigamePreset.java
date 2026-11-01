package com.dungeonarchitect.minigame;

import java.util.List;

/** Immutable settings copied into each run; template lists are explicit pools. */
public record MinigamePreset(String id, String rootId, List<String> ordinaryRooms, List<String> spawnRooms,
                             int roomCount, int minimumDistance, double maximumRatio, List<String> kits) {
    public static final List<String> FIELDS = List.of("root", "rooms", "spawns", "count", "distance", "ratio", "kits");
    public MinigamePreset {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Use lowercase letters, numbers, _ or - for the preset id");
        ordinaryRooms = List.copyOf(ordinaryRooms);
        spawnRooms = List.copyOf(spawnRooms);
        kits = List.copyOf(kits);
        if (roomCount < 1 || minimumDistance < 1 || !Double.isFinite(maximumRatio) || maximumRatio < 1) throw new IllegalArgumentException("Invalid room count or spread settings");
    }
    public static MinigamePreset empty(String id) { return new MinigamePreset(id, "", List.of(), List.of(), 32, 4, 1.25, List.of()); }
    public MinigamePreset copyAs(String id) { return new MinigamePreset(id, rootId, ordinaryRooms, spawnRooms, roomCount, minimumDistance, maximumRatio, kits); }
    public MinigamePreset with(String field, String value) {
        return new MinigamePreset(id, field.equals("root") ? value.trim() : rootId,
            field.equals("rooms") ? ids(value) : ordinaryRooms, field.equals("spawns") ? ids(value) : spawnRooms,
            field.equals("count") ? Integer.parseInt(value) : roomCount,
            field.equals("distance") ? Integer.parseInt(value) : minimumDistance,
            field.equals("ratio") ? Double.parseDouble(value) : maximumRatio,
            field.equals("kits") ? ids(value) : kits);
    }
    public static List<String> ids(String value) {
        return value.isBlank() || value.equalsIgnoreCase("none") ? List.of()
            : java.util.Arrays.stream(value.trim().split("[,\\s]+")).distinct().toList();
    }
}
