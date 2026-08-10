package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateRegistry;
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

    @Test
    void replacesDoorReferencesAcrossRooms() throws Exception {
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
            List.of(new DoorSocket("slot", new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry("old_door", 3)))),
            List.of(),
            List.of(),
            room.resolve("room.nbt")
        ), room);

        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms);
        registry.replaceDoorReferences("old_door", "new_door");

        assertEquals("new_door", RoomTemplateIO.load(room).doors().getFirst().entries().getFirst().doorId());
    }

    @Test
    void reloadRemovesIncompatibleSelectedDoorEntriesAndLogsRepair() throws Exception {
        Path doors = tempDir.resolve("doors");
        Path cellar = doors.resolve("cellar");
        Files.createDirectories(cellar);
        Files.writeString(cellar.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate(
            "cellar",
            new IntVector3(3, 1, 3),
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(0, 0, 0), new IntVector3(3, 1, 3), Direction3.DOWN),
            cellar.resolve("door.nbt")
        ), cellar);
        DoorTemplateRegistry doorRegistry = new DoorTemplateRegistry(doors, null);
        doorRegistry.reload();

        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("stairwell");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "fake");
        RoomTemplateIO.save(new RoomTemplate(
            "stairwell",
            RoomCategory.GENERIC,
            1,
            Set.of(),
            new IntVector3(5, 5, 5),
            null,
            List.of(new DoorSocket("door_2", new IntVector3(1, 4, 1), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of(new DoorSlotEntry("cellar", 1)))),
            List.of(),
            List.of(),
            room.resolve("room.nbt")
        ), room);
        RoomTemplateRegistry roomRegistry = new RoomTemplateRegistry(rooms, null, null, doorRegistry);

        var result = roomRegistry.reload();

        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("removed missing or invalid door cellar from slot door_2")), result.repairs().toString());
        assertTrue(RoomTemplateIO.load(room).doors().getFirst().entries().isEmpty());
    }

    @Test
    void validationInvalidRoomRemainsVisibleButExcludedFromValidTemplates() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("invalid");
        Files.createDirectories(room);
        RoomTemplateIO.save(new RoomTemplate(
            "invalid",
            RoomCategory.GENERIC,
            1,
            Set.of(),
            new IntVector3(5, 5, 5),
            null,
            List.of(),
            List.of(),
            List.of(),
            room.resolve("room.nbt")
        ), room);
        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms);

        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.getVisible("invalid").isPresent());
        assertTrue(registry.get("invalid").isEmpty());
        assertEquals(1, registry.invalidCount());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing room.nbt")));
    }

    @Test
    void repairsMissingRoomIdAndSizeFromStructureReader() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("repaired_room");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "fake");
        Files.writeString(room.resolve("room.yml"), "id: ''\n");
        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms, structureFile -> new IntVector3(4, 5, 6), null, null);

        var result = registry.reload();

        assertTrue(registry.get("repaired_room").isPresent(), result.errors().toString());
        assertEquals(new IntVector3(4, 5, 6), registry.getVisible("repaired_room").orElseThrow().size());
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing room id")));
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("repaired missing or malformed room.yml size")));
        assertFalse(Files.readString(room.resolve("room.yml")).contains("size:"));
    }

    @Test
    void unrecoverableRoomMetadataGetsStatus() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("broken");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.yml"), "id: broken\n");
        RoomTemplateRegistry registry = new RoomTemplateRegistry(rooms);

        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.getVisible("broken").isEmpty());
        assertEquals(1, registry.unrecoverableCount());
        assertTrue(registry.loadStatuses().stream().anyMatch(status -> status.id().equals("broken") && !status.loadable()));
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
