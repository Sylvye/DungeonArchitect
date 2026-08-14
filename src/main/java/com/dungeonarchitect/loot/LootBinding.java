package com.dungeonarchitect.loot;

import java.util.Locale;

/** A marker's selected loot pool and number of container rolls. */
public record LootBinding(String tableId, int minimumRolls, int maximumRolls) {
    public LootBinding {
        if (tableId == null || tableId.isBlank()) throw new IllegalArgumentException("Loot table id is required");
        tableId = tableId.toLowerCase(Locale.ROOT);
        if (minimumRolls < 0 || maximumRolls < minimumRolls) throw new IllegalArgumentException("Invalid loot roll range");
    }

    public LootBinding(String tableId) {
        this(tableId, 1, 1);
    }
}
