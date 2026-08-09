package com.dungeonarchitect.door;

import com.dungeonarchitect.authoring.SelectionBounds;
import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public final class DoorTemplateValidator {
    private final RoomStructureService structureService;

    public DoorTemplateValidator(RoomStructureService structureService) {
        this.structureService = structureService;
    }

    public TemplateValidationResult validate(DoorTemplate template) {
        TemplateValidationResult result = new TemplateValidationResult();
        if (!Files.isRegularFile(template.structureFile())) {
            result.add(template.id() + ": missing door.nbt");
        } else if (structureService != null) {
            try {
                IntVector3 nbtSize = structureService.loadSize(template.structureFile());
                if (!nbtSize.equals(template.size())) {
                    result.add(template.id() + ": door.nbt size " + nbtSize + " does not match door.yml size " + template.size() + ". Re-save this door.");
                }
            } catch (Exception ex) {
                result.add(template.id() + ": failed to load door.nbt for size validation: " + ex.getMessage());
            }
        }
        try {
            BoundingBox3i bounds = BoundingBox3i.fromMinAndSize(IntVector3.ZERO, template.size());
            if (template.gateway() == null) {
                result.add(template.id() + ": missing gateway");
            } else {
                IntVector3 gatewayMax = template.gateway().position().add(template.gateway().size()).subtract(new IntVector3(1, 1, 1));
                SelectionBounds gatewayBounds = SelectionBounds.between(template.gateway().position(), gatewayMax);
                SelectionBounds templateBounds = SelectionBounds.between(bounds.min(), bounds.max());
                if (!bounds.contains(template.gateway().position()) || !bounds.contains(gatewayMax)) {
                    result.add(template.id() + ": gateway is outside door bounds", template.gateway().position());
                } else {
                    try {
                        BoundaryFacing.infer(gatewayBounds, templateBounds, "Gateway");
                    } catch (IllegalArgumentException ex) {
                        result.add(template.id() + ": " + ex.getMessage(), template.gateway().position());
                    }
                }
            }
            for (RoomMarker marker : template.markers()) {
                if (!bounds.contains(marker.position())) {
                    result.add(template.id() + ": marker " + marker.name() + " is outside door bounds", marker.position());
                }
            }
            Set<String> slotIds = new HashSet<>();
            for (RoomFeatureSlot slot : template.featureSlots()) {
                if (!slotIds.add(slot.id())) {
                    result.add(template.id() + ": duplicate feature slot id " + slot.id());
                }
                IntVector3 max = slot.position().add(slot.size()).subtract(new IntVector3(1, 1, 1));
                if (!bounds.contains(slot.position()) || !bounds.contains(max)) {
                    result.add(template.id() + ": feature slot " + slot.id() + " is outside door bounds", slot.position());
                }
            }
        } catch (IllegalArgumentException ex) {
            result.add(template.id() + ": invalid size: " + ex.getMessage());
        }
        return result;
    }
}
