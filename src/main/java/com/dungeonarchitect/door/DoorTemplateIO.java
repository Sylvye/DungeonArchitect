package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DoorTemplateIO {
    private DoorTemplateIO() {
    }

    public static DoorTemplate load(Path doorDirectory) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(doorDirectory.resolve("door.yml").toFile());
        String id = yaml.getString("id", doorDirectory.getFileName().toString());
        IntVector3 size = vector(yaml.getIntegerList("size"));
        Set<String> tags = new LinkedHashSet<>(yaml.getStringList("tags"));
        DoorGateway gateway = null;
        ConfigurationSection gatewaySection = yaml.getConfigurationSection("gateway");
        if (gatewaySection != null) {
            gateway = new DoorGateway(
                vector(gatewaySection.getIntegerList("position")),
                vector(gatewaySection.getIntegerList("size")),
                enumValue(Direction3.class, gatewaySection.getString("facing", "NORTH"))
            );
        }
        List<RoomMarker> markers = new ArrayList<>();
        for (ConfigurationSection section : sections(yaml, "markers")) {
            markers.add(new RoomMarker(section.getString("name"), section.getString("type", "generic"), vector(section.getIntegerList("position"))));
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
                vector(section.getIntegerList("size")),
                enumValue(Direction3.class, section.getString("facing", "NORTH")),
                entries
            ));
        }
        return new DoorTemplate(id, size, tags, markers, featureSlots, gateway, doorDirectory.resolve("door.nbt"));
    }

    public static void save(DoorTemplate template, Path doorDirectory) throws IOException {
        Files.createDirectories(doorDirectory);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", template.id());
        yaml.set("size", list(template.size()));
        yaml.set("tags", new ArrayList<>(template.tags()));
        if (template.gateway() != null) {
            yaml.set("gateway.position", list(template.gateway().position()));
            yaml.set("gateway.size", list(template.gateway().size()));
            yaml.set("gateway.facing", template.gateway().facing().name());
        }
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
        yaml.save(doorDirectory.resolve("door.yml").toFile());
    }

    private static List<ConfigurationSection> sections(ConfigurationSection yaml, String path) {
        List<ConfigurationSection> sections = new ArrayList<>();
        if (!yaml.isList(path)) {
            return sections;
        }
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
