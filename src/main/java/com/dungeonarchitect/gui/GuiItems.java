package com.dungeonarchitect.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;

public final class GuiItems {
    private GuiItems() {
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, NamedTextColor.WHITE));
        meta.lore(lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    public static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(Collection<String> lore) {
        return lore.stream().limit(8).map(line -> text(line, NamedTextColor.GRAY)).toList();
    }
}
