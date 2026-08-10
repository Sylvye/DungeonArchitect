package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class EditWorkspaceStore {
    public static final int SPACING = 512;
    public static final IntVector3 CLEAR_SIZE = new IntVector3(256, 160, 256);
    public static final IntVector3 BUILD_OFFSET = new IntVector3(32, 80, 32);

    private final Path file;
    private final Map<UUID, Integer> assignments = new LinkedHashMap<>();
    private final Map<UUID, BoundingBox3i> dirtyBounds = new LinkedHashMap<>();
    private final Map<UUID, Boolean> initialized = new LinkedHashMap<>();
    private boolean loaded;

    public EditWorkspaceStore(Path file) {
        this.file = file;
    }

    public synchronized EditWorkspace workspace(UUID owner) {
        loadIfNeeded();
        Integer index = assignments.get(owner);
        if (index == null) {
            index = nextIndex();
            assignments.put(owner, index);
            initialized.put(owner, true);
            save();
        }
        return workspace(owner, index);
    }

    public synchronized void markDirty(UUID owner, BoundingBox3i bounds) {
        loadIfNeeded();
        EditWorkspace workspace = workspace(owner);
        clip(bounds, workspace.clearBounds()).ifPresent(clipped -> {
            dirtyBounds.merge(owner, clipped, EditWorkspaceStore::union);
            initialized.put(owner, true);
            save();
        });
    }

    public synchronized java.util.Optional<BoundingBox3i> dirtyBounds(UUID owner) {
        loadIfNeeded();
        return java.util.Optional.ofNullable(dirtyBounds.get(owner));
    }

    public synchronized boolean needsLegacyClear(UUID owner) {
        loadIfNeeded();
        return assignments.containsKey(owner) && !initialized.getOrDefault(owner, false) && !dirtyBounds.containsKey(owner);
    }

    public synchronized void markClean(UUID owner) {
        loadIfNeeded();
        dirtyBounds.remove(owner);
        initialized.put(owner, true);
        save();
    }

    static EditWorkspace workspace(UUID owner, int index) {
        IntVector3 clearMin = new IntVector3(index * SPACING, 0, 0);
        IntVector3 clearMax = clearMin.add(CLEAR_SIZE).subtract(new IntVector3(1, 1, 1));
        return new EditWorkspace(owner, index, new BoundingBox3i(clearMin, clearMax), clearMin.add(BUILD_OFFSET));
    }

    private int nextIndex() {
        return assignments.values().stream().max(Comparator.naturalOrder()).map(value -> value + 1).orElse(0);
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                assignments.put(UUID.fromString(key), players.getInt(key));
            }
        }
        var initializedSection = yaml.getConfigurationSection("initialized");
        if (initializedSection != null) {
            for (String key : initializedSection.getKeys(false)) {
                initialized.put(UUID.fromString(key), initializedSection.getBoolean(key));
            }
        }
        var dirtySection = yaml.getConfigurationSection("dirty");
        if (dirtySection != null) {
            for (String key : dirtySection.getKeys(false)) {
                UUID owner = UUID.fromString(key);
                dirtyBounds.put(owner, new BoundingBox3i(
                    vector(yaml, "dirty." + key + ".min"),
                    vector(yaml, "dirty." + key + ".max")
                ));
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
                yaml.set("players." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, Boolean> entry : initialized.entrySet()) {
                yaml.set("initialized." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, BoundingBox3i> entry : dirtyBounds.entrySet()) {
                writeVector(yaml, "dirty." + entry.getKey() + ".min", entry.getValue().min());
                writeVector(yaml, "dirty." + entry.getKey() + ".max", entry.getValue().max());
            }
            yaml.save(file.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save edit workspace assignments: " + ex.getMessage(), ex);
        }
    }

    private static java.util.Optional<BoundingBox3i> clip(BoundingBox3i bounds, BoundingBox3i workspace) {
        int minX = Math.max(bounds.min().x(), workspace.min().x());
        int minY = Math.max(bounds.min().y(), workspace.min().y());
        int minZ = Math.max(bounds.min().z(), workspace.min().z());
        int maxX = Math.min(bounds.max().x(), workspace.max().x());
        int maxY = Math.min(bounds.max().y(), workspace.max().y());
        int maxZ = Math.min(bounds.max().z(), workspace.max().z());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new BoundingBox3i(new IntVector3(minX, minY, minZ), new IntVector3(maxX, maxY, maxZ)));
    }

    private static BoundingBox3i union(BoundingBox3i first, BoundingBox3i second) {
        return new BoundingBox3i(
            new IntVector3(
                Math.min(first.min().x(), second.min().x()),
                Math.min(first.min().y(), second.min().y()),
                Math.min(first.min().z(), second.min().z())
            ),
            new IntVector3(
                Math.max(first.max().x(), second.max().x()),
                Math.max(first.max().y(), second.max().y()),
                Math.max(first.max().z(), second.max().z())
            )
        );
    }

    private static IntVector3 vector(YamlConfiguration yaml, String path) {
        return new IntVector3(yaml.getInt(path + ".x"), yaml.getInt(path + ".y"), yaml.getInt(path + ".z"));
    }

    private static void writeVector(YamlConfiguration yaml, String path, IntVector3 vector) {
        yaml.set(path + ".x", vector.x());
        yaml.set(path + ".y", vector.y());
        yaml.set(path + ".z", vector.z());
    }
}
