package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.DoorConnectionRules;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.TagDomain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Produces the persisted-template updates needed for a global tag deletion. */
public final class TagCleanupService {
    private TagCleanupService() {
    }

    public static Result remove(TagDomain domain, String tag, Collection<RoomTemplate> rooms, Collection<DoorTemplate> doors) {
        int removedFields = 0;
        List<RoomTemplate> updatedRooms = new ArrayList<>();
        for (RoomTemplate room : rooms) {
            Set<String> roomTags = room.tags();
            if (domain == TagDomain.ROOM) {
                Set<String> updated = without(roomTags, tag);
                if (!updated.equals(roomTags)) {
                    roomTags = updated;
                    removedFields++;
                }
            }
            List<DoorSocket> slots = new ArrayList<>();
            boolean slotsChanged = false;
            for (DoorSocket slot : room.doors()) {
                DoorSocket updated = remove(domain, tag, slot);
                slots.add(updated);
                if (!updated.equals(slot)) {
                    slotsChanged = true;
                    removedFields += changedFields(domain, tag, slot);
                }
            }
            if (!roomTags.equals(room.tags()) || slotsChanged) {
                updatedRooms.add(new RoomTemplate(room.id(), room.category(), room.weight(), room.minimumConnections(), roomTags, room.size(), room.spawn(), slots, room.markers(), room.featureSlots(), room.structureFile()));
            }
        }
        List<DoorTemplate> updatedDoors = new ArrayList<>();
        if (domain == TagDomain.DOOR) {
            for (DoorTemplate door : doors) {
                Set<String> updatedTags = without(door.tags(), tag);
                if (!updatedTags.equals(door.tags())) {
                    updatedDoors.add(new DoorTemplate(door.id(), door.size(), updatedTags, door.markers(), door.featureSlots(), door.gateway(), door.structureFile()));
                    removedFields++;
                }
            }
        }
        return new Result(updatedRooms, updatedDoors, removedFields);
    }

    public static DoorSocket remove(TagDomain domain, String tag, DoorSocket slot) {
        Set<String> tags = slot.tags();
        DoorConnectionRules rules = slot.connectionRules();
        if (domain == TagDomain.DOOR) {
            tags = without(tags, tag);
            rules = new DoorConnectionRules(without(rules.allowedTags(), tag), without(rules.deniedTags(), tag), rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect());
        } else {
            rules = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), without(rules.allowedRoomTags(), tag), without(rules.deniedRoomTags(), tag), rules.mustConnect());
        }
        return new DoorSocket(slot.id(), slot.position(), slot.facing(), slot.socketType(), slot.width(), slot.height(), slot.size(), tags, slot.entries(), rules);
    }

    public static Set<String> without(Set<String> tags, String value) {
        String key = value.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!tag.toLowerCase(Locale.ROOT).equals(key)) {
                result.add(tag);
            }
        }
        return Set.copyOf(result);
    }

    private static int changedFields(TagDomain domain, String tag, DoorSocket slot) {
        int count = 0;
        DoorConnectionRules rules = slot.connectionRules();
        if (domain == TagDomain.DOOR) {
            count += !without(slot.tags(), tag).equals(slot.tags()) ? 1 : 0;
            count += !without(rules.allowedTags(), tag).equals(rules.allowedTags()) ? 1 : 0;
            count += !without(rules.deniedTags(), tag).equals(rules.deniedTags()) ? 1 : 0;
        } else {
            count += !without(rules.allowedRoomTags(), tag).equals(rules.allowedRoomTags()) ? 1 : 0;
            count += !without(rules.deniedRoomTags(), tag).equals(rules.deniedRoomTags()) ? 1 : 0;
        }
        return count;
    }

    public record Result(List<RoomTemplate> rooms, List<DoorTemplate> doors, int affectedFields) {
        public Result {
            rooms = List.copyOf(rooms);
            doors = List.copyOf(doors);
        }
    }
}
