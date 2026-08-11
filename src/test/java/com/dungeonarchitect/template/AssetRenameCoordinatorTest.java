package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssetRenameCoordinatorTest {
    @TempDir
    Path tempDir;

    @Test
    void renamingAssetsUpdatesEveryPersistedReference() throws Exception {
        Path features = tempDir.resolve("features");
        Path oldFeature = features.resolve("old_feature");
        Files.createDirectories(oldFeature);
        Files.writeString(oldFeature.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("old_feature", new IntVector3(1, 1, 1), Set.of(), oldFeature.resolve("feature.nbt")), oldFeature);
        FeatureTemplateRegistry featureRegistry = new FeatureTemplateRegistry(features, null);
        featureRegistry.reload();

        Path doors = tempDir.resolve("doors");
        Path oldDoor = doors.resolve("old_door");
        Files.createDirectories(oldDoor);
        Files.writeString(oldDoor.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate("old_door", new IntVector3(3, 2, 2), Set.of(), List.of(), List.of(
            new RoomFeatureSlot("detail", new IntVector3(0, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("old_feature", 1))
        )), new DoorGateway(new IntVector3(1, 0, 0), new IntVector3(1, 2, 1), Direction3.NORTH), oldDoor.resolve("door.nbt")), oldDoor);
        DoorTemplateRegistry doorRegistry = new DoorTemplateRegistry(doors, null, featureRegistry);
        doorRegistry.reload();
        assertTrue(doorRegistry.get("old_door").isPresent(), doorRegistry.lastValidation().errors().toString());

        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("room");
        Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "fake");
        RoomTemplateIO.save(new RoomTemplate("room", RoomCategory.GENERIC, 1, Set.of(), new IntVector3(5, 5, 5), null,
            List.of(new DoorSocket("door", new IntVector3(1, 1, 0), new IntVector3(3, 2, 2), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry("old_door", 1)))),
            List.of(), List.of(new RoomFeatureSlot("detail", new IntVector3(0, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("old_feature", 1)))), room.resolve("room.nbt")), room);
        RoomTemplateRegistry roomRegistry = new RoomTemplateRegistry(rooms, null, featureRegistry, doorRegistry);
        AssetRenameCoordinator coordinator = new AssetRenameCoordinator(roomRegistry, featureRegistry, doorRegistry);

        coordinator.renameFeature("old_feature", "new_feature");
        assertTrue(!RoomTemplateIO.load(room).doors().getFirst().entries().isEmpty(), roomRegistry.lastValidation().repairs().toString());
        coordinator.renameDoor("old_door", "new_door");

        assertEquals("new_feature", DoorTemplateIO.load(doors.resolve("new_door")).featureSlots().getFirst().entries().getFirst().featureId());
        RoomTemplate updatedRoom = RoomTemplateIO.load(room);
        assertEquals("new_feature", updatedRoom.featureSlots().getFirst().entries().getFirst().featureId());
        assertFalse(updatedRoom.doors().getFirst().entries().isEmpty(), roomRegistry.lastValidation().repairs().toString());
        assertEquals("new_door", updatedRoom.doors().getFirst().entries().getFirst().doorId());
    }

    @Test
    void doorReloadRemovesMissingAndIncompatibleFeatureSelections() throws Exception {
        Path door = tempDir.resolve("doors").resolve("door");
        Files.createDirectories(door);
        Files.writeString(door.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate("door", new IntVector3(3, 2, 2), Set.of(), List.of(), List.of(
            new RoomFeatureSlot("missing", new IntVector3(0, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("missing", 1))),
            new RoomFeatureSlot("small", new IntVector3(1, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("large", 1)))
        ), new DoorGateway(new IntVector3(1, 0, 0), new IntVector3(1, 2, 1), Direction3.NORTH), door.resolve("door.nbt")), door);
        Path largeFeature = tempDir.resolve("features").resolve("large");
        Files.createDirectories(largeFeature);
        Files.writeString(largeFeature.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("large", new IntVector3(2, 1, 1), Set.of(), largeFeature.resolve("feature.nbt")), largeFeature);
        FeatureTemplateRegistry featureRegistry = new FeatureTemplateRegistry(tempDir.resolve("features"), null);
        featureRegistry.reload();
        DoorTemplateRegistry doorRegistry = new DoorTemplateRegistry(tempDir.resolve("doors"), null, featureRegistry);

        var result = doorRegistry.reload();

        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("removed missing or invalid feature missing from slot missing")), result.repairs().toString());
        assertTrue(result.repairs().stream().anyMatch(repair -> repair.contains("removed incompatible feature large from slot small")), result.repairs().toString());
        assertTrue(DoorTemplateIO.load(door).featureSlots().stream().allMatch(slot -> slot.entries().isEmpty()));
    }
}
