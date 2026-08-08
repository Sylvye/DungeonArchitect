package com.dungeonarchitect.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MenuHolder implements InventoryHolder {
    private final String id;

    public MenuHolder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("MenuHolder does not own an inventory instance");
    }
}
