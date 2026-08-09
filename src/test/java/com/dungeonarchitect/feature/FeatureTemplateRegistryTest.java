package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureTemplateRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsValidFeatureAndRejectsMissingStructure() throws Exception {
        Path good = tempDir.resolve("good");
        Files.createDirectories(good);
        Files.writeString(good.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("good", new IntVector3(1, 1, 1), Set.of(), good.resolve("feature.nbt")), good);

        Path bad = tempDir.resolve("bad");
        Files.createDirectories(bad);
        FeatureTemplateIO.save(new FeatureTemplate("bad", new IntVector3(1, 1, 1), Set.of(), bad.resolve("feature.nbt")), bad);

        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);
        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.get("good").isPresent());
        assertTrue(registry.get("GOOD").isPresent());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing feature.nbt")));
    }

    @Test
    void duplicateAndRenameFeatureUpdateMetadataId() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("source", new IntVector3(1, 2, 3), Set.of("tag"), source.resolve("feature.nbt")), source);
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);

        FeatureTemplate duplicated = registry.duplicateFeature("source", "copy");
        FeatureTemplate renamed = registry.renameFeature("copy", "renamed");

        assertEquals("copy", duplicated.id());
        assertEquals("renamed", renamed.id());
        assertTrue(Files.exists(tempDir.resolve("source").resolve("feature.nbt")));
        assertFalse(Files.exists(tempDir.resolve("copy")));
        assertEquals("renamed", FeatureTemplateIO.load(tempDir.resolve("renamed")).id());
    }
}
