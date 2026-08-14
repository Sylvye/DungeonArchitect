package com.dungeonarchitect.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Exclusive, in-memory edit leases for loot tables. */
final class LootEditorLeaseRegistry {
    private final Map<String, Lease> leases = new HashMap<>();

    Optional<Lease> lease(String tableId) {
        return Optional.ofNullable(leases.get(key(tableId)));
    }

    AcquireResult acquire(String tableId, UUID ownerId, String ownerName) {
        Lease requested = new Lease(ownerId, ownerName);
        Lease existing = leases.putIfAbsent(key(tableId), requested);
        if (existing == null || existing.ownerId().equals(ownerId)) return new AcquireResult(true, existing == null ? requested : existing);
        return new AcquireResult(false, existing);
    }

    void release(String tableId, UUID ownerId) {
        leases.computeIfPresent(key(tableId), (ignored, lease) -> lease.ownerId().equals(ownerId) ? null : lease);
    }

    void clear() {
        leases.clear();
    }

    private static String key(String tableId) {
        return tableId.toLowerCase(Locale.ROOT);
    }

    record Lease(UUID ownerId, String ownerName) { }
    record AcquireResult(boolean acquired, Lease lease) { }
}
