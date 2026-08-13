package com.dungeonarchitect.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import static com.dungeonarchitect.gui.LootEditorInteractionRules.Intent;
import static com.dungeonarchitect.gui.LootEditorInteractionRules.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootEditorInteractionRulesTest {
    @Test
    void playerInventoryIsVanillaExceptShiftInsertAndCollect() {
        assertEquals(Intent.PASS, classify(Region.PLAYER, InventoryAction.PLACE_ALL, ClickType.LEFT, true));
        assertEquals(Intent.INSERT_PLAYER, classify(Region.PLAYER, InventoryAction.MOVE_TO_OTHER_INVENTORY, ClickType.SHIFT_LEFT, true));
        assertEquals(Intent.COLLECT, classify(Region.PLAYER, InventoryAction.COLLECT_TO_CURSOR, ClickType.DOUBLE_CLICK, true));
    }

    @Test
    void occupiedEditorEntryUsesVanillaExtractionAndSwapActions() {
        assertEquals(Intent.EXTRACT_CURSOR, classify(Region.EDITOR, InventoryAction.PICKUP_ALL, ClickType.LEFT, true));
        assertEquals(Intent.EXTRACT_INVENTORY, classify(Region.EDITOR, InventoryAction.MOVE_TO_OTHER_INVENTORY, ClickType.SHIFT_LEFT, true));
        assertEquals(Intent.REPLACE_CURSOR, classify(Region.EDITOR, InventoryAction.SWAP_WITH_CURSOR, ClickType.LEFT, true));
        assertEquals(Intent.DROP_ENTRY, classify(Region.EDITOR, InventoryAction.DROP_ALL_SLOT, ClickType.CONTROL_DROP, true));
        assertEquals(Intent.CONFIGURE, classify(Region.EDITOR, InventoryAction.PICKUP_HALF, ClickType.RIGHT, true));
    }

    @Test
    void emptyEditorEntryAcceptsCursorAndHotbarInsertion() {
        assertEquals(Intent.INSERT_CURSOR, classify(Region.EDITOR, InventoryAction.PLACE_ONE, ClickType.RIGHT, false));
        assertEquals(Intent.INSERT_CURSOR, classify(Region.EDITOR, InventoryAction.SWAP_WITH_CURSOR, ClickType.LEFT, false));
        assertEquals(Intent.HOTBAR_SWAP, classify(Region.EDITOR, InventoryAction.HOTBAR_SWAP, ClickType.NUMBER_KEY, false));
    }

    @Test
    void specialActionsRemainVanillaOrExplicitlyEmulated() {
        assertEquals(Intent.PASS, classify(Region.OUTSIDE, InventoryAction.DROP_ALL_CURSOR, ClickType.WINDOW_BORDER_LEFT, false));
        assertEquals(Intent.TOOLBAR, classify(Region.TOOLBAR, InventoryAction.PICKUP_ALL, ClickType.LEFT, true));
        assertEquals(Intent.CLONE, classify(Region.EDITOR, InventoryAction.CLONE_STACK, ClickType.MIDDLE, true));
        assertEquals(Intent.COLLECT, classify(Region.EDITOR, InventoryAction.COLLECT_TO_CURSOR, ClickType.DOUBLE_CLICK, true));
        assertEquals(Intent.UNKNOWN, classify(Region.EDITOR, InventoryAction.UNKNOWN, ClickType.UNKNOWN, true));
    }

    @Test
    void everyBukkitActionHasADefinedResult() {
        for (InventoryAction action : InventoryAction.values()) {
            assertNotNull(classify(Region.EDITOR, action, ClickType.LEFT, true), action.name());
            assertNotNull(classify(Region.EDITOR, action, ClickType.LEFT, false), action.name());
        }
    }

    @Test
    void dragTransferConservesEveryCursorItem() {
        var transfer = LootEditorInteractionRules.dragTransfer(16, 5, 7);
        assertEquals(7, transfer.insertedEntries());
        assertEquals(4, transfer.cursorRemainder());
        assertEquals(16, 5 + transfer.insertedEntries() + transfer.cursorRemainder());
        assertEquals(new LootEditorInteractionRules.DragTransfer(3, 0), LootEditorInteractionRules.dragTransfer(3, 0, 45));
        assertEquals(new LootEditorInteractionRules.DragTransfer(45, 19), LootEditorInteractionRules.dragTransfer(64, 0, 45));
        assertEquals(new LootEditorInteractionRules.DragTransfer(0, 11), LootEditorInteractionRules.dragTransfer(16, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> LootEditorInteractionRules.dragTransfer(2, 3, 1));
    }

    private static Intent classify(Region region, InventoryAction action, ClickType click, boolean occupied) {
        return LootEditorInteractionRules.classify(region, action, click, occupied);
    }
}
