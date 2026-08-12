package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorConnectionRules;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.TagDomain;
import com.dungeonarchitect.authoring.AuthoringSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TagCleanupServiceTest {
    @Test
    void roomDeletionDoesNotTouchDoorFields() {
        RoomTemplate room = room();

        TagCleanupService.Result result = TagCleanupService.remove(TagDomain.ROOM, "room-tag", List.of(room), List.of(door()));
        RoomTemplate updated = result.rooms().getFirst();
        DoorSocket slot = updated.doors().getFirst();

        assertEquals(Set.of(), updated.tags());
        assertEquals(Set.of(), slot.connectionRules().allowedRoomTags());
        assertEquals(Set.of("door-tag"), slot.tags());
        assertEquals(Set.of("door-tag"), slot.connectionRules().allowedTags());
        assertEquals(0, result.doors().size());
    }

    @Test
    void doorDeletionDoesNotTouchRoomFieldsAndUpdatesDoorTemplates() {
        RoomTemplate room = room();

        TagCleanupService.Result result = TagCleanupService.remove(TagDomain.DOOR, "door-tag", List.of(room), List.of(door()));
        RoomTemplate updated = result.rooms().getFirst();
        DoorSocket slot = updated.doors().getFirst();

        assertEquals(Set.of("room-tag"), updated.tags());
        assertEquals(Set.of("room-tag"), slot.connectionRules().allowedRoomTags());
        assertEquals(Set.of(), slot.tags());
        assertEquals(Set.of(), slot.connectionRules().allowedTags());
        assertEquals(Set.of(), result.doors().getFirst().tags());
    }

    @Test
    void activeRoomSessionDropsOnlyTheDeletedDomain() {
        AuthoringSession session = new AuthoringSession("room");
        session.tags(Set.of("room-tag"));
        session.addDoorSlot(room().doors().getFirst());

        session.removeTag(TagDomain.DOOR, "door-tag");

        assertEquals(Set.of("room-tag"), session.tags());
        assertEquals(Set.of(), session.doors().getFirst().tags());
        assertEquals(Set.of(), session.doors().getFirst().connectionRules().allowedTags());
        assertEquals(Set.of("room-tag"), session.doors().getFirst().connectionRules().allowedRoomTags());
    }

    private static RoomTemplate room() {
        DoorSocket slot = new DoorSocket("slot", new IntVector3(0, 0, 0), new IntVector3(1, 2, 1), Direction3.NORTH,
            Set.of("door-tag"), List.of()).withConnectionRules(new DoorConnectionRules(Set.of("door-tag"), Set.of(), Set.of("room-tag"), Set.of(), false));
        return new RoomTemplate("room", RoomCategory.GENERIC, 1, Set.of("room-tag"), new IntVector3(3, 3, 3), null, List.of(slot), List.of(), List.of(), Path.of("room.nbt"));
    }

    private static DoorTemplate door() {
        return new DoorTemplate("door", new IntVector3(1, 2, 1), Set.of("door-tag"), List.of(), List.of(), null, Path.of("door.nbt"));
    }
}
