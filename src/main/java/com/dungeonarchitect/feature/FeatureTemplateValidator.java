package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;

import java.nio.file.Files;

public final class FeatureTemplateValidator {
    private final RoomStructureService structureService;

    public FeatureTemplateValidator(RoomStructureService structureService) {
        this.structureService = structureService;
    }

    public TemplateValidationResult validate(FeatureTemplate template) {
        TemplateValidationResult result = new TemplateValidationResult();
        if (!Files.isRegularFile(template.structureFile())) {
            result.add(template.id() + ": missing feature.nbt");
            return result;
        }
        if (structureService != null) {
            try {
                var nbtSize = structureService.loadSize(template.structureFile());
                if (!nbtSize.equals(template.size())) {
                    result.add(template.id() + ": feature.nbt size " + nbtSize + " does not match feature.yml size " + template.size() + ". Re-save this feature.");
                }
            } catch (Exception ex) {
                result.add(template.id() + ": failed to load feature.nbt for size validation: " + ex.getMessage());
            }
        }
        return result;
    }
}
