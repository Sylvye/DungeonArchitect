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
}
