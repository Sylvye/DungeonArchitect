package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthoringSessionTest {
    @Test
    void newSessionStartsWithoutPreviousRoomMetadata() {
        AuthoringSession previous = new AuthoringSession("old_room");
        previous.addDoor("door_1", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2);
        previous.addMarker("spawn", "generic", new IntVector3(1, 1, 1));
        previous.addFeatureSlot("feature_1", "default", new IntVector3(2, 1, 2), Direction3.SOUTH);

        AuthoringSession next = new AuthoringSession("new_room");

        assertEquals("new_room", next.roomId());
        assertTrue(next.doors().isEmpty());
        assertTrue(next.markers().isEmpty());
        assertTrue(next.featureSlots().isEmpty());
        assertTrue(next.currentSelection().isEmpty());
        assertTrue(next.roomBounds().isEmpty());
        assertEquals(1, next.nextDoorNumber());
        assertEquals(1, next.nextFeatureNumber());
    }

    @Test
    void selectedBoundsBecomeCurrentSelection() {
        AuthoringSession session = new AuthoringSession("room");
        SelectionBounds componentBounds = SelectionBounds.between(new IntVector3(4, 5, 6), new IntVector3(4, 7, 8));

        session.selectCurrentBounds(componentBounds);

        assertEquals(componentBounds, session.currentSelection().orElseThrow());
    }

    @Test
    void componentSelectionRecordsTypeAndId() {
        AuthoringSession session = new AuthoringSession("room");
        SelectionBounds componentBounds = SelectionBounds.between(new IntVector3(4, 5, 6), new IntVector3(4, 7, 8));

        session.selectComponentBounds("door", "door_a", componentBounds);

        assertEquals(componentBounds, session.currentSelection().orElseThrow());
        assertEquals(new AuthoringSession.SelectedComponent("door", "door_a"), session.selectedComponent().orElseThrow());
    }

    @Test
    void manualSelectionClearsSelectedComponent() {
        AuthoringSession session = new AuthoringSession("room");
        session.selectComponentBounds("feature", "feature_a", SelectionBounds.between(new IntVector3(4, 5, 6), new IntVector3(4, 7, 8)));

        session.setPosition(1, new org.bukkit.Location(fakeWorld(), 1, 2, 3));

        assertFalse(session.selectedComponent().isPresent());
    }

    @Test
    void arbitraryBoundsClearSelectedComponent() {
        AuthoringSession session = new AuthoringSession("room");
        session.selectComponentBounds("marker", "spawn", SelectionBounds.between(new IntVector3(4, 5, 6), new IntVector3(4, 5, 6)));

        session.selectCurrentBounds(SelectionBounds.between(new IntVector3(1, 2, 3), new IntVector3(3, 4, 5)));

        assertFalse(session.selectedComponent().isPresent());
    }

    @Test
    void renamesComponentsAndRejectsDuplicates() {
        AuthoringSession session = new AuthoringSession("room");
        session.addDoor("door_a", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2);
        session.addDoor("door_b", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2);
        session.addMarker("marker_a", "generic", new IntVector3(1, 1, 1));
        session.addFeatureSlot(new RoomFeatureSlot("slot_a", new IntVector3(1, 1, 1), new IntVector3(1, 1, 1), Direction3.NORTH));

        assertTrue(session.renameDoor("door_a", "door_c"));
        assertTrue(session.renameMarker("marker_a", "marker_c"));
        assertTrue(session.renameFeatureSlot("slot_a", "slot_c"));

        assertEquals("door_c", session.doors().getFirst().id());
        assertEquals("marker_c", session.markers().getFirst().name());
        assertEquals("slot_c", session.featureSlots().getFirst().id());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> session.renameDoor("door_c", "door_b"));
    }

    @Test
    void loadTemplateForEditCopiesMetadataAndWorldBounds() {
        RoomTemplate template = new RoomTemplate(
            "edit_room",
            RoomCategory.START,
            7,
            Set.of("stone", "starter"),
            new IntVector3(5, 4, 6),
            new IntVector3(2, 1, 2),
            List.of(new DoorSocket("door_a", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 2, 3)),
            List.of(new RoomMarker("spawn", "player", new IntVector3(2, 1, 2))),
            List.of(new RoomFeatureSlot("feature_a", "chest", "chest", new IntVector3(3, 1, 3), Direction3.SOUTH)),
            Path.of("room.nbt")
        );
        AuthoringSession session = new AuthoringSession("blank");
        IntVector3 origin = new IntVector3(10, 80, -5);

        session.loadTemplateForEdit(template, fakeWorld(), origin);

        assertTrue(session.editingExistingRoom());
        assertEquals("edit_room", session.roomId());
        assertEquals(RoomCategory.START, session.category());
        assertEquals(7, session.weight());
        assertEquals(Set.of("stone", "starter"), session.tags());
        assertEquals(new IntVector3(2, 1, 2), session.spawn());
        assertEquals(template.doors(), session.doors());
        assertEquals(template.markers(), session.markers());
        assertEquals(template.featureSlots(), session.featureSlots());
        SelectionBounds bounds = session.roomBounds().orElseThrow();
        assertEquals(origin, bounds.min());
        assertEquals(new IntVector3(14, 83, 0), bounds.max());
        assertEquals(template.size(), bounds.size());
        assertEquals(2, session.nextDoorNumber());
        assertEquals(2, session.nextFeatureNumber());
    }

    private World fakeWorld() {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getUID")) {
                    return java.util.UUID.randomUUID();
                }
                if (method.getName().equals("toString")) {
                    return "fakeWorld";
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
