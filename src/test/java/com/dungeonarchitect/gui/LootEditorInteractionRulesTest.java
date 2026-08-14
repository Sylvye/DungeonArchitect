package com.dungeonarchitect.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static com.dungeonarchitect.gui.LootEditorInteractionRules.Intent;
import static com.dungeonarchitect.gui.LootEditorInteractionRules.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootEditorInteractionRulesTest {
    @Test
    void playerLeftClicksAddTemplates() {
        assertEquals(Intent.ADD_TEMPLATE, classify(Region.PLAYER, ClickType.LEFT, true));
        assertEquals(Intent.ADD_TEMPLATE, classify(Region.PLAYER, ClickType.SHIFT_LEFT, true));
        assertEquals(Intent.PASS, classify(Region.PLAYER, ClickType.LEFT, false));
        assertEquals(Intent.PASS, classify(Region.PLAYER, ClickType.RIGHT, true));
    }

    @Test
    void editorLeftClicksRemoveAndRightClickConfigures() {
        assertEquals(Intent.REMOVE_ENTRY, classify(Region.EDITOR, ClickType.LEFT, true));
        assertEquals(Intent.REMOVE_ENTRY, classify(Region.EDITOR, ClickType.SHIFT_LEFT, true));
        assertEquals(Intent.CONFIGURE, classify(Region.EDITOR, ClickType.RIGHT, true));
        assertEquals(Intent.RESYNC, classify(Region.EDITOR, ClickType.LEFT, false));
    }

    @Test
    void unsupportedEditorInteractionsOnlyResynchronize() {
        assertEquals(Intent.RESYNC, classify(Region.EDITOR, ClickType.DOUBLE_CLICK, true));
        assertEquals(Intent.RESYNC, classify(Region.EDITOR, ClickType.NUMBER_KEY, true));
        assertEquals(Intent.RESYNC, classify(Region.EDITOR, ClickType.DROP, true));
        assertEquals(Intent.RESYNC, classify(Region.EDITOR, ClickType.CONTROL_DROP, true));
        assertEquals(Intent.RESYNC, classify(Region.PLAYER, ClickType.DOUBLE_CLICK, true));
        assertEquals(Intent.PASS, classify(Region.OUTSIDE, ClickType.WINDOW_BORDER_LEFT, false));
        assertEquals(Intent.TOOLBAR, classify(Region.TOOLBAR, ClickType.LEFT, true));
    }

    @Test
    void everyBukkitClickHasADefinedResult() {
        for (ClickType click : ClickType.values()) {
            assertNotNull(classify(Region.EDITOR, click, true), click.name());
            assertNotNull(classify(Region.EDITOR, click, false), click.name());
            assertNotNull(classify(Region.PLAYER, click, true), click.name());
        }
    }

    @Test
    void pagingHasNoBlankExactBoundaryPage() {
        assertEquals(1, LootEditorInteractionRules.pageCount(0));
        assertEquals(1, LootEditorInteractionRules.pageCount(1));
        assertEquals(1, LootEditorInteractionRules.pageCount(45));
        assertEquals(2, LootEditorInteractionRules.pageCount(46));
        assertEquals(3, LootEditorInteractionRules.pageCount(135));
        assertEquals(0, LootEditorInteractionRules.pageOf(44));
        assertEquals(1, LootEditorInteractionRules.pageOf(45));
        assertThrows(IllegalArgumentException.class, () -> LootEditorInteractionRules.pageCount(-1));
    }

    @Test
    void pageNavigationOnlyAppearsWhenUseful() {
        assertFalse(LootEditorInteractionRules.hasPreviousPage(0));
        assertTrue(LootEditorInteractionRules.hasPreviousPage(1));
        assertFalse(LootEditorInteractionRules.hasNextPage(0, 1));
        assertTrue(LootEditorInteractionRules.hasNextPage(0, 2));
        assertFalse(LootEditorInteractionRules.hasNextPage(1, 2));
        assertThrows(IllegalArgumentException.class, () -> LootEditorInteractionRules.hasNextPage(2, 2));
    }

    @Test
    void pageTitleIncludesCurrentAndTotalPages() {
        assertEquals("Loot: treasure [1/1]", LootEditorInteractionRules.pageTitle("Loot: treasure", 0, 1));
        assertEquals("Loot: treasure [2/3]", LootEditorInteractionRules.pageTitle("Loot: treasure", 1, 3));
        assertThrows(IllegalArgumentException.class, () -> LootEditorInteractionRules.pageTitle("Loot", 1, 1));
    }

    private static Intent classify(Region region, ClickType click, boolean occupied) {
        return LootEditorInteractionRules.classify(region, click, occupied);
    }
}
