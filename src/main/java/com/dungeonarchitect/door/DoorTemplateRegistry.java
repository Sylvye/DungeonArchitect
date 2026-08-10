package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateLoadStatus;
import com.dungeonarchitect.template.TemplateValidationResult;

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

public final class DoorTemplateRegistry {
    private final Path doorsDirectory;
    private final DoorTemplateValidator validator;
    private Map<String, DoorTemplate> templates = Map.of();
    private Map<String, DoorTemplate> visibleTemplates = Map.of();
    private Map<String, TemplateLoadStatus<DoorTemplate>> statusById = Map.of();
    private List<TemplateLoadStatus<DoorTemplate>> loadStatuses = List.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public DoorTemplateRegistry(Path doorsDirectory, RoomStructureService structureService) {
        this.doorsDirectory = doorsDirectory;
        this.validator = new DoorTemplateValidator(structureService);
    }

    DoorTemplateRegistry(Path doorsDirectory, com.dungeonarchitect.template.StructureSizeReader sizeReader, boolean ignored) {
        this.doorsDirectory = doorsDirectory;
        this.validator = new DoorTemplateValidator(sizeReader, true);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, DoorTemplate> loaded = new LinkedHashMap<>();
        Map<String, DoorTemplate> visible = new LinkedHashMap<>();
        Map<String, TemplateLoadStatus<DoorTemplate>> statusesById = new LinkedHashMap<>();
        List<TemplateLoadStatus<DoorTemplate>> statuses = new ArrayList<>();
        try {
            Files.createDirectories(doorsDirectory);
            try (var stream = Files.list(doorsDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    TemplateLoadStatus<DoorTemplate> loadedStatus = DoorTemplateIO.loadRecovering(directory, validator.sizeReader());
                    if (!loadedStatus.loadable()) {
                        statuses.add(loadedStatus);
                        result.addAll(loadedStatus.errors());
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    DoorTemplate template = loadedStatus.template();
                    List<String> errors = new ArrayList<>(loadedStatus.errors());
                    if (visible.containsKey(template.id())) {
                        errors.add("Duplicate door id " + template.id());
                        TemplateLoadStatus<DoorTemplate> duplicate = new TemplateLoadStatus<>(template, template.id(), directory, false, errors, loadedStatus.repairs());
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
                    TemplateLoadStatus<DoorTemplate> status = new TemplateLoadStatus<>(template, template.id(), directory, valid, errors, loadedStatus.repairs());
                    statuses.add(status);
                    visible.put(template.id(), template);
                    statusesById.put(template.id(), status);
                    if (valid) {
                        loaded.put(template.id(), template);
                    }
                }
            }
        } catch (IOException ex) {
            result.add("Failed to scan doors directory: " + ex.getMessage());
        }
        templates = Map.copyOf(loaded);
        visibleTemplates = Map.copyOf(visible);
        statusById = Map.copyOf(statusesById);
        loadStatuses = List.copyOf(statuses);
        lastValidation = result;
        return result;
    }

    public Optional<DoorTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<DoorTemplate> all() {
        return templates.values();
    }

    public Optional<DoorTemplate> getVisible(String id) {
        return Optional.ofNullable(visibleTemplates.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<DoorTemplate> visible() {
        return visibleTemplates.values();
    }

    public Optional<TemplateLoadStatus<DoorTemplate>> status(String id) {
        return Optional.ofNullable(statusById.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public List<TemplateLoadStatus<DoorTemplate>> loadStatuses() {
        return loadStatuses;
    }

    public long invalidCount() {
        return loadStatuses.stream().filter(status -> status.loadable() && !status.valid()).count();
    }

    public long unrecoverableCount() {
        return loadStatuses.stream().filter(status -> !status.loadable()).count();
    }

    public Path doorsDirectory() {
        return doorsDirectory;
    }

    public TemplateValidationResult lastValidation() {
        return lastValidation;
    }

    public void deleteDoor(String doorId) throws IOException {
        Path directory = templateDirectory(doorId);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Unknown door template " + doorId);
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        reload();
    }

    public DoorTemplate duplicateDoor(String oldId, String newId) throws IOException {
        String normalizedNewId = normalizeId(newId);
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(normalizedNewId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown door template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Door template already exists: " + normalizedNewId);
        }
        DoorTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "duplication");
        copyDirectory(source, target);
        DoorTemplate renamed = new DoorTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), sourceTemplate.markers(), sourceTemplate.featureSlots(), sourceTemplate.gateway(), target.resolve("door.nbt"));
        DoorTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public DoorTemplate renameDoor(String oldId, String newId) throws IOException {
        String normalizedNewId = normalizeId(newId);
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(normalizedNewId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown door template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Door template already exists: " + normalizedNewId);
        }
        DoorTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "rename");
        Files.move(source, target);
        DoorTemplate renamed = new DoorTemplate(normalizedNewId, sourceTemplate.size(), sourceTemplate.tags(), sourceTemplate.markers(), sourceTemplate.featureSlots(), sourceTemplate.gateway(), target.resolve("door.nbt"));
        DoorTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    private Path templateDirectory(String doorId) {
        String normalized = normalizeId(doorId);
        Path root = doorsDirectory.toAbsolutePath().normalize();
        Path directory = root.resolve(normalized).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Invalid door id " + doorId);
        }
        return directory;
    }

    private DoorTemplate loadVisibleTemplateForOperation(String doorId, Path directory, String operation) {
        DoorTemplate visible = getVisible(doorId).orElse(null);
        if (visible != null) {
            return visible;
        }
        TemplateLoadStatus<DoorTemplate> status = DoorTemplateIO.loadRecovering(directory, validator.sizeReader());
        if (!status.loadable()) {
            throw new IllegalArgumentException("Door template cannot be loaded for " + operation + ": " + doorId);
        }
        return status.template();
    }

    private String normalizeId(String doorId) {
        if (doorId == null || doorId.isBlank() || doorId.contains("/") || doorId.contains("\\") || doorId.contains("..")) {
            throw new IllegalArgumentException("Invalid door id " + doorId);
        }
        return doorId.toLowerCase(java.util.Locale.ROOT);
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
