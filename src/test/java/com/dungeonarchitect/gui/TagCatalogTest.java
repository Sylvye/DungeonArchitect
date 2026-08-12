package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorConnectionRules;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.TagDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TagCatalogTest {
    @TempDir
    Path directory;

    @Test
    void preservesFirstSpellingAndSortsTagsCaseInsensitively() {
        TagCatalog catalog = new TagCatalog(directory.resolve("tags.yml"));

        catalog.add(TagDomain.ROOM, "zebra");
        catalog.add(TagDomain.ROOM, "Alpha");
        catalog.add(TagDomain.ROOM, "ALPHA");

        assertEquals(List.of("Alpha", "zebra"), catalog.tags(TagDomain.ROOM, ""));
        assertEquals(List.of("Alpha"), catalog.tags(TagDomain.ROOM, "lp"));
        assertEquals(List.of("Alpha", "zebra"), new TagCatalog(directory.resolve("tags.yml")).tags(TagDomain.ROOM, ""));
    }

    @Test
    void seedsRoomAndDoorCatalogsFromTheirOwnFields() {
        TagCatalog catalog = new TagCatalog(directory.resolve("tags.yml"));
        DoorSocket slot = new DoorSocket("slot", new IntVector3(0, 0, 0), new IntVector3(1, 2, 1), Direction3.NORTH,
            Set.of("slot-door"), List.of()).withConnectionRules(new DoorConnectionRules(Set.of("allow-door"), Set.of("deny-door"), Set.of("allow-room"), Set.of("deny-room"), false));
        RoomTemplate room = new RoomTemplate("room", RoomCategory.GENERIC, 1, Set.of("room-tag"), new IntVector3(3, 3, 3), null, List.of(slot), List.of(), List.of(), Path.of("room.nbt"));
        DoorTemplate door = new DoorTemplate("door", new IntVector3(1, 2, 1), Set.of("template-door"), List.of(), List.of(), null, Path.of("door.nbt"));

        catalog.synchronize(List.of(room), List.of(door));

        assertEquals(List.of("allow-room", "deny-room", "room-tag"), catalog.tags(TagDomain.ROOM, ""));
        assertEquals(List.of("allow-door", "deny-door", "slot-door", "template-door"), catalog.tags(TagDomain.DOOR, ""));
    }
}
