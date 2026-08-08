package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RoomTemplateIO {
    private RoomTemplateIO() {
    }

    public static RoomTemplate load(Path roomDirectory) {
        Path metadataFile = roomDirectory.resolve("room.yml");
        Path structureFile = roomDirectory.resolve("room.nbt");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(metadataFile.toFile());
        String id = yaml.getString("id", roomDirectory.getFileName().toString());
        RoomCategory category = enumValue(RoomCategory.class, yaml.getString("category", "GENERIC"));
        int weight = yaml.getInt("weight", 10);
        IntVector3 size = vector(yaml.getIntegerList("size"));
        IntVector3 spawn = yaml.isList("spawn") ? vector(yaml.getIntegerList("spawn")) : null;
        Set<String> tags = new LinkedHashSet<>(yaml.getStringList("tags"));

        List<DoorSocket> doors = new ArrayList<>();
        for (ConfigurationSection section : sections(yaml, "doors")) {
            doors.add(new DoorSocket(
                section.getString("id"),
                vector(section.getIntegerList("position")),
                enumValue(Direction3.class, section.getString("facing", "NORTH")),
                enumValue(SocketType.class, section.getString("socket", "STANDARD")),
                section.getInt("width", 1),
                section.getInt("height", 2)
            ));
        }

        List<RoomMarker> markers = new ArrayList<>();
        for (ConfigurationSection section : sections(yaml, "markers")) {
            markers.add(new RoomMarker(
                section.getString("name"),
                section.getString("type", "generic"),
                vector(section.getIntegerList("position"))
            ));
        }

        List<RoomFeatureSlot> featureSlots = new ArrayList<>();
        for (ConfigurationSection section : sections(yaml, "featureSlots")) {
            List<FeatureSlotEntry> entries = new ArrayList<>();
            for (ConfigurationSection entry : sections(section, "entries")) {
                entries.add(new FeatureSlotEntry(entry.getString("feature", FeatureSlotEntry.EMPTY), entry.getInt("weight", 1)));
            }
            featureSlots.add(new RoomFeatureSlot(
                section.getString("id"),
                vector(section.getIntegerList("position")),
                section.isList("size") ? vector(section.getIntegerList("size")) : new IntVector3(1, 1, 1),
                enumValue(Direction3.class, section.getString("facing", "NORTH")),
                entries
            ));
        }

        return new RoomTemplate(id, category, weight, tags, size, spawn, doors, markers, featureSlots, structureFile);
    }

    public static void save(RoomTemplate template, Path roomDirectory) throws IOException {
        Files.createDirectories(roomDirectory);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", template.id());
        yaml.set("category", template.category().name());
        yaml.set("weight", template.weight());
        yaml.set("size", list(template.size()));
        yaml.set("spawn", template.spawn() == null ? null : list(template.spawn()));
        yaml.set("tags", new ArrayList<>(template.tags()));

        List<Map<String, Object>> doors = new ArrayList<>();
        for (DoorSocket door : template.doors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", door.id());
            item.put("position", list(door.position()));
            item.put("facing", door.facing().name());
            item.put("socket", door.socketType().name());
            item.put("width", door.width());
            item.put("height", door.height());
            doors.add(item);
        }
        yaml.set("doors", doors);

        List<Map<String, Object>> markers = new ArrayList<>();
        for (RoomMarker marker : template.markers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", marker.name());
            item.put("type", marker.type());
            item.put("position", list(marker.position()));
            markers.add(item);
        }
        yaml.set("markers", markers);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (RoomFeatureSlot slot : template.featureSlots()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", slot.id());
            item.put("position", list(slot.position()));
            item.put("size", list(slot.size()));
            item.put("facing", slot.facing().name());
            List<Map<String, Object>> entries = new ArrayList<>();
            for (FeatureSlotEntry entry : slot.entries()) {
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("feature", entry.featureId());
                entryMap.put("weight", entry.weight());
                entries.add(entryMap);
            }
            item.put("entries", entries);
            slots.add(item);
        }
        yaml.set("featureSlots", slots);
        yaml.save(roomDirectory.resolve("room.yml").toFile());
    }

    private static List<ConfigurationSection> sections(YamlConfiguration yaml, String path) {
        return sections((ConfigurationSection) yaml, path);
    }

    private static List<ConfigurationSection> sections(ConfigurationSection yaml, String path) {
        List<ConfigurationSection> sections = new ArrayList<>();
        if (yaml.isList(path)) {
            for (Object item : yaml.getList(path, List.of())) {
                if (item instanceof ConfigurationSection section) {
                    sections.add(section);
                } else if (item instanceof Map<?, ?> map) {
                    YamlConfiguration section = new YamlConfiguration();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        section.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    sections.add(section);
                }
            }
        } else {
            ConfigurationSection parent = yaml.getConfigurationSection(path);
            if (parent != null) {
                for (String key : parent.getKeys(false)) {
                    ConfigurationSection section = parent.getConfigurationSection(key);
                    if (section != null) {
                        section.set("id", section.getString("id", key));
                        sections.add(section);
                    }
                }
            }
        }
        return sections;
    }

    private static IntVector3 vector(List<Integer> values) {
        if (values.size() != 3) {
            throw new IllegalArgumentException("Expected vector with 3 integers, got " + values);
        }
        return new IntVector3(values.get(0), values.get(1), values.get(2));
    }

    private static List<Integer> list(IntVector3 vector) {
        return List.of(vector.x(), vector.y(), vector.z());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
