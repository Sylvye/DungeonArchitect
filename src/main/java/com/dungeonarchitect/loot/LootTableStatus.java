package com.dungeonarchitect.loot;

import java.nio.file.Path;
import java.util.List;

public record LootTableStatus(LootTable table, String id, Path file, List<String> errors) {
    public LootTableStatus {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() { return table != null && errors.isEmpty(); }
}
