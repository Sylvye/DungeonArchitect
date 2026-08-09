package com.dungeonarchitect.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonArchitectCommandTabCompletionTest {
    @Test
    void componentActionsIncludeBoundsAndRename() {
        DungeonArchitectCommand command = new DungeonArchitectCommand("test", null, null, null, null, null, null);

        List<String> options = command.onTabComplete(null, null, "da", new String[] {"room", "component", ""});

        assertTrue(options.contains("select"));
        assertTrue(options.contains("remove"));
        assertTrue(options.contains("bounds"));
        assertTrue(options.contains("rename"));
    }
}
