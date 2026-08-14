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
import java.util.HashSet;
import java.util.Set;
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
        List<LootTableStatus> validated = new ArrayList<>();
        for (LootTableStatus status : loadedStatuses) {
            if (status.table() == null) { validated.add(status); continue; }
            List<String> tableErrors = new ArrayList<>(status.errors());
            List<String> dependencyErrors = validationErrors(status.table());
            tableErrors.addAll(dependencyErrors);
            dependencyErrors.stream().filter(error -> !errors.contains(error)).forEach(errors::add);
            validated.add(new LootTableStatus(status.table(), status.id(), status.file(), tableErrors));
        }
        loadErrors = List.copyOf(errors);
        statuses = List.copyOf(validated);
    }
    public Collection<LootTable> all() { return ListCopy.copy(tables.values()); }
    public Optional<LootTable> get(String id) { return Optional.ofNullable(tables.get(id.toLowerCase(Locale.ROOT))); }
    public void save(LootTable table) throws IOException {
        String id = TopLevelIdentity.normalize(table.id());
        TopLevelIdentity.requireAvailable(directory, "loot table", id, tables.containsKey(id) ? id : null);
        List<String> errors = validationErrors(table);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.getFirst());
        LootTableIO.save(table, file(id));
        reload();
    }
    public void delete(String id) throws IOException {
        Files.deleteIfExists(file(id));
        reload();
    }
    public Path directory() { return directory; }
    public List<String> loadErrors() { return loadErrors; }
    public List<LootTableStatus> statuses() { return statuses; }
    public boolean usable(String id) {
        LootTable table = get(id).orElse(null);
        return table != null && !table.entries().isEmpty() && validationErrors(table).isEmpty() && hasReachableItem(table, new HashSet<>());
    }
    public List<String> validationErrors(LootTable candidate) {
        Map<String, LootTable> prospective = new LinkedHashMap<>(tables);
        prospective.put(candidate.id(), candidate);
        List<String> errors = new ArrayList<>();
        Set<String> references = new HashSet<>();
        for (LootPoolEntry entry : candidate.entries()) {
            if (!(entry instanceof LootTableEntry nested)) continue;
            if (!references.add(nested.tableId())) errors.add(candidate.id() + " references " + nested.tableId() + " more than once");
            if (!prospective.containsKey(nested.tableId())) errors.add(candidate.id() + " references unknown loot table " + nested.tableId());
        }
        List<String> cycle = cycleFrom(candidate.id(), prospective, new ArrayList<>(), new HashSet<>());
        if (!cycle.isEmpty()) errors.add("Loot table cycle: " + String.join(" -> ", cycle));
        return List.copyOf(errors);
    }
    public List<String> parentsOf(String tableId) {
        String normalized = tableId.toLowerCase(Locale.ROOT);
        return tables.values().stream()
            .filter(table -> table.entries().stream().anyMatch(entry -> entry instanceof LootTableEntry nested && nested.tableId().equals(normalized)))
            .map(LootTable::id).sorted().toList();
    }
    private List<String> cycleFrom(String current, Map<String, LootTable> source, List<String> path, Set<String> complete) {
        int existing = path.indexOf(current);
        if (existing >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(existing, path.size()));
            cycle.add(current);
            return cycle;
        }
        if (!complete.add(current)) return List.of();
        LootTable table = source.get(current);
        if (table == null) return List.of();
        path.add(current);
        for (LootPoolEntry entry : table.entries()) {
            if (entry instanceof LootTableEntry nested) {
                List<String> cycle = cycleFrom(nested.tableId(), source, path, complete);
                if (!cycle.isEmpty()) return cycle;
            }
        }
        path.removeLast();
        return List.of();
    }
    private boolean hasReachableItem(LootTable table, Set<String> path) {
        if (!path.add(table.id())) return false;
        for (LootPoolEntry entry : table.entries()) {
            if (entry instanceof LootEntry) return true;
            if (entry instanceof LootTableEntry nested) {
                LootTable child = tables.get(nested.tableId());
                if (child != null && hasReachableItem(child, new HashSet<>(path))) return true;
            }
        }
        return false;
    }
    private Path file(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid loot table id " + id);
        return directory.resolve(normalized + ".yml");
    }
    private static final class ListCopy { private static <T> Collection<T> copy(Collection<T> items) { return java.util.List.copyOf(items); } }
}
