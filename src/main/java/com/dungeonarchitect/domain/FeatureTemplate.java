package com.dungeonarchitect.domain;

import java.nio.file.Path;
import java.util.Set;

public record FeatureTemplate(
    String id,
    IntVector3 size,
    Set<String> tags,
    Path structureFile
) {
    public FeatureTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feature id is required");
        }
        if (id.equalsIgnoreCase(FeatureSlotEntry.EMPTY)) {
            throw new IllegalArgumentException("empty is reserved");
        }
        id = id.toLowerCase(java.util.Locale.ROOT);
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("Feature size must be positive");
        }
        tags = Set.copyOf(tags);
    }
}
