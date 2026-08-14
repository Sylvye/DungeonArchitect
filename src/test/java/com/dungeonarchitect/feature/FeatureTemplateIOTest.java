package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.loot.LootBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FeatureTemplateIOTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsFeatureMetadata() throws Exception {
        Path featureDir = tempDir.resolve("chest_pile");
        Files.createDirectories(featureDir);
        FeatureTemplate template = new FeatureTemplate(
            "chest_pile", new IntVector3(2, 3, 4), Set.of("loot", "stone"),
            List.of(new RoomMarker("reward", "generic", new IntVector3(1, 0, 1))),
            List.of(new RoomFeatureSlot("detail", IntVector3.ZERO, new IntVector3(2, 2, 2), Direction3.NORTH, List.of(new FeatureSlotEntry("small_detail", 2)))),
            Map.of("reward", new LootBinding("feature_loot", 1, 3)), featureDir.resolve("feature.nbt")
        );

        FeatureTemplateIO.save(template, featureDir);
        FeatureTemplate loaded = FeatureTemplateIO.load(featureDir);

        assertEquals(template.id(), loaded.id());
        assertEquals(template.size(), loaded.size());
        assertEquals(template.tags(), loaded.tags());
        assertEquals(template.markers(), loaded.markers());
        assertEquals(template.featureSlots(), loaded.featureSlots());
        assertEquals(template.lootBindings(), loaded.lootBindings());
        assertEquals(template.structureFile(), loaded.structureFile());
    }

    @Test
    void legacyMetadataLoadsWithNoNestedSlots() throws Exception {
        Path featureDir = tempDir.resolve("legacy");
        Files.createDirectories(featureDir);
        Files.writeString(featureDir.resolve("feature.yml"), "id: legacy\nsize: [1, 1, 1]\n");

        assertEquals(List.of(), FeatureTemplateIO.load(featureDir).featureSlots());
    }
}
