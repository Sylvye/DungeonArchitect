package com.dungeonarchitect.feature;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class FeaturePoolRegistry {
    private final File file;
    private Map<String, List<FeatureEntry>> pools = Map.of();

    public FeaturePoolRegistry(File file) {
        this.file = file;
    }

    public List<String> reload() {
        List<String> errors = new ArrayList<>();
        Map<String, List<FeatureEntry>> loaded = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection poolsSection = yaml.getConfigurationSection("pools");
        if (poolsSection == null) {
            errors.add("feature-pools.yml has no pools section");
            pools = Map.of();
            return errors;
        }
        for (String poolId : poolsSection.getKeys(false)) {
            List<FeatureEntry> entries = new ArrayList<>();
            for (Object item : poolsSection.getList(poolId, List.of())) {
                ConfigurationSection section = section(item);
                if (section == null) {
                    continue;
                }
                try {
                    FeatureType type = FeatureType.valueOf(section.getString("type", "EMPTY").toUpperCase(Locale.ROOT));
                    Material material = null;
                    if (type == FeatureType.BLOCK) {
                        material = Material.matchMaterial(section.getString("material", "AIR"));
                        if (material == null || !material.isBlock()) {
                            throw new IllegalArgumentException("invalid block material");
                        }
                    }
                    entries.add(new FeatureEntry(section.getString("id"), section.getInt("weight", 1), type, material));
                } catch (RuntimeException ex) {
                    errors.add("Feature pool " + poolId + ": " + ex.getMessage());
                }
            }
            loaded.put(poolId, List.copyOf(entries));
        }
        pools = Map.copyOf(loaded);
        return errors;
    }

    public Optional<List<FeatureEntry>> get(String poolId) {
        return Optional.ofNullable(pools.get(poolId));
    }

    public List<String> poolIds() {
        return pools.keySet().stream().sorted().toList();
    }

    private ConfigurationSection section(Object item) {
        if (item instanceof ConfigurationSection section) {
            return section;
        }
        if (item instanceof Map<?, ?> map) {
            YamlConfiguration section = new YamlConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                section.set(String.valueOf(entry.getKey()), entry.getValue());
            }
            return section;
        }
        return null;
    }
}
