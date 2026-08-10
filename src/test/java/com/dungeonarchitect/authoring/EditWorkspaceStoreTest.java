package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.BoundingBox3i;
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
    void tracksDirtyBoundsAndClearsThem() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Path file = tempDir.resolve("edit-workspaces.yml");
        EditWorkspaceStore store = new EditWorkspaceStore(file);
        EditWorkspace workspace = store.workspace(owner);

        store.markDirty(owner, new BoundingBox3i(workspace.buildOrigin(), workspace.buildOrigin()));
        store.markDirty(owner, new BoundingBox3i(workspace.buildOrigin().add(new IntVector3(2, 3, 4)), workspace.buildOrigin().add(new IntVector3(2, 3, 4))));

        BoundingBox3i dirty = new EditWorkspaceStore(file).dirtyBounds(owner).orElseThrow();
        assertEquals(workspace.buildOrigin(), dirty.min());
        assertEquals(workspace.buildOrigin().add(new IntVector3(2, 3, 4)), dirty.max());

        EditWorkspaceStore loaded = new EditWorkspaceStore(file);
        loaded.markClean(owner);
        assertTrue(new EditWorkspaceStore(file).dirtyBounds(owner).isEmpty());
    }

    @Test
    void oldAssignmentWithoutInitializedFlagNeedsLegacyClearOnce() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Path file = tempDir.resolve("edit-workspaces.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("players." + owner, 0);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> yaml.save(file.toFile()));

        EditWorkspaceStore store = new EditWorkspaceStore(file);
        assertTrue(store.needsLegacyClear(owner));

        store.markClean(owner);
        assertFalse(new EditWorkspaceStore(file).needsLegacyClear(owner));
    }

    @Test
    void clearBoundsContainBuildOriginAndTypicalTemplate() {
        EditWorkspace workspace = EditWorkspaceStore.workspace(UUID.randomUUID(), 3);

        assertTrue(workspace.clearBounds().contains(workspace.buildOrigin()));
        assertTrue(workspace.containsTemplate(new IntVector3(21, 10, 21)));
        assertFalse(workspace.containsTemplate(new IntVector3(300, 10, 21)));
    }
}
