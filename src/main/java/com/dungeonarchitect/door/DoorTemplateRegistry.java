package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.DoorTemplate;
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

public final class DoorTemplateRegistry {
    private final Path doorsDirectory;
    private final DoorTemplateValidator validator;
    private Map<String, DoorTemplate> templates = Map.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public DoorTemplateRegistry(Path doorsDirectory, RoomStructureService structureService) {
        this.doorsDirectory = doorsDirectory;
        this.validator = new DoorTemplateValidator(structureService);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, DoorTemplate> loaded = new LinkedHashMap<>();
        try {
            Files.createDirectories(doorsDirectory);
            try (var stream = Files.list(doorsDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    try {
                        DoorTemplate template = DoorTemplateIO.load(directory);
                        if (loaded.containsKey(template.id())) {
                            result.add("Duplicate door id " + template.id());
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
            result.add("Failed to scan doors directory: " + ex.getMessage());
        }
        templates = Map.copyOf(loaded);
        lastValidation = result;
        return result;
    }

    public Optional<DoorTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<DoorTemplate> all() {
        return templates.values();
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
        copyDirectory(source, target);
        DoorTemplate copied = DoorTemplateIO.load(target);
        DoorTemplate renamed = new DoorTemplate(normalizedNewId, copied.size(), copied.tags(), copied.markers(), copied.featureSlots(), copied.gateway(), target.resolve("door.nbt"));
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
        Files.move(source, target);
        DoorTemplate moved = DoorTemplateIO.load(target);
        DoorTemplate renamed = new DoorTemplate(normalizedNewId, moved.size(), moved.tags(), moved.markers(), moved.featureSlots(), moved.gateway(), target.resolve("door.nbt"));
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
