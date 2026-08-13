package com.dungeonarchitect.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LootTableIO {
    private LootTableIO() { }

    public static LootTable load(Path file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        String id = yaml.getString("id", stripExtension(file.getFileName().toString()));
        List<LootEntry> entries = new ArrayList<>();
        for (Object raw : yaml.getList("entries", List.of())) {
            ConfigurationSection section = section(raw);
            if (section == null) continue;
            Object itemRaw = section.get("item");
            ItemStack item = itemRaw instanceof ItemStack stack ? stack : ItemStack.deserialize(map(itemRaw));
            entries.add(new LootEntry(item, section.getInt("weight", 1), section.getInt("minimumAmount", item.getAmount()), section.getInt("maximumAmount", item.getAmount()), section.getInt("maximumPerContainer", 0)));
        }
        return new LootTable(id, yaml.getInt("minimumRolls", 1), yaml.getInt("maximumRolls", 1), entries);
    }

    public static void save(LootTable table, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", table.id());
        yaml.set("minimumRolls", table.minimumRolls());
        yaml.set("maximumRolls", table.maximumRolls());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (LootEntry entry : table.entries()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("item", entry.item().serialize());
            data.put("weight", entry.weight());
            data.put("minimumAmount", entry.minimumAmount());
            data.put("maximumAmount", entry.maximumAmount());
            data.put("maximumPerContainer", entry.maximumPerContainer());
            entries.add(data);
        }
        yaml.set("entries", entries);
        yaml.save(file.toFile());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        throw new IllegalArgumentException("Loot entry has no serialized item");
    }

    private static ConfigurationSection section(Object raw) {
        if (raw instanceof ConfigurationSection section) return section;
        if (!(raw instanceof Map<?, ?> map)) return null;
        YamlConfiguration result = new YamlConfiguration();
        map.forEach((key, value) -> result.set(String.valueOf(key), value));
        return result;
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }
}
