package com.dungeonarchitect.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootTableRegistryNestedTest {
    @TempDir Path tempDir;

    @Test
    void rejectsUnknownDuplicateAndCyclicReferences() throws Exception {
        LootTableRegistry registry = new LootTableRegistry(tempDir);
        registry.reload();
        registry.save(new LootTable("food", List.of()));

        assertFalse(registry.validationErrors(new LootTable("supplies", List.of(new LootTableEntry("missing", 1, 0)))).isEmpty());
        assertFalse(registry.validationErrors(new LootTable("supplies", List.of(new LootTableEntry("food", 1, 0), new LootTableEntry("food", 2, 0)))).isEmpty());

        registry.save(new LootTable("supplies", List.of(new LootTableEntry("food", 1, 0))));
        assertTrue(registry.validationErrors(new LootTable("food", List.of(new LootTableEntry("supplies", 1, 0)))).getFirst().contains("cycle"));
    }

}
