package com.dungeonarchitect.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/** Pure classification and conservation rules for the loot editor's Bukkit adapter. */
final class LootEditorInteractionRules {
    enum Region { EDITOR, TOOLBAR, PLAYER, OUTSIDE }
    enum Intent {
        PASS,
        TOOLBAR,
        CONFIGURE,
        INSERT_CURSOR,
        INSERT_PLAYER,
        EXTRACT_CURSOR,
        EXTRACT_INVENTORY,
        REPLACE_CURSOR,
        HOTBAR_SWAP,
        DROP_ENTRY,
        CLONE,
        COLLECT,
        RESYNC,
        UNKNOWN
    }

    private LootEditorInteractionRules() { }

    static Intent classify(Region region, InventoryAction action, ClickType click, boolean occupied) {
        if (region == Region.OUTSIDE) return Intent.PASS;
        if (region == Region.TOOLBAR) return Intent.TOOLBAR;
        if (region == Region.PLAYER) {
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) return Intent.INSERT_PLAYER;
            if (action == InventoryAction.COLLECT_TO_CURSOR) return Intent.COLLECT;
            return Intent.PASS;
        }
        if (occupied && click.isRightClick()) return Intent.CONFIGURE;
        if (action == InventoryAction.COLLECT_TO_CURSOR || click == ClickType.DOUBLE_CLICK) return Intent.COLLECT;
        if (action == InventoryAction.CLONE_STACK || click == ClickType.MIDDLE) return Intent.CLONE;
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> Intent.EXTRACT_CURSOR;
            case MOVE_TO_OTHER_INVENTORY -> Intent.EXTRACT_INVENTORY;
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> Intent.DROP_ENTRY;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> occupied ? Intent.RESYNC : Intent.INSERT_CURSOR;
            case SWAP_WITH_CURSOR -> occupied ? Intent.REPLACE_CURSOR : Intent.INSERT_CURSOR;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> Intent.HOTBAR_SWAP;
            case UNKNOWN -> Intent.UNKNOWN;
            default -> Intent.RESYNC;
        };
    }

    static DragTransfer dragTransfer(int cursorAmount, int playerInventoryAdded, int emptyEditorTargets) {
        if (cursorAmount < 0 || playerInventoryAdded < 0 || emptyEditorTargets < 0 || playerInventoryAdded > cursorAmount) {
            throw new IllegalArgumentException("Invalid drag quantities");
        }
        int insertedEntries = Math.min(emptyEditorTargets, cursorAmount - playerInventoryAdded);
        return new DragTransfer(insertedEntries, cursorAmount - playerInventoryAdded - insertedEntries);
    }

    record DragTransfer(int insertedEntries, int cursorRemainder) {
        DragTransfer {
            if (insertedEntries < 0 || cursorRemainder < 0) throw new IllegalArgumentException("Negative drag result");
        }
    }
}
