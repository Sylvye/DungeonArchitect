package com.dungeonarchitect.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class RoomTemplateRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void deleteRoomRemovesRoomDirectory() throws Exception {
        Path room = tempDir.resolve("rooms").resolve("delete_me");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.yml"), "id: delete_me\nsize: [1,1,1]\n");
        Files.writeString(room.resolve("room.nbt"), "fake");

        RoomTemplateRegistry registry = new RoomTemplateRegistry(tempDir.resolve("rooms"));
        registry.deleteRoom("delete_me");

        assertFalse(Files.exists(room));
    }
}
