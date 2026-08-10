package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
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
    private final FeatureTemplateRegistry featureRegistry;
    private final DoorTemplateRegistry doorRegistry;
    private Map<String, RoomTemplate> templates = Map.of();
    private Map<String, RoomTemplate> visibleTemplates = Map.of();
    private Map<String, TemplateLoadStatus<RoomTemplate>> statusById = Map.of();
    private List<TemplateLoadStatus<RoomTemplate>> loadStatuses = List.of();
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
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
        this.validator = new RoomTemplateValidator(structureService, featureRegistry, doorRegistry);
    }

    RoomTemplateRegistry(Path roomsDirectory, StructureSizeReader sizeReader, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry) {
        this.roomsDirectory = roomsDirectory;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
        this.validator = new RoomTemplateValidator(sizeReader, featureRegistry, doorRegistry);
    }

    public TemplateValidationResult reload() {
        TemplateValidationResult result = new TemplateValidationResult();
        Map<String, RoomTemplate> loaded = new LinkedHashMap<>();
        Map<String, RoomTemplate> visible = new LinkedHashMap<>();
        Map<String, TemplateLoadStatus<RoomTemplate>> statusesById = new LinkedHashMap<>();
        List<TemplateLoadStatus<RoomTemplate>> statuses = new ArrayList<>();
        try {
            Files.createDirectories(roomsDirectory);
            try (var stream = Files.list(roomsDirectory)) {
                for (Path directory : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    TemplateLoadStatus<RoomTemplate> loadedStatus = RoomTemplateIO.loadRecovering(directory, validator.sizeReader());
                    if (!loadedStatus.loadable()) {
                        statuses.add(loadedStatus);
                        result.addAll(loadedStatus.errors());
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    RoomTemplate template = loadedStatus.template();
                    List<String> errors = new ArrayList<>(loadedStatus.errors());
                    List<String> repairs = new ArrayList<>(loadedStatus.repairs());
                    if (visible.containsKey(template.id())) {
                        errors.add("Duplicate room id " + template.id());
                        TemplateLoadStatus<RoomTemplate> duplicate = new TemplateLoadStatus<>(template, template.id(), directory, false, errors, loadedStatus.repairs());
                        statuses.add(duplicate);
                        result.addAll(errors);
                        result.addRepairs(loadedStatus.repairs());
                        continue;
                    }
                    CleanupResult cleanup = cleanupInvalidSelections(template);
                    if (cleanup.changed()) {
                        template = cleanup.template();
                        RoomTemplateIO.save(template, directory);
                        repairs.addAll(cleanup.repairs());
                    }
                    TemplateValidationResult templateResult = validator.validate(template);
                    errors.addAll(templateResult.errors());
                    result.addAll(errors);
                    result.addRepairs(repairs);
                    boolean valid = errors.isEmpty();
                    TemplateLoadStatus<RoomTemplate> status = new TemplateLoadStatus<>(template, template.id(), directory, valid, errors, repairs);
                    statuses.add(status);
                    visible.put(template.id(), template);
                    statusesById.put(template.id(), status);
                    if (valid) {
                        loaded.put(template.id(), template);
                    }
                }
            }
        } catch (IOException ex) {
            result.add("Failed to scan rooms directory: " + ex.getMessage());
        }
        this.templates = Map.copyOf(loaded);
        this.visibleTemplates = Map.copyOf(visible);
        this.statusById = Map.copyOf(statusesById);
        this.loadStatuses = List.copyOf(statuses);
        this.lastValidation = result;
        return result;
    }

    public Optional<RoomTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<RoomTemplate> all() {
        return templates.values();
    }

    public Optional<RoomTemplate> getVisible(String id) {
        return Optional.ofNullable(visibleTemplates.get(id));
    }

    public Collection<RoomTemplate> visible() {
        return visibleTemplates.values();
    }

    public Optional<TemplateLoadStatus<RoomTemplate>> status(String id) {
        return Optional.ofNullable(statusById.get(id));
    }

    public List<TemplateLoadStatus<RoomTemplate>> loadStatuses() {
        return loadStatuses;
    }

    public long invalidCount() {
        return loadStatuses.stream().filter(status -> status.loadable() && !status.valid()).count();
    }

    public long unrecoverableCount() {
        return loadStatuses.stream().filter(status -> !status.loadable()).count();
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
        RoomTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "duplication");
        copyDirectory(source, target);
        RoomTemplate renamed = new RoomTemplate(newId, sourceTemplate.category(), sourceTemplate.weight(), sourceTemplate.tags(), sourceTemplate.size(), sourceTemplate.spawn(), sourceTemplate.doors(), sourceTemplate.markers(), sourceTemplate.featureSlots(), target.resolve("room.nbt"));
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
        RoomTemplate sourceTemplate = loadVisibleTemplateForOperation(oldId, source, "rename");
        Files.move(source, target);
        RoomTemplate renamed = new RoomTemplate(newId, sourceTemplate.category(), sourceTemplate.weight(), sourceTemplate.tags(), sourceTemplate.size(), sourceTemplate.spawn(), sourceTemplate.doors(), sourceTemplate.markers(), sourceTemplate.featureSlots(), target.resolve("room.nbt"));
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

    private RoomTemplate loadVisibleTemplateForOperation(String roomId, Path directory, String operation) {
        RoomTemplate visible = getVisible(roomId).orElse(null);
        if (visible != null) {
            return visible;
        }
        TemplateLoadStatus<RoomTemplate> status = RoomTemplateIO.loadRecovering(directory, validator.sizeReader());
        if (!status.loadable()) {
            throw new IllegalArgumentException("Room template cannot be loaded for " + operation + ": " + roomId);
        }
        return status.template();
    }

    private CleanupResult cleanupInvalidSelections(RoomTemplate template) {
        boolean changed = false;
        List<String> repairs = new ArrayList<>();
        List<DoorSocket> doors = new ArrayList<>();
        for (DoorSocket door : template.doors()) {
            if (doorRegistry == null) {
                doors.add(door);
                continue;
            }
            List<DoorSlotEntry> entries = new ArrayList<>();
            for (DoorSlotEntry entry : door.entries()) {
                if (entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                    entries.add(entry);
                    continue;
                }
                var selected = doorRegistry.get(entry.doorId());
                if (selected.isEmpty()) {
                    changed = true;
                    repairs.add(template.id() + ": removed missing or invalid door " + entry.doorId() + " from slot " + door.id());
                    continue;
                }
                var match = DoorTemplateMatcher.match(door, selected.get());
                if (!match.matched()) {
                    changed = true;
                    repairs.add(template.id() + ": removed incompatible door " + entry.doorId() + " from slot " + door.id() + ": " + match.reason());
                    continue;
                }
                entries.add(entry);
            }
            doors.add(changed ? door.withEntries(entries) : door);
        }
        List<RoomFeatureSlot> features = new ArrayList<>();
        for (RoomFeatureSlot slot : template.featureSlots()) {
            if (featureRegistry == null) {
                features.add(slot);
                continue;
            }
            List<FeatureSlotEntry> entries = new ArrayList<>();
            for (FeatureSlotEntry entry : slot.entries()) {
                if (entry.featureId().equals(FeatureSlotEntry.EMPTY)) {
                    entries.add(entry);
                    continue;
                }
                var selected = featureRegistry.get(entry.featureId());
                if (selected.isEmpty()) {
                    changed = true;
                    repairs.add(template.id() + ": removed missing or invalid feature " + entry.featureId() + " from slot " + slot.id());
                    continue;
                }
                var match = FeatureMatcher.match(slot, selected.get());
                if (!match.matched()) {
                    changed = true;
                    repairs.add(template.id() + ": removed incompatible feature " + entry.featureId() + " from slot " + slot.id() + ": " + match.reason());
                    continue;
                }
                entries.add(entry);
            }
            features.add(changed ? slot.withEntries(entries) : slot);
        }
        if (!changed) {
            return new CleanupResult(template, false, List.of());
        }
        return new CleanupResult(new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), doors, template.markers(), features, template.structureFile()), true, repairs);
    }

    private record CleanupResult(RoomTemplate template, boolean changed, List<String> repairs) {
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
