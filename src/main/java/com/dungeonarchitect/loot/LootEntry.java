package com.dungeonarchitect.loot;

import org.bukkit.inventory.ItemStack;

/** A weighted configured item used by a loot table. */
public record LootEntry(ItemStack item, int weight, int minimumAmount, int maximumAmount, int maximumPerContainer) {
    public LootEntry {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("Loot entry requires an item");
        }
        item = item.clone();
        if (weight <= 0) {
            throw new IllegalArgumentException("Loot entry weight must be positive");
        }
        if (minimumAmount <= 0 || maximumAmount < minimumAmount) {
            throw new IllegalArgumentException("Invalid loot amount range");
        }
        if (maximumPerContainer < 0) {
            throw new IllegalArgumentException("Maximum per container cannot be negative");
        }
    }

    public LootEntry(ItemStack item, int weight, int minimumAmount, int maximumAmount) {
        this(item, weight, minimumAmount, maximumAmount, 0);
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
