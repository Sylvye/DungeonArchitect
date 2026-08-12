package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.TagDomain;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persistent, case-insensitive tag catalogs used by the authoring menus. */
public final class TagCatalog {
    private final Path file;
    private final Map<TagDomain, Map<String, String>> tags = new EnumMap<>(TagDomain.class);

    public TagCatalog(Path file) {
        this.file = file;
        for (TagDomain domain : TagDomain.values()) {
            tags.put(domain, new LinkedHashMap<>());
        }
        load();
    }

    public List<String> tags(TagDomain domain, String filter) {
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return tags.get(domain).values().stream()
            .filter(tag -> tag.toLowerCase(Locale.ROOT).contains(normalizedFilter))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    public String add(TagDomain domain, String value) {
        String tag = requireTag(value);
        String key = key(tag);
        String existing = tags.get(domain).get(key);
        if (existing != null) {
            return existing;
        }
        tags.get(domain).put(key, tag);
        save();
        return tag;
    }

    public boolean remove(TagDomain domain, String value) {
        boolean removed = tags.get(domain).remove(key(value)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public void synchronize(Collection<RoomTemplate> rooms, Collection<DoorTemplate> doors) {
        boolean changed = false;
        for (RoomTemplate room : rooms) {
            changed |= addAll(TagDomain.ROOM, room.tags());
            for (var slot : room.doors()) {
                changed |= addAll(TagDomain.DOOR, slot.tags());
                changed |= addAll(TagDomain.DOOR, slot.connectionRules().allowedTags());
                changed |= addAll(TagDomain.DOOR, slot.connectionRules().deniedTags());
                changed |= addAll(TagDomain.ROOM, slot.connectionRules().allowedRoomTags());
                changed |= addAll(TagDomain.ROOM, slot.connectionRules().deniedRoomTags());
            }
        }
        for (DoorTemplate door : doors) {
            changed |= addAll(TagDomain.DOOR, door.tags());
        }
        if (changed) {
            save();
        }
    }

    private boolean addAll(TagDomain domain, Collection<String> values) {
        boolean changed = false;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String tag = value.trim();
            changed |= tags.get(domain).putIfAbsent(key(tag), tag) == null;
        }
        return changed;
    }

    private void load() {
        boolean exists = Files.exists(file);
        if (file.getParent() != null) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Failed to create tag catalog directory: " + ex.getMessage(), ex);
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        addAll(TagDomain.ROOM, yaml.getStringList("room-tags"));
        addAll(TagDomain.DOOR, yaml.getStringList("door-tags"));
        if (!exists) {
            save();
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("room-tags", sorted(TagDomain.ROOM));
        yaml.set("door-tags", sorted(TagDomain.DOOR));
        try {
            yaml.save(file.toFile());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to save tag catalog: " + ex.getMessage(), ex);
        }
    }

    private List<String> sorted(TagDomain domain) {
        List<String> result = new ArrayList<>(tags.get(domain).values());
        result.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static String requireTag(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tag cannot be blank");
        }
        String tag = value.trim();
        if (tag.contains(",")) {
            throw new IllegalArgumentException("Add one tag at a time");
        }
        return tag;
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
