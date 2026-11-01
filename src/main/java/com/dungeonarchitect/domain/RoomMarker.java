package com.dungeonarchitect.domain;
import java.util.*;
/** A named, inclusive block area in its owning template's local coordinates. */
public record RoomMarker(String name, String type, IntVector3 position, IntVector3 size) {
    public enum Purpose {
        GENERIC("Generic", "generic"), LOOT("Loot", "loot"), PLAYER_SPAWN("Spawn", "spawn");
        private final String label, id;
        Purpose(String label, String id) { this.label = label; this.id = id; }
        public String label() { return label; }
        public String id() { return id; }
    }
    public RoomMarker {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Marker name is required");
        if (type == null || type.isBlank()) type = "generic";
        if (type.equalsIgnoreCase("player_spawn") || type.equalsIgnoreCase("player_spawn_area")) type = "spawn";
        Objects.requireNonNull(position, "Marker position"); Objects.requireNonNull(size, "Marker size");
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) throw new IllegalArgumentException("Marker dimensions must be positive");
    }
    /** Legacy player_spawn positions described feet; new areas describe floor blocks. */
    public RoomMarker(String name, String type, IntVector3 position) {
        this(name, type, type != null && type.equalsIgnoreCase("player_spawn") ? position.add(new IntVector3(0, -1, 0)) : position, new IntVector3(1, 1, 1));
    }
    public Purpose purpose() {
        if (type.equalsIgnoreCase("loot")) return Purpose.LOOT;
        if (type.equalsIgnoreCase("spawn")) return Purpose.PLAYER_SPAWN;
        return Purpose.GENERIC;
    }
    public BoundingBox3i bounds() { return BoundingBox3i.fromMinAndSize(position, size); }
    public RoomMarker renamed(String value) { return new RoomMarker(value, type, position, size); }
    public RoomMarker withBounds(IntVector3 min, IntVector3 dimensions) { return new RoomMarker(name, type, min, dimensions); }
    public RoomMarker withPurpose(Purpose value) { return new RoomMarker(name, value.id(), position, size); }
    public static List<RoomMarker> withLegacyEntry(List<RoomMarker> source, IntVector3 feet, Set<String> componentNames) {
        if (feet == null) return source;
        List<RoomMarker> markers = new ArrayList<>(source);
        IntVector3 floor = feet.add(new IntVector3(0, -1, 0));
        if (markers.stream().anyMatch(m -> m.purpose() == RoomMarker.Purpose.PLAYER_SPAWN && m.bounds().contains(floor))) return markers;
        Set<String> names = new HashSet<>();
        source.forEach(m -> names.add(m.name().toLowerCase(Locale.ROOT)));
        componentNames.forEach(n -> names.add(n.toLowerCase(Locale.ROOT)));
        String name = "dungeon_entry"; int next = 2;
        while (names.contains(name)) name = "dungeon_entry_" + next++;
        markers.add(new RoomMarker(name, "player_spawn_area", floor, new IntVector3(1, 1, 1)));
        return markers;
    }

}
