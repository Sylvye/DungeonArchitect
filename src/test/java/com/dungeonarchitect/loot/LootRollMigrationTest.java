package com.dungeonarchitect.loot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LootRollMigrationTest {
    @TempDir Path tempDir;

    @Test
    void movesLegacyTableRollsToStringBindingsIdempotently() throws Exception {
        Path lootDir = tempDir.resolve("loot-tables"); Files.createDirectories(lootDir);
        Files.writeString(lootDir.resolve("food.yml"), "id: food\nminimumRolls: 2\nmaximumRolls: 4\nentries: []\n");
        Path roomDir = tempDir.resolve("rooms/kitchen"); Files.createDirectories(roomDir);
        Path roomFile = roomDir.resolve("room.yml");
        Files.writeString(roomFile, "id: kitchen\nlootBindings:\n  chest: food\n");
        LootTableRegistry registry = new LootTableRegistry(lootDir); registry.reload();

        assertTrue(LootRollMigration.migrate(tempDir, registry));
        YamlConfiguration room = YamlConfiguration.loadConfiguration(roomFile.toFile());
        assertEquals("food", room.getString("lootBindings.chest.table"));
        assertEquals(2, room.getInt("lootBindings.chest.minimumRolls"));
        assertEquals(4, room.getInt("lootBindings.chest.maximumRolls"));
        assertFalse(Files.readString(lootDir.resolve("food.yml")).contains("minimumRolls:"));

        registry.reload();
        assertFalse(LootRollMigration.migrate(tempDir, registry));
    }
}
