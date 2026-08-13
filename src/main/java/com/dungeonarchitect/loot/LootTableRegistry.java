package com.dungeonarchitect.loot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import com.dungeonarchitect.template.TopLevelIdentity;

public final class LootTableRegistry {
    private final Path directory;
    private final Map<String, LootTable> tables = new LinkedHashMap<>();
    private List<String> loadErrors = List.of();
    private List<LootTableStatus> statuses = List.of();

    public LootTableRegistry(Path directory) { this.directory = directory; }
    public void reload() {
        tables.clear();
        List<String> errors = new ArrayList<>();
        List<LootTableStatus> loadedStatuses = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                for (Path path : files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                    try {
                        LootTable table = LootTableIO.load(path);
                        TopLevelIdentity.requireAvailable(directory, "loot table", table.id(), table.id());
                        if (tables.putIfAbsent(table.id(), table) != null) {
                            String error = "Duplicate loot table " + table.id();
                            errors.add(error);
                            loadedStatuses.add(new LootTableStatus(table, table.id(), path, List.of(error)));
                        } else {
                            loadedStatuses.add(new LootTableStatus(table, table.id(), path, List.of()));
                        }
                    } catch (IllegalArgumentException ex) {
                        errors.add(ex.getMessage());
                        String id = path.getFileName().toString().replaceFirst("(?i)\\.yml$", "");
                        loadedStatuses.add(new LootTableStatus(null, id, path, List.of(ex.getMessage())));
                    }
                }
            }
        } catch (IOException ex) { throw new IllegalStateException("Failed to load loot tables: " + ex.getMessage(), ex); }
        loadErrors = List.copyOf(errors);
        statuses = List.copyOf(loadedStatuses);
    }
    public Collection<LootTable> all() { return ListCopy.copy(tables.values()); }
    public Optional<LootTable> get(String id) { return Optional.ofNullable(tables.get(id.toLowerCase(Locale.ROOT))); }
    public void save(LootTable table) throws IOException {
        String id = TopLevelIdentity.normalize(table.id());
        TopLevelIdentity.requireAvailable(directory, "loot table", id, tables.containsKey(id) ? id : null);
        LootTableIO.save(table, file(id));
        tables.put(id, table);
        statuses = statuses.stream().filter(status -> !status.id().equalsIgnoreCase(id)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<LootTableStatus> updatedStatuses = new ArrayList<>(statuses);
        updatedStatuses.add(new LootTableStatus(table, id, file(id), List.of()));
        statuses = List.copyOf(updatedStatuses);
    }
    public void delete(String id) throws IOException {
        Files.deleteIfExists(file(id));
        tables.remove(id.toLowerCase(Locale.ROOT));
        statuses = statuses.stream().filter(status -> !status.id().equalsIgnoreCase(id)).toList();
    }
    public Path directory() { return directory; }
    public List<String> loadErrors() { return loadErrors; }
    public List<LootTableStatus> statuses() { return statuses; }
    private Path file(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid loot table id " + id);
        return directory.resolve(normalized + ".yml");
    }
    private static final class ListCopy { private static <T> Collection<T> copy(Collection<T> items) { return java.util.List.copyOf(items); } }
}
