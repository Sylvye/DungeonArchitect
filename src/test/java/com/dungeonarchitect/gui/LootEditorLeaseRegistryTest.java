package com.dungeonarchitect.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootEditorLeaseRegistryTest {
    @Test
    void leaseIsExclusiveAndCaseInsensitive() {
        LootEditorLeaseRegistry leases = new LootEditorLeaseRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(leases.acquire("Treasure", first, "Alice").acquired());
        var denied = leases.acquire("treasure", second, "Bob");
        assertFalse(denied.acquired());
        assertEquals("Alice", denied.lease().ownerName());
    }

    @Test
    void onlyOwnerCanReleaseLease() {
        LootEditorLeaseRegistry leases = new LootEditorLeaseRegistry();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        leases.acquire("treasure", owner, "Alice");

        leases.release("treasure", other);
        assertTrue(leases.lease("treasure").isPresent());
        leases.release("TREASURE", owner);
        assertTrue(leases.lease("treasure").isEmpty());
    }

    @Test
    void clearReleasesAllLeasesForReload() {
        LootEditorLeaseRegistry leases = new LootEditorLeaseRegistry();
        leases.acquire("one", UUID.randomUUID(), "Alice");
        leases.acquire("two", UUID.randomUUID(), "Bob");
        leases.clear();
        assertTrue(leases.lease("one").isEmpty());
        assertTrue(leases.lease("two").isEmpty());
    }
}
