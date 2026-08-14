package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.domain.FeatureTemplate;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void componentSelectionRecordsTypeAndIdWithoutChangingCurrentSelection() {
        AuthoringSession session = new AuthoringSession("room");
        SelectionBounds current = SelectionBounds.between(new IntVector3(1, 2, 3), new IntVector3(2, 3, 4));
        session.selectCurrentBounds(current);

        session.selectComponent("door", "door_a");

        assertEquals(current, session.currentSelection().orElseThrow());
        assertEquals(new AuthoringSession.SelectedComponent("door", "door_a"), session.selectedComponent().orElseThrow());
    }

    @Test
    void manualSelectionKeepsSelectedComponentForBoundsEditing() {
        AuthoringSession session = new AuthoringSession("room");
        session.selectComponent("feature", "feature_a");

        session.setPosition(1, new org.bukkit.Location(fakeWorld(), 1, 2, 3));

        assertEquals(new AuthoringSession.SelectedComponent("feature", "feature_a"), session.selectedComponent().orElseThrow());
    }

    @Test
    void arbitraryBoundsKeepSelectedComponentForBoundsEditing() {
        AuthoringSession session = new AuthoringSession("room");
        session.selectComponent("marker", "spawn");

        session.selectCurrentBounds(SelectionBounds.between(new IntVector3(1, 2, 3), new IntVector3(3, 4, 5)));

        assertEquals(new AuthoringSession.SelectedComponent("marker", "spawn"), session.selectedComponent().orElseThrow());
    }

    @Test
    void deletingSelectedComponentClearsSelection() {
        AuthoringSession session = new AuthoringSession("room");
        session.addDoor("door_a", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2);
        session.selectComponent("door", "door_a");

        assertTrue(session.removeDoor("door_a"));

        assertTrue(session.selectedComponent().isEmpty());
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
    void updatesComponentBoundsAndPreservesMetadata() {
        AuthoringSession session = new AuthoringSession("room");
        session.addDoor("door_a", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2);
        session.addMarker("marker_a", "spawn", new IntVector3(1, 1, 1));
        session.addFeatureSlot(new RoomFeatureSlot("slot_a", new IntVector3(1, 1, 1), new IntVector3(1, 1, 1), Direction3.SOUTH));

        assertTrue(session.updateDoorBounds("door_a", SelectionBounds.between(new IntVector3(2, 3, 0), new IntVector3(4, 6, 0)), Direction3.NORTH));
        assertTrue(session.updateMarkerPosition("marker_a", new IntVector3(5, 6, 7)));
        assertTrue(session.updateFeatureSlotBounds("slot_a", SelectionBounds.between(new IntVector3(8, 1, 8), new IntVector3(10, 2, 11))));

        DoorSocket door = session.doors().getFirst();
        assertEquals(new IntVector3(2, 3, 0), door.position());
        assertEquals(Direction3.NORTH, door.facing());
        assertEquals(SocketType.STANDARD, door.socketType());
        assertEquals(3, door.width());
        assertEquals(4, door.height());
        assertEquals(new IntVector3(5, 6, 7), session.markers().getFirst().position());
        RoomFeatureSlot slot = session.featureSlots().getFirst();
        assertEquals(new IntVector3(8, 1, 8), slot.position());
        assertEquals(new IntVector3(3, 2, 4), slot.size());
        assertEquals(Direction3.SOUTH, slot.facing());
        assertEquals(1, slot.entries().size());
    }

    @Test
    void sizeBasedDoorSlotsPreserveSocketType() {
        AuthoringSession session = new AuthoringSession("room");

        session.addDoorSlot("stairs", new IntVector3(1, 0, 1), new IntVector3(3, 1, 4), Direction3.DOWN, SocketType.STAIRS_DOWN);

        DoorSocket door = session.doors().getFirst();
        assertEquals(SocketType.STAIRS_DOWN, door.socketType());
        assertEquals(3, door.width());
        assertEquals(4, door.height());
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

    @Test
    void loadFeatureForEditRetainsNestedSlots() {
        RoomFeatureSlot nested = new RoomFeatureSlot("detail", new IntVector3(1, 0, 1), new IntVector3(2, 2, 2), Direction3.EAST);
        FeatureTemplate template = new FeatureTemplate("composite", new IntVector3(4, 4, 4), Set.of(), List.of(), List.of(nested), Map.of(), Path.of("feature.nbt"));
        AuthoringSession session = new AuthoringSession("blank");

        session.loadFeatureForEdit(template, fakeWorld(), new IntVector3(10, 80, 10));

        assertTrue(session.featureSession());
        assertTrue(session.editingExistingFeature());
        assertEquals(List.of(nested), session.featureSlots());
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
