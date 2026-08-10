package com.dungeonarchitect.command;

import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonArchitectCommandTabCompletionTest {
    @TempDir
    Path tempDir;

    @Test
    void componentActionsIncludeBoundsAndRename() {
        DungeonArchitectCommand command = new DungeonArchitectCommand("test", null, null, null, null, null, null);

        List<String> options = command.onTabComplete(null, null, "da", new String[] {"room", "component", ""});

        assertTrue(options.contains("select"));
        assertTrue(options.contains("remove"));
        assertTrue(options.contains("bounds"));
        assertTrue(options.contains("rename"));
    }

    @Test
    void templateActionsIncludeRenameAndDuplicate() {
        DungeonArchitectCommand command = command();

        assertContains(command.onTabComplete(null, null, "da", new String[] {"room", ""}), "rename", "duplicate");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"feature", ""}), "rename", "duplicate");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"door", ""}), "rename", "duplicate");
        assertContains(command.onTabComplete(null, null, "da", new String[] {""}), "diagnose");
    }

    @Test
    void renameAndDuplicateSuggestNewIdPlaceholder() {
        DungeonArchitectCommand command = command();

        assertContains(command.onTabComplete(null, null, "da", new String[] {"room", "rename", "old", ""}), "new_id");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"feature", "duplicate", "old", ""}), "new_id");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"door", "rename", "old", ""}), "new_id");
    }

    @Test
    void roomDoorSuggestsSocketTypesAndFacings() {
        DungeonArchitectCommand command = command();

        assertContains(command.onTabComplete(null, null, "da", new String[] {"room", "door", "slot", ""}), "STANDARD", "STAIRS_UP");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"room", "door", "slot", "STANDARD", ""}), "NORTH", "UP", "DOWN");
    }

    @Test
    void templateIdCompletionIncludesInvalidVisibleTemplates() throws Exception {
        Path rooms = tempDir.resolve("rooms");
        Path room = rooms.resolve("invalid_room");
        Files.createDirectories(room);
        RoomTemplateIO.save(new RoomTemplate("invalid_room", RoomCategory.GENERIC, 1, Set.of(), new IntVector3(1, 1, 1), null, List.of(), List.of(), List.of(), room.resolve("room.nbt")), room);
        Path features = tempDir.resolve("features");
        Path feature = features.resolve("invalid_feature");
        Files.createDirectories(feature);
        FeatureTemplateIO.save(new FeatureTemplate("invalid_feature", new IntVector3(1, 1, 1), Set.of(), feature.resolve("feature.nbt")), feature);
        Path doors = tempDir.resolve("doors");
        Path door = doors.resolve("invalid_door");
        Files.createDirectories(door);
        Files.writeString(door.resolve("door.nbt"), "fake");
        DoorTemplateIO.save(new DoorTemplate("invalid_door", new IntVector3(1, 1, 1), Set.of(), List.of(), List.of(), null, door.resolve("door.nbt")), door);

        RoomTemplateRegistry roomRegistry = new RoomTemplateRegistry(rooms);
        FeatureTemplateRegistry featureRegistry = new FeatureTemplateRegistry(features, null);
        DoorTemplateRegistry doorRegistry = new DoorTemplateRegistry(doors, null);
        roomRegistry.reload();
        featureRegistry.reload();
        doorRegistry.reload();
        DungeonArchitectCommand command = new DungeonArchitectCommand("test", null, roomRegistry, featureRegistry, doorRegistry, null, null, null);

        assertContains(command.onTabComplete(null, null, "da", new String[] {"room", "inspect", ""}), "invalid_room");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"feature", "inspect", ""}), "invalid_feature");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"door", "inspect", ""}), "invalid_door");
        assertContains(command.onTabComplete(null, null, "da", new String[] {"diagnose", "room", ""}), "all", "invalid_room");
    }

    private DungeonArchitectCommand command() {
        return new DungeonArchitectCommand(
            "test",
            null,
            new RoomTemplateRegistry(tempDir.resolve("rooms")),
            new FeatureTemplateRegistry(tempDir.resolve("features"), null),
            new DoorTemplateRegistry(tempDir.resolve("doors"), null),
            null,
            null,
            null
        );
    }

    private void assertContains(List<String> options, String... expected) {
        for (String value : expected) {
            assertTrue(options.contains(value), options.toString());
        }
    }
}
