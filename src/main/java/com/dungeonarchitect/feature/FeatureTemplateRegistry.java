package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.template.TemplateLoadStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FeatureTemplateRegistry {
    private final Path featuresDirectory;
    private final FeatureTemplateValidator validator;
    private Map<String, FeatureTemplate> templates = Map.of();
    private Map<String, FeatureTemplate> visibleTemplates = Map.of();
    private Map<String, TemplateLoadStatus<FeatureTemplate>> statusById = Map.of();
    private List<TemplateLoadStatus<FeatureTemplate>> loadStatuses = List.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public FeatureTemplateRegistry(Path featuresDirectory, RoomStructureService structureService) {
        this.featuresDirectory = featuresDirectory;
        this.validator = new FeatureTemplateValidator(structureService);
    }

    FeatureTemplateRegistry(Path featuresDirectory, com.dungeonarchitect.template.StructureSizeReader sizeReader, boolean ignored) {
        this.featuresDirectory = featuresDirectory;
        this.validator = new FeatureTemplateValidator(sizeReader, true);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, FeatureTemplate> loaded = new LinkedHashMap<>();
        Map<String, FeatureTemplate> visible = new LinkedHashMap<>();
        Map<String, TemplateLoadStatus<FeatureTemplate>> statusesById = new LinkedHashMap<>();
        List<TemplateLoadStatus<FeatureTemplate>> statuses = new ArrayList<>();
        try {
            Files.createDirectories(featuresDirectory);
            try (var stream = Files.list(featuresDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    TemplateLoadStatus<FeatureTemplate> loadedStatus = FeatureTemplateIO.loadRecovering(directory, validator.sizeReader());
                    if (!loadedStatus.loadable()) {
                        statuses.add(loadedStatus);
                        result.addAll(loadedStatus.errors());
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    FeatureTemplate template = loadedStatus.template();
                    List<String> errors = new ArrayList<>(loadedStatus.errors());
                    if (visible.containsKey(template.id())) {
                        errors.add("Duplicate feature id " + template.id());
                        TemplateLoadStatus<FeatureTemplate> duplicate = new TemplateLoadStatus<>(template, template.id(), directory, false, errors, loadedStatus.repairs());
                        statuses.add(duplicate);
                        result.addAll(errors);
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    TemplateValidationResult templateResult = validator.validate(template);
                    errors.addAll(templateResult.errors());
                    result.addAll(errors);
                    result.addRepairs(loadedStatus.repairs());
                    boolean valid = errors.isEmpty();
                    TemplateLoadStatus<FeatureTemplate> status = new TemplateLoadStatus<>(template, template.id(), directory, valid, errors, loadedStatus.repairs());
                    statuses.add(status);
                    visible.put(template.id(), template);
                    statusesById.put(template.id(), status);
                    if (valid) {
                        loaded.put(template.id(), template);
                    }
                }
            }
        } catch (IOException ex) {
            result.add("Failed to scan features directory: " + ex.getMessage());
        }
        templates = Map.copyOf(loaded);
        visibleTemplates = Map.copyOf(visible);
        statusById = Map.copyOf(statusesById);
        loadStatuses = List.copyOf(statuses);
        lastValidation = result;
        return result;
    }

    public Optional<FeatureTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<FeatureTemplate> all() {
        return templates.values();
    }

    public Optional<FeatureTemplate> getVisible(String id) {
        return Optional.ofNullable(visibleTemplates.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<FeatureTemplate> visible() {
        return visibleTemplates.values();
    }

    public Optional<TemplateLoadStatus<FeatureTemplate>> status(String id) {
        return Optional.ofNullable(statusById.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public List<TemplateLoadStatus<FeatureTemplate>> loadStatuses() {
        return loadStatuses;
    }

    public long invalidCount() {
        return loadStatuses.stream().filter(status -> status.loadable() && !status.valid()).count();
    }

    public long unrecoverableCount() {
        return loadStatuses.stream().filter(status -> !status.loadable()).count();
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
        FeatureTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "duplication");
        copyDirectory(source, target);
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), target.resolve("feature.nbt"));
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
        FeatureTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "rename");
        Files.move(source, target);
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), target.resolve("feature.nbt"));
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

    private FeatureTemplate loadVisibleTemplateForOperation(String featureId, Path directory, String operation) {
        FeatureTemplate visible = getVisible(featureId).orElse(null);
        if (visible != null) {
            return visible;
        }
        TemplateLoadStatus<FeatureTemplate> status = FeatureTemplateIO.loadRecovering(directory, validator.sizeReader());
        if (!status.loadable()) {
            throw new IllegalArgumentException("Feature template cannot be loaded for " + operation + ": " + featureId);
        }
        return status.template();
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
