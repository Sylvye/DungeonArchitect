package com.dungeonarchitect.loot;

/** A weighted direct item or nested-table entry in a reusable loot pool. */
public sealed interface LootPoolEntry permits LootEntry, LootTableEntry {
    int weight();
    int maximumPerContainer();
}
