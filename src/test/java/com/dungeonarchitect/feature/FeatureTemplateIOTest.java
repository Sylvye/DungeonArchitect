package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FeatureTemplateIOTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsFeatureMetadata() throws Exception {
        Path featureDir = tempDir.resolve("chest_pile");
        Files.createDirectories(featureDir);
        FeatureTemplate template = new FeatureTemplate("chest_pile", new IntVector3(2, 3, 4), Set.of("loot", "stone"), featureDir.resolve("feature.nbt"));

        FeatureTemplateIO.save(template, featureDir);
        FeatureTemplate loaded = FeatureTemplateIO.load(featureDir);

        assertEquals(template.id(), loaded.id());
        assertEquals(template.size(), loaded.size());
        assertEquals(template.tags(), loaded.tags());
        assertEquals(template.structureFile(), loaded.structureFile());
    }
}
