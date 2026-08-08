package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public final class RoomTemplateValidator {
    private final RoomStructureService structureService;

    public RoomTemplateValidator() {
        this(null);
    }

    public RoomTemplateValidator(RoomStructureService structureService) {
        this.structureService = structureService;
    }

    public TemplateValidationResult validate(RoomTemplate template) {
        TemplateValidationResult result = new TemplateValidationResult();
        if (!Files.isRegularFile(template.structureFile())) {
            result.add(template.id() + ": missing room.nbt");
        } else if (structureService != null) {
            try {
                var nbtSize = structureService.loadSize(template.structureFile());
                if (!nbtSize.equals(template.size())) {
                    result.add(template.id() + ": room.nbt size " + nbtSize + " does not match room.yml size " + template.size() + ". Re-save this room from the original build area.");
                }
            } catch (Exception ex) {
                result.add(template.id() + ": failed to load room.nbt for size validation: " + ex.getMessage());
            }
        }
        try {
            BoundingBox3i bounds = BoundingBox3i.fromMinAndSize(com.dungeonarchitect.domain.IntVector3.ZERO, template.size());
            validateDoors(template, bounds, result);
            validateMarkers(template, bounds, result);
            validateFeatureSlots(template, bounds, result);
        } catch (IllegalArgumentException ex) {
            result.add(template.id() + ": invalid size: " + ex.getMessage());
        }
        return result;
    }

    private void validateDoors(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (DoorSocket door : template.doors()) {
            if (!ids.add(door.id())) {
                result.add(template.id() + ": duplicate door id " + door.id());
            }
            if (!bounds.contains(door.position())) {
                result.add(template.id() + ": door " + door.id() + " is outside room bounds");
            }
        }
    }

    private void validateMarkers(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        for (RoomMarker marker : template.markers()) {
            if (!bounds.contains(marker.position())) {
                result.add(template.id() + ": marker " + marker.name() + " is outside room bounds");
            }
        }
    }

    private void validateFeatureSlots(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (RoomFeatureSlot slot : template.featureSlots()) {
            if (!ids.add(slot.id())) {
                result.add(template.id() + ": duplicate feature slot id " + slot.id());
            }
            if (!bounds.contains(slot.position())) {
                result.add(template.id() + ": feature slot " + slot.id() + " is outside room bounds");
            }
        }
    }
}
