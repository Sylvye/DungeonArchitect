package com.dungeonarchitect.gui;

import com.dungeonitems.api.DungeonItemsAPI;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Loaded only when the optional DungeonItems plugin is enabled. */
final class DungeonItemsBridge {
    private DungeonItemsBridge() { }

    static List<Entry> entries() {
        DungeonItemsAPI api = Bukkit.getServicesManager().load(DungeonItemsAPI.class);
        if (api == null) return List.of();
        return api.items().stream().sorted(java.util.Comparator.comparing(info -> info.id()))
            .map(info -> new Entry(info.id(), api.createItem(info.id(), 1).orElse(null)))
            .filter(entry -> entry.item() != null).toList();
    }

    static ItemStack create(String id) {
        DungeonItemsAPI api = Bukkit.getServicesManager().load(DungeonItemsAPI.class);
        return api == null ? null : api.createItem(id, 1).orElse(null);
    }

    record Entry(String id, ItemStack item) { }
}
