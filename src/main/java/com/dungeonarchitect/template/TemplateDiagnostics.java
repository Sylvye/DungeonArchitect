package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;

import java.util.ArrayList;
import java.util.List;

public final class TemplateDiagnostics {
    private TemplateDiagnostics() {
    }

    public static TemplateValidationResult analyze(RoomTemplateRegistry rooms, FeatureTemplateRegistry features, DoorTemplateRegistry doors) {
        TemplateValidationResult result = new TemplateValidationResult();
        if (rooms == null) {
            return result;
        }
        for (TemplateLoadStatus<RoomTemplate> status : rooms.loadStatuses()) {
            status.repairs().forEach(result::addRepair);
            status.errors().forEach(result::add);
        }
        if (features != null) {
            for (var status : features.loadStatuses()) {
                status.repairs().forEach(result::addRepair);
                status.errors().forEach(result::add);
            }
        }
        if (doors != null) {
            for (var status : doors.loadStatuses()) {
                status.repairs().forEach(result::addRepair);
                status.errors().forEach(result::add);
            }
        }
        for (RoomTemplate room : rooms.visible()) {
            analyzeRoom(room, rooms, features, doors, result);
        }
        if (doors != null) {
            for (DoorTemplate door : doors.visible()) {
                analyzeDoor(door, features, result);
            }
        }
        return result;
    }

    public static TemplateValidationResult analyzeRoom(RoomTemplate room, RoomTemplateRegistry rooms, FeatureTemplateRegistry features, DoorTemplateRegistry doors) {
        TemplateValidationResult result = new TemplateValidationResult();
        analyzeRoom(room, rooms, features, doors, result);
        return result;
    }

    public static TemplateValidationResult analyzeDoor(DoorTemplate door, FeatureTemplateRegistry features) {
        TemplateValidationResult result = new TemplateValidationResult();
        analyzeDoor(door, features, result);
        return result;
    }

    public static TemplateValidationResult analyzeFeature(FeatureTemplate feature) {
        return new TemplateValidationResult();
    }

    private static void analyzeRoom(RoomTemplate room, RoomTemplateRegistry rooms, FeatureTemplateRegistry features, DoorTemplateRegistry doors, TemplateValidationResult result) {
        if (room.minimumConnections() > 0) {
            int compatibleSlots = 0;
            for (DoorSocket slot : room.doors()) {
                if (hasCompatibleOppositeCandidate(room, slot, rooms, doors)) {
                    compatibleSlots++;
                }
            }
            if (compatibleSlots < room.minimumConnections()) {
                result.addDiagnostic(warning("room", room.id(), "room", null,
                    "requires " + room.minimumConnections() + " connections, but only " + compatibleSlots + " door slots have compatible room candidates",
                    "Lower Minimum Connections or add compatible opposite-facing door slots.", null));
            }
        }
        for (DoorSocket slot : room.doors()) {
            if (!hasAssignedDoorTemplate(slot)) {
                result.addDiagnostic(warning("room", room.id(), "door", slot.id(),
                    "door slot " + slot.id() + " has no door template assigned, so template-door generation will never use it as a connection",
                    "Open this door slot and select at least one compatible door template.", slot.position()));
            }
            for (DoorSlotEntry entry : slot.entries()) {
                if (entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                    continue;
                }
                if (doors == null || doors.getVisible(entry.doorId()).isEmpty()) {
                    result.addDiagnostic(error("room", room.id(), "door", slot.id(),
                        "door " + slot.id() + " selects missing door template " + entry.doorId(),
                        "Remove this entry or rename it to an existing door template.", slot.position()));
                    continue;
                }
                var door = doors.getVisible(entry.doorId()).orElseThrow();
                var status = doors.status(door.id()).orElse(null);
                if (status != null && !status.valid()) {
                    result.addDiagnostic(error("room", room.id(), "door", slot.id(),
                        "door " + slot.id() + " selects invalid door template " + door.id(),
                        "Fix the door template or remove it from this slot.", slot.position()));
                    continue;
                }
                var match = DoorTemplateMatcher.match(slot, door);
                if (!match.matched()) {
                    result.addDiagnostic(error("room", room.id(), "door", slot.id(),
                        "door " + slot.id() + " cannot use door template " + door.id() + ": " + match.reason(),
                        "Reselect the room slot or door gateway so their faces and footprints line up.", slot.position()));
                }
            }
            if ((slot.facing() == Direction3.UP || slot.facing() == Direction3.DOWN) && !hasOppositeVerticalCandidate(room, slot, rooms, doors)) {
                result.addDiagnostic(warning("room", room.id(), "door", slot.id(),
                    "vertical door " + slot.id() + " has no compatible opposite-facing room slot candidate",
                    "Create a matching " + slot.facing().opposite() + " slot in another room and select compatible door templates.", slot.position()));
            }
            if (slot.connectionRules().mustConnect() && !hasCompatibleOppositeCandidate(room, slot, rooms, doors)) {
                result.addDiagnostic(error("room", room.id(), "door", slot.id(),
                    "required door " + slot.id() + " has no compatible opposite-facing room slot candidate: " + incompatibleOppositeCandidateReason(room, slot, rooms),
                    "Adjust this slot's connection tags or rules, or create a compatible " + slot.facing().opposite() + " slot.", slot.position()));
            }
        }
        for (RoomFeatureSlot slot : room.featureSlots()) {
            analyzeFeatureSlot("room", room.id(), slot, features, result);
        }
    }

