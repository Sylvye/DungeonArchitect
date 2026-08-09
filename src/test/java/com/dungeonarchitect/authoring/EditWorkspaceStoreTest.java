package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EditWorkspaceStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void assignsStableNonOverlappingWorkspaces() {
        EditWorkspaceStore store = new EditWorkspaceStore(tempDir.resolve("edit-workspaces.yml"));
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        EditWorkspace firstWorkspace = store.workspace(first);
        EditWorkspace secondWorkspace = store.workspace(second);

        assertEquals(firstWorkspace, store.workspace(first));
        assertFalse(firstWorkspace.clearBounds().intersects(secondWorkspace.clearBounds()));
        assertEquals(new IntVector3(32, 80, 32), firstWorkspace.buildOrigin());
        assertEquals(new IntVector3(544, 80, 32), secondWorkspace.buildOrigin());
    }

    @Test
    void persistsAssignments() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Path file = tempDir.resolve("edit-workspaces.yml");
        EditWorkspace original = new EditWorkspaceStore(file).workspace(owner);

        EditWorkspace loaded = new EditWorkspaceStore(file).workspace(owner);

        assertEquals(original, loaded);
    }

    @Test
    void clearBoundsContainBuildOriginAndTypicalTemplate() {
        EditWorkspace workspace = EditWorkspaceStore.workspace(UUID.randomUUID(), 3);

        assertTrue(workspace.clearBounds().contains(workspace.buildOrigin()));
        assertTrue(workspace.containsTemplate(new IntVector3(21, 10, 21)));
        assertFalse(workspace.containsTemplate(new IntVector3(300, 10, 21)));
    }
}
