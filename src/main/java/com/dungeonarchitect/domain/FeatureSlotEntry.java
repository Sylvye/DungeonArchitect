package com.dungeonarchitect.domain;

public record FeatureSlotEntry(String featureId, int weight) {
    public static final String EMPTY = "empty";

    public FeatureSlotEntry {
        if (featureId == null || featureId.isBlank()) {
            throw new IllegalArgumentException("Feature id is required");
        }
        featureId = featureId.toLowerCase(java.util.Locale.ROOT);
        if (weight <= 0) {
            throw new IllegalArgumentException("Feature weight must be positive");
        }
    }
}
