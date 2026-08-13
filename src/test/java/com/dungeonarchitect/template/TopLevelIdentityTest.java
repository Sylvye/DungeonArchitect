package com.dungeonarchitect.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TopLevelIdentityTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsCaseInsensitiveCrossKindIdsButAllowsOwnUpdate() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Files.createDirectories(rooms.resolve("crypt"));
        Files.createDirectories(tempDir.resolve("loot-tables"));
        Files.writeString(tempDir.resolve("loot-tables/treasure.yml"), "id: treasure");

        assertThrows(IllegalArgumentException.class, () -> TopLevelIdentity.requireAvailable(tempDir.resolve("doors"), "door", "CRYPT", null));
        assertThrows(IllegalArgumentException.class, () -> TopLevelIdentity.requireAvailable(tempDir.resolve("features"), "feature", " treasure ", null));
        assertDoesNotThrow(() -> TopLevelIdentity.requireAvailable(rooms, "room", "CRYPT", "crypt"));
    }
}
