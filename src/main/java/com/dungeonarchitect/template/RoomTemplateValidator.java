package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.authoring.SelectionBounds;
import com.dungeonarchitect.door.BoundaryFacing;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public final class RoomTemplateValidator {
    private final RoomStructureService structureService;
    private final FeatureTemplateRegistry featureRegistry;
    private final DoorTemplateRegistry doorRegistry;

    public RoomTemplateValidator() {
        this(null, null, null);
    }

    public RoomTemplateValidator(RoomStructureService structureService) {
        this(structureService, null, null);
    }

    public RoomTemplateValidator(RoomStructureService structureService, FeatureTemplateRegistry featureRegistry) {
        this(structureService, featureRegistry, null);
    }

    public RoomTemplateValidator(RoomStructureService structureService, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry) {
        this.structureService = structureService;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
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
                result.add(template.id() + ": door " + door.id() + " is outside room bounds", door.position());
            }
            IntVector3 max = door.position().add(door.size()).subtract(new IntVector3(1, 1, 1));
            if (!bounds.contains(max)) {
                result.add(template.id() + ": door " + door.id() + " extends outside room bounds", door.position());
            } else {
                try {
                    var inferred = BoundaryFacing.infer(SelectionBounds.between(door.position(), max), SelectionBounds.between(bounds.min(), bounds.max()), "Door " + door.id());
                    if (door.facing() != inferred) {
                        result.add(template.id() + ": door " + door.id() + " facing " + door.facing() + " does not match inferred bounds face " + inferred, door.position());
                    }
                } catch (IllegalArgumentException ex) {
                    result.add(template.id() + ": " + ex.getMessage(), door.position());
                }
            }
            for (DoorSlotEntry entry : door.entries()) {
                if (entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                    continue;
                }
                if (doorRegistry == null) {
                    continue;
                }
                var doorTemplate = doorRegistry.get(entry.doorId());
                if (doorTemplate.isEmpty()) {
                    result.add(template.id() + ": door " + door.id() + " references unknown door template " + entry.doorId(), door.position());
                } else {
                    DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(door, doorTemplate.get());
                    if (!match.matched()) {
                        result.add(template.id() + ": door " + door.id() + " does not match door template " + entry.doorId() + ": " + match.reason(), door.position());
                    }
                }
            }
        }
    }

    private void validateMarkers(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        for (RoomMarker marker : template.markers()) {
            if (!bounds.contains(marker.position())) {
                result.add(template.id() + ": marker " + marker.name() + " is outside room bounds", marker.position());
            }
        }
    }

    private void validateFeatureSlots(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        Set<String> ids = new HashSet<>();
        for (RoomFeatureSlot slot : template.featureSlots()) {
            if (!ids.add(slot.id())) {
                result.add(template.id() + ": duplicate feature slot id " + slot.id());
            }
            boolean minInside = bounds.contains(slot.position());
            if (!minInside) {
                result.add(template.id() + ": feature slot " + slot.id() + " is outside room bounds", slot.position());
            }
            if (minInside && !bounds.contains(slot.position().add(slot.size()).subtract(new com.dungeonarchitect.domain.IntVector3(1, 1, 1)))) {
                result.add(template.id() + ": feature slot " + slot.id() + " extends outside room bounds", slot.position());
            }
            for (FeatureSlotEntry entry : slot.entries()) {
                if (entry.featureId().equals(FeatureSlotEntry.EMPTY)) {
                    continue;
                }
                if (featureRegistry == null) {
                    continue;
                }
                var feature = featureRegistry.get(entry.featureId());
                if (feature.isEmpty()) {
                    result.add(template.id() + ": feature slot " + slot.id() + " references unknown feature " + entry.featureId(), slot.position());
                } else {
                    FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(slot, feature.get());
                    if (!match.matched()) {
                        result.add(template.id() + ": feature slot " + slot.id() + " does not match feature " + entry.featureId() + ": " + match.reason(), slot.position());
                    }
                }
            }
        }
    }
}
