package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FeatureTemplateRegistry {
    private final Path featuresDirectory;
    private final FeatureTemplateValidator validator;
    private Map<String, FeatureTemplate> templates = Map.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public FeatureTemplateRegistry(Path featuresDirectory, RoomStructureService structureService) {
        this.featuresDirectory = featuresDirectory;
        this.validator = new FeatureTemplateValidator(structureService);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, FeatureTemplate> loaded = new LinkedHashMap<>();
        try {
            Files.createDirectories(featuresDirectory);
            try (var stream = Files.list(featuresDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    try {
                        FeatureTemplate template = FeatureTemplateIO.load(directory);
                        if (loaded.containsKey(template.id())) {
                            result.add("Duplicate feature id " + template.id());
                            continue;
                        }
                        TemplateValidationResult templateResult = validator.validate(template);
                        result.addAll(templateResult.errors());
                        if (templateResult.valid()) {
                            loaded.put(template.id(), template);
                        }
                    } catch (RuntimeException ex) {
                        result.add(directory.getFileName() + ": " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            result.add("Failed to scan features directory: " + ex.getMessage());
        }
        templates = Map.copyOf(loaded);
        lastValidation = result;
        return result;
    }

    public Optional<FeatureTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<FeatureTemplate> all() {
        return templates.values();
    }

    public Path featuresDirectory() {
        return featuresDirectory;
    }

    public TemplateValidationResult lastValidation() {
        return lastValidation;
    }

    public void deleteFeature(String featureId) throws IOException {
        Path featureDirectory = featuresDirectory.resolve(featureId).toAbsolutePath().normalize();
        Path root = featuresDirectory.toAbsolutePath().normalize();
        if (!featureDirectory.startsWith(root) || !Files.isDirectory(featureDirectory)) {
            throw new IllegalArgumentException("Unknown feature template " + featureId);
        }
        try (var walk = Files.walk(featureDirectory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        reload();
    }
}
