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
            save();
        }
        return workspace(owner, index);
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
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            assignments.put(UUID.fromString(key), players.getInt(key));
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
                yaml.set("players." + entry.getKey(), entry.getValue());
            }
            yaml.save(file.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save edit workspace assignments: " + ex.getMessage(), ex);
        }
    }
}
