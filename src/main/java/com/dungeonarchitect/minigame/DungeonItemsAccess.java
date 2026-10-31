package com.dungeonarchitect.minigame;

import com.dungeonitems.api.DungeonItemsAPI;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

final class DungeonItemsAccess {
    private static DungeonItemsAPI api() {
        DungeonItemsAPI api = Bukkit.getServicesManager().load(DungeonItemsAPI.class);
        if (api == null) throw new IllegalStateException("DungeonItems service is unavailable");
        return api;
    }
    static List<String> available() { return api().kits().stream().sorted().toList(); }
    static Map<String, ItemStack> resolve(String id) {
        var kit = api().kit(id).orElseThrow(() -> new IllegalArgumentException("Missing kit: " + id));
        Map<String, ItemStack> slots = new LinkedHashMap<>();
        kit.slots().forEach((slot, item) -> {
            boolean valid = Set.of("helmet", "chestplate", "leggings", "boots", "offhand").contains(slot);
            if (slot.matches("inventory\\.\\d+")) {
                int index = Integer.parseInt(slot.substring(10)); valid = index >= 0 && index < 36;
            }
            if (!valid || item == null) throw new IllegalArgumentException("Invalid slot in kit " + id + ": " + slot);
            slots.put(slot, item.clone());
        });
        return Collections.unmodifiableMap(slots);
    }
}
