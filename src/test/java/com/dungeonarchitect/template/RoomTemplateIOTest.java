package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoomTemplateIOTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsEditableRoomMetadata() throws Exception {
        Path roomDir = tempDir.resolve("crypt_start_01");
        Files.createDirectories(roomDir);
        Files.writeString(roomDir.resolve("room.nbt"), "fake");
        RoomTemplate template = new RoomTemplate(
            "crypt_start_01",
            RoomCategory.START,
            7,
            Set.of("crypt", "start"),
            new IntVector3(9, 6, 9),
            new IntVector3(4, 1, 4),
            List.of(new DoorSocket("door_1", new IntVector3(4, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 3)),
            List.of(new RoomMarker("reward", "generic", new IntVector3(4, 1, 4))),
            List.of(new RoomFeatureSlot("slot_1", "default", new IntVector3(2, 1, 2), Direction3.SOUTH)),
            roomDir.resolve("room.nbt")
        );

        RoomTemplateIO.save(template, roomDir);
        RoomTemplate loaded = RoomTemplateIO.load(roomDir);

        assertEquals(template.id(), loaded.id());
        assertEquals(template.category(), loaded.category());
        assertEquals(template.weight(), loaded.weight());
        assertEquals(template.size(), loaded.size());
        assertEquals(template.spawn(), loaded.spawn());
        assertEquals(template.doors(), loaded.doors());
        assertEquals(template.markers(), loaded.markers());
        assertEquals(template.featureSlots(), loaded.featureSlots());
    }
}
