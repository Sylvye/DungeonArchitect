package com.dungeonarchitect.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Idempotently moves legacy table roll ranges onto every marker binding. */
public final class LootRollMigration {
    private LootRollMigration() { }

    public static boolean migrate(Path dataDirectory, LootTableRegistry registry) throws IOException {
        Map<String, LootBinding> defaults = new LinkedHashMap<>();
        for (LootTable table : registry.all()) {
            if (table.hasLegacyRolls()) defaults.put(table.id(), new LootBinding(table.id(), table.legacyMinimumRolls(), table.legacyMaximumRolls()));
        }
        if (defaults.isEmpty()) return false;

        List<Path> metadataFiles = new ArrayList<>();
        collect(metadataFiles, dataDirectory.resolve("rooms"), "room.yml");
        collect(metadataFiles, dataDirectory.resolve("doors"), "door.yml");
        collect(metadataFiles, dataDirectory.resolve("features"), "feature.yml");
        for (Path file : metadataFiles) migrateBindings(file, defaults);

        for (LootTable table : registry.all()) {
            if (table.hasLegacyRolls()) LootTableIO.save(table.withoutLegacyRolls(), registry.directory().resolve(table.id() + ".yml"));
        }
        return true;
    }

    private static void collect(List<Path> files, Path directory, String filename) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.walk(directory)) {
            files.addAll(paths.filter(path -> path.getFileName().toString().equals(filename)).sorted().toList());
        }
    }

    private static void migrateBindings(Path file, Map<String, LootBinding> defaults) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection section = yaml.getConfigurationSection("lootBindings");
        if (section == null) return;
        boolean changed = false;
        for (String marker : section.getKeys(false)) {
            Object raw = section.get(marker);
            if (!(raw instanceof String tableId) || tableId.isBlank()) continue;
            LootBinding binding = defaults.getOrDefault(tableId.toLowerCase(java.util.Locale.ROOT), new LootBinding(tableId));
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("table", binding.tableId());
            serialized.put("minimumRolls", binding.minimumRolls());
            serialized.put("maximumRolls", binding.maximumRolls());
            section.set(marker, serialized);
            changed = true;
        }
        if (changed) AtomicFileWriter.write(file, temporary -> yaml.save(temporary.toFile()));
    }
}
