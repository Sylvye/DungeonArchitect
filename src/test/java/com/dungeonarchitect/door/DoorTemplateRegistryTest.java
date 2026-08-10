package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoorTemplateRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void duplicateAndRenameDoorUpdateMetadataId() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate(
            "source",
            new IntVector3(3, 4, 1),
            Set.of("stone"),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH),
            source.resolve("door.nbt")
        ), source);
        DoorTemplateRegistry registry = new DoorTemplateRegistry(tempDir, null);

        DoorTemplate duplicated = registry.duplicateDoor("source", "copy");
        DoorTemplate renamed = registry.renameDoor("copy", "renamed");

        assertEquals("copy", duplicated.id());
        assertEquals("renamed", renamed.id());
        assertTrue(Files.exists(tempDir.resolve("source").resolve("door.nbt")));
        assertFalse(Files.exists(tempDir.resolve("copy")));
        assertEquals("renamed", DoorTemplateIO.load(tempDir.resolve("renamed")).id());
    }

    @Test
    void invalidDoorRemainsVisibleButNotValid() throws Exception {
        Path door = tempDir.resolve("missing_gateway");
        Files.createDirectories(door);
        Files.writeString(door.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate("missing_gateway", new IntVector3(3, 4, 1), Set.of(), List.of(), List.of(), null, door.resolve("door.nbt")), door);
        DoorTemplateRegistry registry = new DoorTemplateRegistry(tempDir, null);

        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.getVisible("missing_gateway").isPresent());
        assertTrue(registry.get("missing_gateway").isEmpty());
        assertEquals(1, registry.invalidCount());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing gateway")));
    }

    @Test
    void repairsMissingIdAndSizeFromStructureReader() throws Exception {
        Path door = tempDir.resolve("repaired_door");
        Files.createDirectories(door);
        Files.writeString(door.resolve("door.nbt"), "fake");
        Files.writeString(door.resolve("door.yml"), "id: ''\ngateway:\n  position: [1, 1, 0]\n  size: [1, 2, 1]\n  facing: NORTH\n");
        DoorTemplateRegistry registry = new DoorTemplateRegistry(tempDir, structureFile -> new IntVector3(3, 4, 2), true);

        var result = registry.reload();

        assertTrue(registry.get("repaired_door").isPresent(), result.errors().toString());
        assertEquals(new IntVector3(3, 4, 2), registry.getVisible("repaired_door").orElseThrow().size());
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing door id")));
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing or malformed door.yml size")));
    }

    @Test
    void duplicateIdsKeepFirstVisibleDoorAndMarkLaterEntryInvalid() throws Exception {
        saveDoor(tempDir.resolve("first"), "dupe");
        saveDoor(tempDir.resolve("second"), "dupe");
        DoorTemplateRegistry registry = new DoorTemplateRegistry(tempDir, null);

        var result = registry.reload();

        assertEquals(1, registry.visible().size());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Duplicate door id dupe")));
        assertTrue(registry.loadStatuses().stream().anyMatch(status -> !status.valid() && status.errors().stream().anyMatch(error -> error.contains("Duplicate door id dupe"))));
    }

    private void saveDoor(Path directory, String id) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate(
            id,
            new IntVector3(3, 4, 1),
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH),
            directory.resolve("door.nbt")
        ), directory);
    }
}
