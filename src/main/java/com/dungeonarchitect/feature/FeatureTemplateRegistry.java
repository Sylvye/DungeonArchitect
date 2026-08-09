package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        return Optional.ofNullable(templates.get(id.toLowerCase(java.util.Locale.ROOT)));
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
        Path featureDirectory = templateDirectory(featureId);
        if (!Files.isDirectory(featureDirectory)) {
            throw new IllegalArgumentException("Unknown feature template " + featureId);
        }
        try (var walk = Files.walk(featureDirectory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        reload();
    }

    public FeatureTemplate duplicateFeature(String oldId, String newId) throws IOException {
        String normalizedNewId = normalizeId(newId);
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(normalizedNewId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown feature template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Feature template already exists: " + normalizedNewId);
        }
        copyDirectory(source, target);
        FeatureTemplate copied = FeatureTemplateIO.load(target);
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, copied.size(), copied.tags(), target.resolve("feature.nbt"));
        FeatureTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public FeatureTemplate renameFeature(String oldId, String newId) throws IOException {
        String normalizedNewId = normalizeId(newId);
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(normalizedNewId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown feature template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Feature template already exists: " + normalizedNewId);
        }
        Files.move(source, target);
        FeatureTemplate moved = FeatureTemplateIO.load(target);
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, moved.size(), moved.tags(), target.resolve("feature.nbt"));
        FeatureTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    private Path templateDirectory(String featureId) {
        String normalized = normalizeId(featureId);
        Path root = featuresDirectory.toAbsolutePath().normalize();
        Path directory = root.resolve(normalized).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Invalid feature id " + featureId);
        }
        return directory;
    }

    private String normalizeId(String featureId) {
        if (featureId == null || featureId.isBlank() || featureId.contains("/") || featureId.contains("\\") || featureId.contains("..")) {
            throw new IllegalArgumentException("Invalid feature id " + featureId);
        }
        return featureId.toLowerCase(java.util.Locale.ROOT);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }
}
