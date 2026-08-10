package com.dungeonarchitect.command;

import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.nio.file.Path;

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
