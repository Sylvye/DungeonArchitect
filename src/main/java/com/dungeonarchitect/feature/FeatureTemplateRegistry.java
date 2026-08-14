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
import java.util.Set;
import java.util.LinkedHashSet;

public final class FeatureTemplateRegistry {
    private final Path featuresDirectory;
    private final FeatureTemplateValidator validator;
    private final FeatureNestingPolicy nestingPolicy;
    private final FeatureGraphValidator graphValidator;
    private Map<String, FeatureTemplate> templates = Map.of();
    private Map<String, FeatureTemplate> visibleTemplates = Map.of();
    private Map<String, TemplateLoadStatus<FeatureTemplate>> statusById = Map.of();
    private List<TemplateLoadStatus<FeatureTemplate>> loadStatuses = List.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();
    private FeatureGraphValidator.Analysis graphAnalysis = new FeatureGraphValidator.Analysis(Map.of(), Map.of());

    public FeatureTemplateRegistry(Path featuresDirectory, RoomStructureService structureService) {
        this(featuresDirectory, structureService, new FeatureNestingPolicy());
    }

    public FeatureTemplateRegistry(Path featuresDirectory, RoomStructureService structureService, FeatureNestingPolicy nestingPolicy) {
        this.featuresDirectory = featuresDirectory;
        this.validator = new FeatureTemplateValidator(structureService);
        this.nestingPolicy = nestingPolicy;
        this.graphValidator = new FeatureGraphValidator(nestingPolicy);
    }

