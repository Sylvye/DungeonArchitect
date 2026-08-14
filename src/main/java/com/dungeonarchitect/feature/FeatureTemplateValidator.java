package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.DiagnosticText;
import com.dungeonarchitect.template.StructureSizeReader;
import com.dungeonarchitect.template.TemplateValidationResult;

import java.nio.file.Files;

public final class FeatureTemplateValidator {
    private final StructureSizeReader structureService;

    public FeatureTemplateValidator(RoomStructureService structureService) {
        this.structureService = structureService;
    }

    FeatureTemplateValidator(StructureSizeReader structureService, boolean ignored) {
        this.structureService = structureService;
    }

    public StructureSizeReader sizeReader() {
        return structureService;
    }

    public TemplateValidationResult validate(FeatureTemplate template) {
        TemplateValidationResult result = new TemplateValidationResult();
        com.dungeonarchitect.template.IdentityRules.validateFeatureComponents(template.id(), template.markers(), template.featureSlots(), result);
        if (!Files.isRegularFile(template.structureFile())) {
            result.add(template.id() + ": missing feature.nbt");
            return result;
        }
        if (structureService != null) {
            try {
                var nbtSize = structureService.loadSize(template.structureFile());
                if (!nbtSize.equals(template.size())) {
                    result.add(template.id() + ": feature.nbt is " + DiagnosticText.size(nbtSize) + ", but feature.yml says " + DiagnosticText.size(template.size()) + ". Re-save this feature.");
                }
            } catch (Exception ex) {
                result.add(template.id() + ": failed to load feature.nbt for size validation: " + ex.getMessage());
            }
        }
        try {
            BoundingBox3i bounds = BoundingBox3i.fromMinAndSize(IntVector3.ZERO, template.size());
            template.markers().stream()
                .filter(marker -> !bounds.contains(marker.position()))
                .forEach(marker -> result.add(template.id() + ": marker " + marker.name() + " is outside feature bounds at " + DiagnosticText.position(marker.position()), marker.position()));
            for (RoomFeatureSlot slot : template.featureSlots()) {
                IntVector3 max = slot.position().add(slot.size()).subtract(new IntVector3(1, 1, 1));
                if (!bounds.contains(slot.position()) || !bounds.contains(max)) {
                    result.add(template.id() + ": feature slot " + slot.id() + " is outside feature bounds at " + DiagnosticText.position(slot.position()), slot.position());
                }
            }
        } catch (IllegalArgumentException ex) {
            result.add(template.id() + ": invalid feature bounds: " + ex.getMessage());
        }
        return result;
    }
}
