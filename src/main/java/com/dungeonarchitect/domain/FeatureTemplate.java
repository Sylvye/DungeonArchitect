package com.dungeonarchitect.domain;

import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import java.util.Map;
import com.dungeonarchitect.loot.LootBinding;

public record FeatureTemplate(
    String id,
    IntVector3 size,
    Set<String> tags,
    List<RoomMarker> markers,
    List<RoomFeatureSlot> featureSlots,
    Map<String, LootBinding> lootBindings,
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
        markers = markers == null ? List.of() : List.copyOf(markers);
        featureSlots = featureSlots == null ? List.of() : List.copyOf(featureSlots);
        lootBindings = lootBindings == null ? Map.of() : Map.copyOf(lootBindings);
    }

    public FeatureTemplate(String id, IntVector3 size, Set<String> tags, Path structureFile) {
        this(id, size, tags, List.of(), List.of(), Map.of(), structureFile);
    }

    public FeatureTemplate(String id, IntVector3 size, Set<String> tags, List<RoomMarker> markers, Map<String, LootBinding> lootBindings, Path structureFile) {
        this(id, size, tags, markers, List.of(), lootBindings, structureFile);
    }
}
