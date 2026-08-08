package com.dungeonarchitect.feature;

import org.bukkit.Material;

public record FeatureEntry(String id, int weight, FeatureType type, Material material) {
    public FeatureEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feature id is required");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Feature weight must be positive");
        }
    }
}
