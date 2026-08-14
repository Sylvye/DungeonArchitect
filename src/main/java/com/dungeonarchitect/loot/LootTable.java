package com.dungeonarchitect.loot;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A reusable weighted pool. Roll counts belong to marker bindings. */
public final class LootTable {
    private final String id;
    private final List<LootPoolEntry> entries;
    private final Integer legacyMinimumRolls;
    private final Integer legacyMaximumRolls;

    public LootTable(String id, List<? extends LootPoolEntry> entries) {
        this(id, entries, null, null);
    }

    /** Source-compatible legacy constructor used while old data is migrated. */
    @Deprecated
    public LootTable(String id, int minimumRolls, int maximumRolls, List<LootEntry> entries) {
        this(id, entries, minimumRolls, maximumRolls);
        if (minimumRolls < 0 || maximumRolls < minimumRolls) throw new IllegalArgumentException("Invalid loot roll range");
    }

    LootTable(String id, List<? extends LootPoolEntry> entries, Integer legacyMinimumRolls, Integer legacyMaximumRolls) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Loot table id is required");
        this.id = id.toLowerCase(Locale.ROOT);
        this.entries = List.copyOf(entries);
        this.legacyMinimumRolls = legacyMinimumRolls;
        this.legacyMaximumRolls = legacyMaximumRolls;
    }

    public String id() { return id; }
    public List<LootPoolEntry> entries() { return entries; }
    public boolean hasLegacyRolls() { return legacyMinimumRolls != null; }
    public int legacyMinimumRolls() { return legacyMinimumRolls == null ? 1 : legacyMinimumRolls; }
    public int legacyMaximumRolls() { return legacyMaximumRolls == null ? 1 : legacyMaximumRolls; }
    public LootTable withoutLegacyRolls() { return new LootTable(id, entries); }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof LootTable table && id.equals(table.id) && entries.equals(table.entries);
    }
    @Override public int hashCode() { return Objects.hash(id, entries); }
    @Override public String toString() { return "LootTable[id=" + id + ", entries=" + entries + "]"; }
}
