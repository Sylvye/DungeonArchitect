package com.dungeonarchitect.runtime;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class DungeonEntityOwnership {
    private DungeonEntityOwnership() {
    }

    static Optional<UUID> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean hasCurrentOwner(String value, Set<UUID> currentOwners) {
        return parse(value).filter(currentOwners::contains).isPresent();
    }
}