    private static void analyzeDoor(DoorTemplate door, FeatureTemplateRegistry features, TemplateValidationResult result) {
        for (RoomFeatureSlot slot : door.featureSlots()) {
            analyzeFeatureSlot("door", door.id(), slot, features, result);
        }
    }

    private static void analyzeFeatureSlot(String templateType, String templateId, RoomFeatureSlot slot, FeatureTemplateRegistry features, TemplateValidationResult result) {
        if (!hasAssignedFeatureTemplate(slot)) {
            result.addDiagnostic(warning(templateType, templateId, "feature", slot.id(),
                "feature slot " + slot.id() + " has no feature assigned, so it can only paste nothing",
                "Open this feature slot and select at least one compatible feature.", slot.position()));
        }
        for (FeatureSlotEntry entry : slot.entries()) {
            if (entry.featureId().equals(FeatureSlotEntry.EMPTY)) {
                continue;
            }
            if (features == null || features.getVisible(entry.featureId()).isEmpty()) {
                result.addDiagnostic(error(templateType, templateId, "feature", slot.id(),
                    "feature slot " + slot.id() + " selects missing feature " + entry.featureId(),
                    "Remove this entry or rename it to an existing feature.", slot.position()));
                continue;
            }
            var feature = features.getVisible(entry.featureId()).orElseThrow();
            var status = features.status(feature.id()).orElse(null);
            if (status != null && !status.valid()) {
                result.addDiagnostic(error(templateType, templateId, "feature", slot.id(),
                    "feature slot " + slot.id() + " selects invalid feature " + feature.id(),
                    "Fix the feature template or remove it from this slot.", slot.position()));
                continue;
            }
            var match = FeatureMatcher.match(slot, feature);
            if (!match.matched()) {
                result.addDiagnostic(error(templateType, templateId, "feature", slot.id(),
                    "feature slot " + slot.id() + " cannot use feature " + feature.id() + ": " + match.reason(),
                    "Resize the slot or select a smaller feature.", slot.position()));
            }
        }
    }

    private static boolean hasOppositeVerticalCandidate(RoomTemplate owner, DoorSocket slot, RoomTemplateRegistry rooms, DoorTemplateRegistry doors) {
        return hasCompatibleOppositeCandidate(owner, slot, rooms, doors);
    }

