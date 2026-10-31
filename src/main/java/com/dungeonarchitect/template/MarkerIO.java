package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.loot.LootBinding;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Shared marker metadata, including non-destructive legacy conversion. */
public final class MarkerIO {
    private MarkerIO() { }
    public static List<RoomMarker> load(ConfigurationSection yaml, Map<String, LootBinding> bindings) {
        List<RoomMarker> markers = new ArrayList<>();
        for (Object raw : yaml.getList("markers", List.of())) {
            ConfigurationSection section;
            if (raw instanceof ConfigurationSection value) section = value;
            else if (raw instanceof Map<?, ?> map) {
                section = new YamlConfiguration();
                for (var entry : map.entrySet()) section.set(String.valueOf(entry.getKey()), entry.getValue());
            } else throw new IllegalArgumentException("Invalid marker metadata");
            String name = section.getString("name"), type = section.getString("type", "generic");
            if (section.isList("size")) {
                RoomMarker marker = new RoomMarker(name, type, vector(section.getIntegerList("position")), vector(section.getIntegerList("size")));
                if (marker.purpose() != RoomMarker.Purpose.LOOT && bindings.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name))) throw new IllegalArgumentException("Marker '" + name + "' has a loot binding but is not a Loot marker. Correct its purpose or remove the binding.");
                markers.add(marker);
            }
            else {
                if (!type.equalsIgnoreCase("player_spawn") && bindings.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name))) type = "loot";
                markers.add(new RoomMarker(name, type, vector(section.getIntegerList("position"))));
            }
        }
        return markers;
    }
    public static List<RoomMarker> withLegacyEntry(List<RoomMarker> source, IntVector3 feet, Set<String> componentNames) {
        return RoomMarker.withLegacyEntry(source, feet, componentNames);
    }

    public static List<Map<String, Object>> save(List<RoomMarker> markers) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RoomMarker marker : markers) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", marker.name()); value.put("type", marker.type());
            value.put("position", list(marker.position())); value.put("size", list(marker.size())); result.add(value);
        }
        return result;
    }
    private static IntVector3 vector(List<Integer> value) {
        if (value.size() != 3) throw new IllegalArgumentException("Marker vectors need three integers");
        return new IntVector3(value.get(0), value.get(1), value.get(2));
    }
    private static List<Integer> list(IntVector3 value) { return List.of(value.x(), value.y(), value.z()); }

    public static void validate(String owner, List<RoomMarker> markers, Map<String, LootBinding> bindings, BoundingBox3i bounds, boolean room, TemplateValidationResult result) {
        for (RoomMarker marker : markers) {
            if (!bounds.contains(marker.position()) || !bounds.contains(marker.bounds().max())) result.add(owner + ": marker '" + marker.name() + "' extends outside template bounds", marker.position());
            if (!room && marker.purpose() == RoomMarker.Purpose.PLAYER_SPAWN) result.add(owner + ": Player Spawn markers belong to rooms, not doors or features", marker.position());
        }
        for (String name : bindings.keySet()) {
            RoomMarker marker = markers.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst().orElse(null);
            if (marker == null || marker.purpose() != RoomMarker.Purpose.LOOT) result.add(owner + ": loot binding '" + name + "' requires a Loot marker; correct its purpose or remove the binding");
        }
    }
}
