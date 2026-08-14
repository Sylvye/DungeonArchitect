package com.dungeonarchitect.gui;

import com.dungeonarchitect.loot.LootEntry;
import com.dungeonarchitect.loot.LootTable;
import org.bukkit.inventory.ItemStack;

final class LootEditorTemplates {
    private LootEditorTemplates() { }

    static ItemStack normalize(ItemStack source) {
        ItemStack template = source.clone();
        template.setAmount(1);
        return template;
    }

    static LootEntry normalize(LootEntry entry) {
        return new LootEntry(normalize(entry.item()), entry.weight(), entry.minimumAmount(), entry.maximumAmount(), entry.maximumPerContainer());
    }

    static LootTable normalize(LootTable table) {
        return new LootTable(table.id(), table.minimumRolls(), table.maximumRolls(), table.entries().stream().map(LootEditorTemplates::normalize).toList());
    }

    static boolean requiresNormalization(LootTable table) {
        return table.entries().stream().anyMatch(entry -> entry.item().getAmount() != 1);
    }
}
