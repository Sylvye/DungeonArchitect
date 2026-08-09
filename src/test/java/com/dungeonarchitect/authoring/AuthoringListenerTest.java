package com.dungeonarchitect.authoring;

import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthoringListenerTest {
    @Test
    void onlyMainHandBlockClicksCanSetWandSelection() {
        assertTrue(AuthoringListener.canSetWandSelection(EquipmentSlot.HAND, Action.LEFT_CLICK_BLOCK, true));
        assertTrue(AuthoringListener.canSetWandSelection(EquipmentSlot.HAND, Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(AuthoringListener.canSetWandSelection(EquipmentSlot.OFF_HAND, Action.LEFT_CLICK_BLOCK, true));
        assertFalse(AuthoringListener.canSetWandSelection(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR, false));
    }
}