    FeatureTemplateRegistry(Path featuresDirectory, com.dungeonarchitect.template.StructureSizeReader sizeReader, boolean ignored) {
        this.featuresDirectory = featuresDirectory;
        this.validator = new FeatureTemplateValidator(sizeReader, true);
        this.nestingPolicy = new FeatureNestingPolicy();
        this.graphValidator = new FeatureGraphValidator(nestingPolicy);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, FeatureTemplate> loaded = new LinkedHashMap<>();
        Map<String, FeatureTemplate> visible = new LinkedHashMap<>();
        Map<String, Path> directories = new LinkedHashMap<>();
        Map<String, List<String>> localErrors = new LinkedHashMap<>();
        Map<String, List<String>> repairsById = new LinkedHashMap<>();
        List<TemplateLoadStatus<FeatureTemplate>> unrecoverable = new ArrayList<>();
        try {
            Files.createDirectories(featuresDirectory);
            try (var stream = Files.list(featuresDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    TemplateLoadStatus<FeatureTemplate> loadedStatus = FeatureTemplateIO.loadRecovering(directory, validator.sizeReader());
                    if (!loadedStatus.loadable()) {
                        unrecoverable.add(loadedStatus);
                        result.addAll(loadedStatus.errors());
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    FeatureTemplate template = loadedStatus.template();
                    List<String> errors = new ArrayList<>(loadedStatus.errors());
                    try {
                        com.dungeonarchitect.template.TopLevelIdentity.requireAvailable(featuresDirectory, "feature", template.id(), template.id());
                    } catch (IllegalArgumentException ex) {
                        errors.add(ex.getMessage());
                    }
                    if (visible.containsKey(template.id())) {
                        errors.add("Duplicate feature id " + template.id());
                        unrecoverable.add(new TemplateLoadStatus<>(template, template.id(), directory, false, errors, loadedStatus.repairs()));
                        result.addAll(errors);
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    TemplateValidationResult templateResult = validator.validate(template);
                    errors.addAll(templateResult.errors());
                    visible.put(template.id(), template);
                    directories.put(template.id(), directory);
                    localErrors.put(template.id(), List.copyOf(errors));
                    repairsById.put(template.id(), loadedStatus.repairs());
                }
            }
        } catch (IOException ex) {
            result.add("Failed to scan features directory: " + ex.getMessage());
        }
        graphAnalysis = graphValidator.analyze(visible, localErrors);
        Map<String, TemplateLoadStatus<FeatureTemplate>> statusesById = new LinkedHashMap<>();
        List<TemplateLoadStatus<FeatureTemplate>> statuses = new ArrayList<>(unrecoverable);
        for (FeatureTemplate template : visible.values()) {
            List<String> errors = graphAnalysis.errors(template.id());
            List<String> repairs = repairsById.getOrDefault(template.id(), List.of());
            TemplateLoadStatus<FeatureTemplate> status = new TemplateLoadStatus<>(template, template.id(), directories.get(template.id()), errors.isEmpty(), errors, repairs);
            statuses.add(status);
            statusesById.put(template.id(), status);
            result.addAll(errors);
            result.addRepairs(repairs);
            if (errors.isEmpty()) loaded.put(template.id(), template);
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

    public FeatureNestingPolicy nestingPolicy() {
        return nestingPolicy;
    }

    public Optional<FeatureGraphValidator.Metrics> metrics(String featureId) {
        return Optional.ofNullable(graphAnalysis.metrics(featureId));
    }

    /** Validates an in-memory replacement against the complete prospective graph. */
    public TemplateValidationResult validateProspective(FeatureTemplate candidate) {
        Map<String, FeatureTemplate> prospective = new LinkedHashMap<>(visibleTemplates);
        prospective.put(candidate.id(), candidate);
        Map<String, List<String>> local = new LinkedHashMap<>();
        for (FeatureTemplate feature : prospective.values()) {
            local.put(feature.id(), validator.validate(feature).errors());
        }
        FeatureGraphValidator.Analysis analysis = graphValidator.analyze(prospective, local);
        TemplateValidationResult result = new TemplateValidationResult();
        for (Map.Entry<String, List<String>> entry : analysis.errorsByFeature().entrySet()) {
            Set<String> prior = new LinkedHashSet<>(graphAnalysis.errors(entry.getKey()));
            entry.getValue().stream().filter(error -> !prior.contains(error)).forEach(result::add);
        }
        return result;
    }

    public List<String> featureOwnersReferencing(String featureId) {
        String target = featureId.toLowerCase(java.util.Locale.ROOT);
        return visibleTemplates.values().stream()
            .filter(owner -> !owner.id().equalsIgnoreCase(target))
            .filter(owner -> owner.featureSlots().stream().flatMap(slot -> slot.entries().stream()).anyMatch(entry -> entry.featureId().equalsIgnoreCase(target)))
            .map(owner -> "feature " + owner.id())
            .sorted()
            .toList();
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
        com.dungeonarchitect.template.TopLevelIdentity.requireAvailable(featuresDirectory, "feature", normalizedNewId, null);
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
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), sourceTemplate.markers(), sourceTemplate.featureSlots(), sourceTemplate.lootBindings(), target.resolve("feature.nbt"));
        FeatureTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public FeatureTemplate renameFeature(String oldId, String newId) throws IOException {
        String normalizedNewId = normalizeId(newId);
        com.dungeonarchitect.template.TopLevelIdentity.requireAvailable(featuresDirectory, "feature", normalizedNewId, oldId);
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
        FeatureTemplate renamed = new FeatureTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), sourceTemplate.markers(), sourceTemplate.featureSlots(), sourceTemplate.lootBindings(), target.resolve("feature.nbt"));
        FeatureTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public void replaceFeatureReferences(String oldFeatureId, String newFeatureId) throws IOException {
        for (FeatureTemplate template : new ArrayList<>(visibleTemplates.values())) {
            boolean changed = false;
            List<com.dungeonarchitect.domain.RoomFeatureSlot> slots = new ArrayList<>();
            for (com.dungeonarchitect.domain.RoomFeatureSlot slot : template.featureSlots()) {
                List<com.dungeonarchitect.domain.FeatureSlotEntry> entries = new ArrayList<>();
                for (com.dungeonarchitect.domain.FeatureSlotEntry entry : slot.entries()) {
                    if (entry.featureId().equalsIgnoreCase(oldFeatureId)) {
                        entries.add(new com.dungeonarchitect.domain.FeatureSlotEntry(newFeatureId, entry.weight()));
                        changed = true;
                    } else entries.add(entry);
                }
                slots.add(slot.withEntries(entries));
            }
            if (changed) {
                FeatureTemplate updated = new FeatureTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.lootBindings(), template.structureFile());
                FeatureTemplateIO.save(updated, template.structureFile().getParent());
            }
        }
        reload();
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
