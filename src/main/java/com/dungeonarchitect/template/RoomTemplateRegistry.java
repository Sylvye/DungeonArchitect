package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;

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

public final class RoomTemplateRegistry {
    private final Path roomsDirectory;
    private final RoomTemplateValidator validator;
    private Map<String, RoomTemplate> templates = Map.of();
    private TemplateValidationResult lastValidation = new TemplateValidationResult();

    public RoomTemplateRegistry(Path roomsDirectory) {
        this(roomsDirectory, null);
    }

    public RoomTemplateRegistry(Path roomsDirectory, RoomStructureService structureService) {
        this(roomsDirectory, structureService, null);
    }

    public RoomTemplateRegistry(Path roomsDirectory, RoomStructureService structureService, FeatureTemplateRegistry featureRegistry) {
        this(roomsDirectory, structureService, featureRegistry, null);
    }

    public RoomTemplateRegistry(Path roomsDirectory, RoomStructureService structureService, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry) {
        this.roomsDirectory = roomsDirectory;
        this.validator = new RoomTemplateValidator(structureService, featureRegistry, doorRegistry);
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
        Path roomDirectory = templateDirectory(roomId);
        if (!Files.isDirectory(roomDirectory)) {
            throw new IllegalArgumentException("Unknown room template " + roomId);
        }
        try (var walk = Files.walk(roomDirectory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        reload();
    }

    public RoomTemplate duplicateRoom(String oldId, String newId) throws IOException {
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(newId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown room template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Room template already exists: " + newId);
        }
        copyDirectory(source, target);
        RoomTemplate copied = RoomTemplateIO.load(target);
        RoomTemplate renamed = new RoomTemplate(newId, copied.category(), copied.weight(), copied.tags(), copied.size(), copied.spawn(), copied.doors(), copied.markers(), copied.featureSlots(), target.resolve("room.nbt"));
        RoomTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public RoomTemplate renameRoom(String oldId, String newId) throws IOException {
        Path source = templateDirectory(oldId);
        Path target = templateDirectory(newId);
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Unknown room template " + oldId);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Room template already exists: " + newId);
        }
        Files.move(source, target);
        RoomTemplate moved = RoomTemplateIO.load(target);
        RoomTemplate renamed = new RoomTemplate(newId, moved.category(), moved.weight(), moved.tags(), moved.size(), moved.spawn(), moved.doors(), moved.markers(), moved.featureSlots(), target.resolve("room.nbt"));
        RoomTemplateIO.save(renamed, target);
        reload();
        return renamed;
    }

    public void replaceFeatureReferences(String oldFeatureId, String newFeatureId) throws IOException {
        Files.createDirectories(roomsDirectory);
        try (var stream = Files.list(roomsDirectory)) {
            for (Path directory : stream.filter(Files::isDirectory).toList()) {
                RoomTemplate template = RoomTemplateIO.load(directory);
                List<RoomFeatureSlot> slots = new ArrayList<>();
                boolean changed = false;
                for (RoomFeatureSlot slot : template.featureSlots()) {
                    List<FeatureSlotEntry> entries = new ArrayList<>();
                    for (FeatureSlotEntry entry : slot.entries()) {
                        if (entry.featureId().equalsIgnoreCase(oldFeatureId)) {
                            entries.add(new FeatureSlotEntry(newFeatureId, entry.weight()));
                            changed = true;
                        } else {
                            entries.add(entry);
                        }
                    }
                    slots.add(slot.withEntries(entries));
                }
                if (changed) {
                    RoomTemplate updated = new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.structureFile());
                    RoomTemplateIO.save(updated, directory);
                }
            }
        }
        reload();
    }

    public void replaceDoorReferences(String oldDoorId, String newDoorId) throws IOException {
        Files.createDirectories(roomsDirectory);
        try (var stream = Files.list(roomsDirectory)) {
            for (Path directory : stream.filter(Files::isDirectory).toList()) {
                RoomTemplate template = RoomTemplateIO.load(directory);
                List<DoorSocket> doors = new ArrayList<>();
                boolean changed = false;
                for (DoorSocket door : template.doors()) {
                    List<DoorSlotEntry> entries = new ArrayList<>();
                    for (DoorSlotEntry entry : door.entries()) {
                        if (entry.doorId().equalsIgnoreCase(oldDoorId)) {
                            entries.add(new DoorSlotEntry(newDoorId, entry.weight()));
                            changed = true;
                        } else {
                            entries.add(entry);
                        }
                    }
                    doors.add(door.withEntries(entries));
                }
                if (changed) {
                    RoomTemplate updated = new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), doors, template.markers(), template.featureSlots(), template.structureFile());
                    RoomTemplateIO.save(updated, directory);
                }
            }
        }
        reload();
    }

    private Path templateDirectory(String roomId) {
        if (roomId == null || roomId.isBlank() || roomId.contains("/") || roomId.contains("\\") || roomId.contains("..")) {
            throw new IllegalArgumentException("Invalid room id " + roomId);
        }
        Path root = roomsDirectory.toAbsolutePath().normalize();
        Path directory = root.resolve(roomId).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Invalid room id " + roomId);
        }
        return directory;
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
