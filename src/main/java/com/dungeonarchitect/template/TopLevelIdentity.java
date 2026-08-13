package com.dungeonarchitect.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** File-backed global namespace for rooms, doors, features, and loot tables. */
public final class TopLevelIdentity {
    private TopLevelIdentity() {}

    public static String normalize(String id) {
        String normalized = IdentityRules.canonical(id);
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid id " + id);
        }
        return normalized;
    }

    public static void requireAvailable(Path ownDirectory, String kind, String requestedId, String currentId) {
        String requested = normalize(requestedId);
        String current = currentId == null ? null : normalize(currentId);
        List<String> owners = owners(ownDirectory, requested);
        for (String owner : owners) {
            if (owner.equals(kind + ":" + current)) continue;
            throw new IllegalArgumentException("Name already exists as " + owner.replace(':', ' ') + ": " + requestedId);
        }
    }

    private static List<String> owners(Path ownDirectory, String id) {
        Path root = ownDirectory.toAbsolutePath().normalize().getParent();
        List<String> owners = new ArrayList<>();
        scanDirectories(root.resolve("rooms"), "room", id, owners);
        scanDirectories(root.resolve("doors"), "door", id, owners);
        scanDirectories(root.resolve("features"), "feature", id, owners);
        Path loot = root.resolve("loot-tables");
        if (Files.isDirectory(loot)) {
            try (var files = Files.list(loot)) {
                files.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"))
                    .filter(path -> normalize(path.getFileName().toString().replaceFirst("(?i)\\.yml$", "")).equals(id))
                    .forEach(path -> owners.add("loot table:" + id));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to inspect loot tables: " + ex.getMessage(), ex);
            }
        }
        return owners;
    }

    private static void scanDirectories(Path directory, String kind, String id, List<String> owners) {
        if (!Files.isDirectory(directory)) return;
        try (var children = Files.list(directory)) {
            children.filter(Files::isDirectory)
                .filter(path -> normalize(path.getFileName().toString()).equals(id))
                .forEach(path -> owners.add(kind + ":" + id));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect " + kind + " templates: " + ex.getMessage(), ex);
        }
    }
}