    private static boolean hasCompatibleOppositeCandidate(RoomTemplate owner, DoorSocket slot, RoomTemplateRegistry rooms, DoorTemplateRegistry doors) {
        if (rooms == null) {
            return false;
        }
        for (RoomTemplate candidateRoom : rooms.all()) {
            for (DoorSocket candidateSlot : candidateRoom.doors()) {
                if (candidateRoom.id().equals(owner.id()) && candidateSlot.id().equals(slot.id())) {
                    continue;
                }
                if (candidateSlot.facing() != slot.facing().opposite()) {
                    continue;
                }
                if (!slot.compatibleWith(candidateSlot, owner.tags(), candidateRoom.tags())) {
                    continue;
                }
                if (compatibleDoorEntries(slot, candidateSlot, doors)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String incompatibleOppositeCandidateReason(RoomTemplate owner, DoorSocket slot, RoomTemplateRegistry rooms) {
        if (rooms == null) {
            return "room templates are unavailable for comparison";
        }
        for (RoomTemplate candidateRoom : rooms.all()) {
            for (DoorSocket candidateSlot : candidateRoom.doors()) {
                if (candidateRoom.id().equals(owner.id()) && candidateSlot.id().equals(slot.id())) {
                    continue;
                }
                if (candidateSlot.facing() != slot.facing().opposite()) {
                    continue;
                }
                DoorSocket.ConnectionMatch match = slot.connectionMatch(candidateSlot, owner.tags(), candidateRoom.tags());
                if (!match.compatible()) {
                    return match.reason();
                }
            }
        }
        return "no opposite-facing room slot exists";
    }

    private static boolean compatibleDoorEntries(DoorSocket first, DoorSocket second, DoorTemplateRegistry doors) {
        if (!first.compatibleWith(second)) {
            return false;
        }
        if (doors == null || doors.all().isEmpty()) {
            return first.size().equals(second.size());
        }
        List<String> firstEntries = selectedDoorIds(first);
        List<String> secondEntries = selectedDoorIds(second);
        if (firstEntries.isEmpty() || secondEntries.isEmpty()) {
            return false;
        }
        for (String firstId : firstEntries) {
            var firstDoor = doors.get(firstId).orElse(null);
            if (firstDoor == null || !DoorTemplateMatcher.matches(first, firstDoor)) {
                continue;
            }
            for (String secondId : secondEntries) {
                var secondDoor = doors.get(secondId).orElse(null);
                if (secondDoor != null && DoorTemplateMatcher.matches(second, secondDoor)
                    && firstDoor.gateway() != null && secondDoor.gateway() != null
                    && firstDoor.gateway().size().equals(secondDoor.gateway().size())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> selectedDoorIds(DoorSocket slot) {
        List<String> ids = new ArrayList<>();
        for (DoorSlotEntry entry : slot.entries()) {
            if (!entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                ids.add(entry.doorId());
            }
        }
        return ids;
    }

    private static boolean hasAssignedDoorTemplate(DoorSocket slot) {
        return slot.entries().stream().anyMatch(entry -> !entry.doorId().equals(DoorSlotEntry.EMPTY));
    }

    private static boolean hasAssignedFeatureTemplate(RoomFeatureSlot slot) {
        return slot.entries().stream().anyMatch(entry -> !entry.featureId().equals(FeatureSlotEntry.EMPTY));
    }

    private static TemplateDiagnostic error(String templateType, String templateId, String componentType, String componentId, String message, String suggestion, com.dungeonarchitect.domain.IntVector3 position) {
        return new TemplateDiagnostic(DiagnosticSeverity.ERROR, templateType, templateId, componentType, componentId, message, suggestion, position);
    }

    private static TemplateDiagnostic warning(String templateType, String templateId, String componentType, String componentId, String message, String suggestion, com.dungeonarchitect.domain.IntVector3 position) {
        return new TemplateDiagnostic(DiagnosticSeverity.WARNING, templateType, templateId, componentType, componentId, message, suggestion, position);
    }
}
