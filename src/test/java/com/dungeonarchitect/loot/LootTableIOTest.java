package com.dungeonarchitect.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootTableIOTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsNestedTablesWithoutRollSettings() throws Exception {
        LootTable table = new LootTable("supplies", List.of(new LootTableEntry("food", 3, 0)));
        Path file = tempDir.resolve("supplies.yml");

        LootTableIO.save(table, file);
        LootTable loaded = LootTableIO.load(file);

        assertEquals(table, loaded);
        assertFalse(Files.readString(file).contains("minimumRolls:"));
        assertEquals(new LootTableEntry("food", 3, 0), loaded.entries().getFirst());
    }

    @Test
    void readsLegacyRollMetadata() throws Exception {
        Path file = tempDir.resolve("legacy.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("id", "legacy"); yaml.set("minimumRolls", 2); yaml.set("maximumRolls", 5);
        yaml.set("entries", List.of()); yaml.save(file.toFile());

        LootTable loaded = LootTableIO.load(file);
        assertTrue(loaded.hasLegacyRolls());
        assertEquals(2, loaded.legacyMinimumRolls());
        assertTrue(loaded.entries().isEmpty());
    }
}
