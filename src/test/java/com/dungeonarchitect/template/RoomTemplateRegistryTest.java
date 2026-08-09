package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void duplicateAndRenameRoomUpdateMetadataId() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        saveRoom(rooms.resolve("source"), "source");
        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms);

        RoomTemplate duplicated = registry.duplicateRoom("source", "copy");
        RoomTemplate renamed = registry.renameRoom("copy", "renamed");

        assertEquals("copy", duplicated.id());
        assertEquals("renamed", renamed.id());
        assertTrue(Files.exists(rooms.resolve("source").resolve("room.nbt")));
        assertFalse(Files.exists(rooms.resolve("copy")));
        assertEquals("renamed", RoomTemplateIO.load(rooms.resolve("renamed")).id());
    }

    @Test
    void replacesFeatureReferencesAcrossRooms() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("room");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "fake");
        RoomTemplateIO.save(new RoomTemplate(
            "room",
            RoomCategory.GENERIC,
            1,
            Set.of(),
            new IntVector3(5, 5, 5),
            null,
            List.of(),
            List.of(),
            List.of(new RoomFeatureSlot("slot", new IntVector3(1, 1, 1), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("old_feature", 3)))),
            room.resolve("room.nbt")
        ), room);

        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms);
        registry.replaceFeatureReferences("old_feature", "new_feature");

        assertEquals("new_feature", RoomTemplateIO.load(room).featureSlots().getFirst().entries().getFirst().featureId());
    }

    private void saveRoom(Path room, String id) throws Exception {
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "fake");
        RoomTemplateIO.save(new RoomTemplate(
            id,
            RoomCategory.GENERIC,
            1,
            Set.of(),
            new IntVector3(1, 1, 1),
            null,
            List.of(),
            List.of(),
            List.of(),
            room.resolve("room.nbt")
        ), room);
    }
}
