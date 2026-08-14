package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import java.util.Map;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.Direction3;

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

    @Test
    void quarantinesCyclicFeaturesButKeepsThemVisible() throws Exception {
        saveNested("a", "b");
        saveNested("b", "a");
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null, new FeatureNestingPolicy());

        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.getVisible("a").isPresent());
        assertTrue(registry.getVisible("b").isPresent());
        assertTrue(registry.get("a").isEmpty());
        assertTrue(registry.get("b").isEmpty());
        assertTrue(registry.status("a").orElseThrow().errors().stream().anyMatch(error -> error.contains("cycle")));
    }

    @Test
    void prospectiveValidationRejectsAncestorDepthRegression() throws Exception {
        saveNested("a", "b");
        saveNested("b", "c");
        saveNested("c", null);
        saveNested("d", null);
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null, new FeatureNestingPolicy(3, 256));
        registry.reload();
        FeatureTemplate c = registry.get("c").orElseThrow();
        RoomFeatureSlot child = new RoomFeatureSlot("child", IntVector3.ZERO, new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("d", 1)));
        FeatureTemplate changed = new FeatureTemplate(c.id(), c.size(), c.tags(), c.markers(), List.of(child), c.lootBindings(), c.structureFile());

        assertFalse(registry.validateProspective(changed).valid());
        assertTrue(registry.validateProspective(changed).errors().stream().anyMatch(error -> error.contains("depth 4")));
    }

    private void saveNested(String id, String childId) throws Exception {
        Path directory = tempDir.resolve(id);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("feature.nbt"), "fake");
        List<RoomFeatureSlot> slots = childId == null ? List.of() : List.of(new RoomFeatureSlot(
            "child", IntVector3.ZERO, new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry(childId, 1))));
        FeatureTemplateIO.save(new FeatureTemplate(id, new IntVector3(1, 1, 1), Set.of(), List.of(), slots, Map.of(), directory.resolve("feature.nbt")), directory);
    }
}
