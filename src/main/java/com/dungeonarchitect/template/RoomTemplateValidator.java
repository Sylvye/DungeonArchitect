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
    private final StructureSizeReader structureService;
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

    RoomTemplateValidator(StructureSizeReader structureService, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry) {
        this.structureService = structureService;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
    }

    public StructureSizeReader sizeReader() {
        return structureService;
    }

    public TemplateValidationResult validate(RoomTemplate template) {
        TemplateValidationResult result = new TemplateValidationResult();
        if (template.minimumConnections() > template.doors().size()) {
            result.add(template.id() + ": minimum connections " + template.minimumConnections() + " exceeds its " + template.doors().size() + " door slots");
        }
        if (!Files.isRegularFile(template.structureFile())) {
            result.add(template.id() + ": missing room.nbt");
        } else if (structureService != null) {
            try {
                var nbtSize = structureService.loadSize(template.structureFile());
                if (!nbtSize.equals(template.size())) {
                    result.add(template.id() + ": room.nbt is " + DiagnosticText.size(nbtSize) + ", but room.yml says " + DiagnosticText.size(template.size()) + ". Re-save this room from the original build area.");
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
            result.add(template.id() + ": invalid size: " + readable(ex.getMessage()));
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
                result.add(template.id() + ": door " + door.id() + " starts outside room bounds at " + DiagnosticText.position(door.position()), door.position());
            }
            IntVector3 max = door.position().add(door.size()).subtract(new IntVector3(1, 1, 1));
            if (!bounds.contains(max)) {
                result.add(template.id() + ": door " + door.id() + " extends outside room bounds from " + DiagnosticText.position(door.position()) + " size " + DiagnosticText.size(door.size()), door.position());
            } else {
                try {
                    var inferred = BoundaryFacing.infer(SelectionBounds.between(door.position(), max), SelectionBounds.between(bounds.min(), bounds.max()), "Door " + door.id());
                    if (door.facing() != inferred) {
                        result.add(template.id() + ": door " + door.id() + " faces " + door.facing() + ", but its bounds touch the " + inferred + " room face", door.position());
                    }
                } catch (IllegalArgumentException ex) {
                    result.add(template.id() + ": " + readable(ex.getMessage()), door.position());
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
                    result.add(template.id() + ": door " + door.id() + " references missing or invalid door template " + entry.doorId(), door.position());
                } else {
                    DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(door, doorTemplate.get());
                    if (!match.matched()) {
                        result.add(template.id() + ": door " + door.id() + " cannot use door template " + entry.doorId() + ": " + match.reason(), door.position());
                    }
                }
            }
        }
    }

    private void validateMarkers(RoomTemplate template, BoundingBox3i bounds, TemplateValidationResult result) {
        for (RoomMarker marker : template.markers()) {
            if (!bounds.contains(marker.position())) {
                result.add(template.id() + ": marker " + marker.name() + " is outside room bounds at " + DiagnosticText.position(marker.position()), marker.position());
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
                result.add(template.id() + ": feature slot " + slot.id() + " starts outside room bounds at " + DiagnosticText.position(slot.position()), slot.position());
            }
            if (minInside && !bounds.contains(slot.position().add(slot.size()).subtract(new com.dungeonarchitect.domain.IntVector3(1, 1, 1)))) {
                result.add(template.id() + ": feature slot " + slot.id() + " extends outside room bounds from " + DiagnosticText.position(slot.position()) + " size " + DiagnosticText.size(slot.size()), slot.position());
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
                    result.add(template.id() + ": feature slot " + slot.id() + " references missing or invalid feature " + entry.featureId(), slot.position());
                } else {
                    FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(slot, feature.get());
                    if (!match.matched()) {
                        result.add(template.id() + ": feature slot " + slot.id() + " cannot use feature " + entry.featureId() + ": " + match.reason(), slot.position());
                    }
                }
            }
        }
    }

    private String readable(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message
            .replace("IntVector3[x=", "(")
            .replace(", y=", ", ")
            .replace(", z=", ", ")
            .replace("]", ")");
    }
}
