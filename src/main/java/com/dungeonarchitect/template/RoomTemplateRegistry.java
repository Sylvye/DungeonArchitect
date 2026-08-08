package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.RoomTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RoomTemplateRegistry {
    private final Path roomsDirectory;
    private final RoomTemplateValidator validator;
    private Map<String, RoomTemplate> templates = Map.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public RoomTemplateRegistry(Path roomsDirectory) {
        this(roomsDirectory, null);
    }

    public RoomTemplateRegistry(Path roomsDirectory, RoomStructureService structureService) {
        this.roomsDirectory = roomsDirectory;
        this.validator = new RoomTemplateValidator(structureService);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, RoomTemplate> loaded = new LinkedHashMap<>();
        try {
            Files.createDirectories(roomsDirectory);
            try (var stream = Files.list(roomsDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    try {
                        RoomTemplate template = RoomTemplateIO.load(directory);
                        if (loaded.containsKey(template.id())) {
                            result.add("Duplicate room id " + template.id());
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
            result.add("Failed to scan rooms directory: " + ex.getMessage());
        }
        this.templates = Map.copyOf(loaded);
        this.lastValidation = result;
        return result;
    }

    public Optional<RoomTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<RoomTemplate> all() {
        return templates.values();
    }

    public TemplateValidationResult lastValidation() {
        return lastValidation;
    }

    public Path roomsDirectory() {
        return roomsDirectory;
    }

    public void deleteRoom(String roomId) throws IOException {
        Path roomDirectory = roomsDirectory.resolve(roomId).toAbsolutePath().normalize();
        Path root = roomsDirectory.toAbsolutePath().normalize();
        if (!roomDirectory.startsWith(root) || !Files.isDirectory(roomDirectory)) {
            throw new IllegalArgumentException("Unknown room template " + roomId);
        }
        try (var walk = Files.walk(roomDirectory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        reload();
    }
}
