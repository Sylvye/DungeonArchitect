package com.dungeonarchitect.loot;

import java.util.Locale;

/** A weighted reference that resolves one final item from another loot table. */
public record LootTableEntry(String tableId, int weight, int maximumPerContainer) implements LootPoolEntry {
    public LootTableEntry {
        if (tableId == null || tableId.isBlank()) throw new IllegalArgumentException("Nested loot table id is required");
        tableId = tableId.toLowerCase(Locale.ROOT);
        if (weight <= 0) throw new IllegalArgumentException("Loot entry weight must be positive");
        if (maximumPerContainer < 0) throw new IllegalArgumentException("Maximum per container cannot be negative");
    }
}
