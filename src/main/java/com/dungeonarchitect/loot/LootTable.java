package com.dungeonarchitect.loot;

import java.util.List;
import java.util.Locale;

public record LootTable(String id, int minimumRolls, int maximumRolls, List<LootEntry> entries) {
    public LootTable {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Loot table id is required");
        }
        id = id.toLowerCase(Locale.ROOT);
        if (minimumRolls < 0 || maximumRolls < minimumRolls) {
            throw new IllegalArgumentException("Invalid loot roll range");
        }
        entries = List.copyOf(entries);
    }
}
