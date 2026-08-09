package com.dungeonarchitect.command;

import com.dungeonarchitect.authoring.AuthoringSession;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ComponentCommandTargetTest {
    @Test
    void explicitTypeAndIdWin() {
        var selected = Optional.of(new AuthoringSession.SelectedComponent("door", "selected_door"));

        ComponentCommandTarget target = ComponentCommandTarget.resolve(new String[] {"room", "component", "bounds", "feature", "slot_a"}, 3, selected);

        assertEquals(new ComponentCommandTarget("feature", "slot_a"), target);
    }

    @Test
    void omittedTypeAndIdUseSelectedComponent() {
        var selected = Optional.of(new AuthoringSession.SelectedComponent("marker", "spawn"));

        ComponentCommandTarget target = ComponentCommandTarget.resolve(new String[] {"room", "component", "remove"}, 3, selected);

        assertEquals(new ComponentCommandTarget("marker", "spawn"), target);
    }

    @Test
    void typeOnlyUsesSelectedIdWhenTypeMatches() {
        var selected = Optional.of(new AuthoringSession.SelectedComponent("door", "door_a"));

        ComponentCommandTarget target = ComponentCommandTarget.resolve(new String[] {"room", "component", "rename", "door"}, 3, selected);

        assertEquals(new ComponentCommandTarget("door", "door_a"), target);
    }

    @Test
    void typeOnlyRejectsMismatchedSelection() {
        var selected = Optional.of(new AuthoringSession.SelectedComponent("feature", "slot_a"));

        assertThrows(IllegalArgumentException.class, () -> ComponentCommandTarget.resolve(new String[] {"room", "component", "bounds", "door"}, 3, selected));
    }

    @Test
    void missingTargetRejectsWithoutSelection() {
        assertThrows(IllegalArgumentException.class, () -> ComponentCommandTarget.resolve(new String[] {"room", "component", "select"}, 3, Optional.empty()));
    }

    @Test
    void renameValueIsOptionalAfterTypeAndId() {
        assertEquals(Optional.of("door_b"), ComponentCommandTarget.renameValue(new String[] {"room", "component", "rename", "door", "door_a", "door_b"}, 3));
        assertEquals(Optional.empty(), ComponentCommandTarget.renameValue(new String[] {"room", "component", "rename", "door", "door_a"}, 3));
    }
}
