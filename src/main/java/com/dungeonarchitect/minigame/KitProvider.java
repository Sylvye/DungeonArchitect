package com.dungeonarchitect.minigame;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The optional provider is loaded only when DungeonItems is present. */
final class KitProvider {
    private KitProvider() { }
    static List<String> available() {
        if (!Bukkit.getPluginManager().isPluginEnabled("DungeonItems")) return List.of();
        try { return DungeonItemsAccess.available(); }
        catch (LinkageError error) { return List.of(); }
    }
    static Map<String, Map<String, ItemStack>> resolve(List<String> ids) {
        Map<String, Map<String, ItemStack>> result = new LinkedHashMap<>();
        if (ids.isEmpty()) return result;
        if (!Bukkit.getPluginManager().isPluginEnabled("DungeonItems")) throw new IllegalStateException("This run requires DungeonItems kits");
        try { for (String id : ids) result.put(id, DungeonItemsAccess.resolve(id)); }
        catch (LinkageError error) { throw new IllegalStateException("Update DungeonItems to a version with the kit API", error); }
        return result;
    }
    static void equip(Player player, Map<String, ItemStack> slots) {
        player.getInventory().clear();
        slots.forEach((slot, item) -> {
            ItemStack copy = item.clone();
            if (slot.startsWith("inventory.")) player.getInventory().setItem(Integer.parseInt(slot.substring(10)), copy);
            else switch (slot) {
                case "helmet" -> player.getInventory().setHelmet(copy);
                case "chestplate" -> player.getInventory().setChestplate(copy);
                case "leggings" -> player.getInventory().setLeggings(copy);
                case "boots" -> player.getInventory().setBoots(copy);
                case "offhand" -> player.getInventory().setItemInOffHand(copy);
                default -> throw new IllegalArgumentException("Invalid kit slot: " + slot);
            }
        });
    }
}
