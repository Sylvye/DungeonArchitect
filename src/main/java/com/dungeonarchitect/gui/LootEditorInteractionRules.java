package com.dungeonarchitect.gui;

import org.bukkit.event.inventory.ClickType;
/** Pure click rules for the loot editor's Bukkit adapter. */
final class LootEditorInteractionRules {
    enum Region { EDITOR, TOOLBAR, PLAYER, OUTSIDE }
    enum Intent {
        PASS,
        TOOLBAR,
        CONFIGURE,
        ADD_TEMPLATE,
        REMOVE_ENTRY,
        RESYNC
    }

    private LootEditorInteractionRules() { }

    static Intent classify(Region region, ClickType click, boolean occupied) {
        if (region == Region.OUTSIDE) return Intent.PASS;
        if (region == Region.TOOLBAR) return Intent.TOOLBAR;
        if (region == Region.PLAYER) {
            if (occupied && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) return Intent.ADD_TEMPLATE;
            if (click == ClickType.DOUBLE_CLICK) return Intent.RESYNC;
            return Intent.PASS;
        }
        if (occupied && click == ClickType.RIGHT) return Intent.CONFIGURE;
        if (occupied && (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) return Intent.REMOVE_ENTRY;
        return Intent.RESYNC;
    }

    static int pageCount(int entryCount) {
        if (entryCount < 0) throw new IllegalArgumentException("Entry count cannot be negative");
        return Math.max(1, (entryCount + 44) / 45);
    }

    static int pageOf(int entryIndex) {
        if (entryIndex < 0) throw new IllegalArgumentException("Entry index cannot be negative");
        return entryIndex / 45;
    }

    static boolean hasPreviousPage(int page) {
        return page > 0;
    }

    static boolean hasNextPage(int page, int pageCount) {
        if (page < 0 || pageCount < 1 || page >= pageCount) {
            throw new IllegalArgumentException("Page must be within the available page count");
        }
        return page + 1 < pageCount;
    }

    static String pageTitle(String subject, int page, int pageCount) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Page title subject cannot be blank");
        if (page < 0 || pageCount < 1 || page >= pageCount) {
            throw new IllegalArgumentException("Page must be within the available page count");
        }
        return subject + " [" + (page + 1) + "/" + pageCount + "]";
    }
}
