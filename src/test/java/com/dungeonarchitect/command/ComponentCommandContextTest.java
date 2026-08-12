package com.dungeonarchitect.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComponentCommandContextTest {
    @Test
    void doorContextAllowsOnlyRelevantTypesAndActions() {
        assertTrue(ComponentCommandContext.DOOR.types().contains("gateway"));
        assertFalse(ComponentCommandContext.DOOR.types().contains("door"));

        ComponentCommandContext.DOOR.requireAction("face", "gateway");
        ComponentCommandContext.DOOR.requireAction("rename", "marker");
        assertThrows(IllegalArgumentException.class, () -> ComponentCommandContext.DOOR.requireAction("remove", "gateway"));
        assertThrows(IllegalArgumentException.class, () -> ComponentCommandContext.DOOR.requireAction("rotate", "marker"));
        assertThrows(IllegalArgumentException.class, () -> ComponentCommandContext.DOOR.requireAction("select", "door"));
    }

    @Test
    void featureContextHasNoNestedComponents() {
        assertFalse(ComponentCommandContext.FEATURE.hasComponents());
        assertTrue(ComponentCommandContext.FEATURE.actions().isEmpty());
    }
}
