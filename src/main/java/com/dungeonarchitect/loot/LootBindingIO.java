package com.dungeonarchitect.loot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LootBindingIO {
    private LootBindingIO() { }

    public static Map<String, LootBinding> read(ConfigurationSection yaml) {
        Map<String, LootBinding> bindings = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("lootBindings");
        if (section == null) return bindings;
        for (String marker : section.getKeys(false)) {
            Object raw = section.get(marker);
            if (raw instanceof String tableId && !tableId.isBlank()) {
                bindings.put(marker, new LootBinding(tableId));
                continue;
            }
            ConfigurationSection binding = section.getConfigurationSection(marker);
            if (binding == null) continue;
            String tableId = binding.getString("table");
            if (tableId == null || tableId.isBlank()) continue;
            bindings.put(marker, new LootBinding(tableId, binding.getInt("minimumRolls", 1), binding.getInt("maximumRolls", 1)));
        }
        return bindings;
    }

    public static Map<String, Object> serialize(Map<String, LootBinding> bindings) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        bindings.forEach((marker, binding) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("table", binding.tableId());
            value.put("minimumRolls", binding.minimumRolls());
            value.put("maximumRolls", binding.maximumRolls());
            serialized.put(marker, value);
        });
        return serialized;
    }
}
