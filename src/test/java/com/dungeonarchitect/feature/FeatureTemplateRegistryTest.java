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
        assertTrue(registry.getVisible("bad").isPresent());
        assertTrue(registry.visible().stream().anyMatch(template -> template.id().equals("bad")));
        assertFalse(registry.all().stream().anyMatch(template -> template.id().equals("bad")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing feature.nbt")));
    }

    @Test
    void repairsMissingIdFromDirectoryName() throws Exception {
        Path feature = tempDir.resolve("missing_id");
        Files.createDirectories(feature);
        Files.writeString(feature.resolve("feature.nbt"), "fake");
        Files.writeString(feature.resolve("feature.yml"), "id: ''\nsize: [1, 1, 1]\n");

        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);
        var result = registry.reload();

        assertTrue(registry.getVisible("missing_id").isPresent());
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing feature id")));
    }

    @Test
    void repairsMissingSizeFromStructureReaderWithoutSavingMetadata() throws Exception {
        Path feature = tempDir.resolve("size_from_nbt");
        Files.createDirectories(feature);
        Files.writeString(feature.resolve("feature.nbt"), "fake");
        Files.writeString(feature.resolve("feature.yml"), "id: size_from_nbt\n");
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, structureFile -> new IntVector3(2, 3, 4), true);

        var result = registry.reload();

        assertTrue(registry.get("size_from_nbt").isPresent(), result.errors().toString());
        assertEquals(new IntVector3(2, 3, 4), registry.getVisible("size_from_nbt").orElseThrow().size());
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing or malformed feature.yml size")));
        assertFalse(Files.readString(feature.resolve("feature.yml")).contains("size:"));
    }

    @Test
    void unrecoverableMetadataGetsStatusInsteadOfDisappearingSilently() throws Exception {
        Path feature = tempDir.resolve("broken");
        Files.createDirectories(feature);
        Files.writeString(feature.resolve("feature.yml"), "id: broken\n");
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);

        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.getVisible("broken").isEmpty());
        assertEquals(1, registry.unrecoverableCount());
        assertTrue(registry.loadStatuses().stream().anyMatch(status -> status.id().equals("broken") && !status.loadable()));
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
