package com.dungeonarchitect.minigame;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PresetRepository {
    private final Path directory;
    private final Map<String, MinigamePreset> presets = new TreeMap<>();
    public PresetRepository(Path directory) { this.directory = directory; reload(); }
    public void reload() {
        Map<String, MinigamePreset> loaded = new TreeMap<>();
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(file.toFile());
                    MinigamePreset preset = new MinigamePreset(yaml.getString("id"), yaml.getString("root", ""),
                        yaml.getStringList("rooms"), yaml.getStringList("spawns"), yaml.getInt("room-count", 32),
                        yaml.getInt("minimum-distance", 4), yaml.getDouble("maximum-ratio", 1.25), yaml.getStringList("kits"));
                    if (loaded.putIfAbsent(preset.id(), preset) != null) throw new IllegalStateException("Duplicate minigame preset " + preset.id());
                }
            }
        } catch (Exception ex) { throw new IllegalStateException("Could not load minigame presets: " + ex.getMessage(), ex); }
        presets.clear(); presets.putAll(loaded);
    }
    public Collection<MinigamePreset> all() { return List.copyOf(presets.values()); }
    public MinigamePreset require(String id) {
        MinigamePreset preset = presets.get(id);
        if (preset == null) throw new IllegalArgumentException("Unknown preset: " + id);
        return preset;
    }
    public void create(MinigamePreset preset) {
        if (presets.containsKey(preset.id())) throw new IllegalArgumentException("Preset already exists: " + preset.id());
        save(preset);
    }
    public void save(MinigamePreset preset) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1); yaml.set("id", preset.id()); yaml.set("root", preset.rootId());
        yaml.set("rooms", preset.ordinaryRooms()); yaml.set("spawns", preset.spawnRooms()); yaml.set("room-count", preset.roomCount());
        yaml.set("minimum-distance", preset.minimumDistance()); yaml.set("maximum-ratio", preset.maximumRatio()); yaml.set("kits", preset.kits());
        write(yaml, directory.resolve(preset.id() + ".yml")); presets.put(preset.id(), preset);
    }
    static void write(YamlConfiguration yaml, Path target) {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent()); yaml.save(temporary.toFile());
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { throw new IllegalStateException("Could not save " + target.getFileName(), ex); }
    }
}
