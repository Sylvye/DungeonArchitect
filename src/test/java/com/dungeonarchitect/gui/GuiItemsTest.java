package com.dungeonarchitect.gui;

import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuiItemsTest {
    @Test
    void disablesDefaultItalicStylingOnNamesAndLore() {
        var name = GuiItems.text("Room", net.kyori.adventure.text.format.NamedTextColor.WHITE);
        var lore = GuiItems.lore(List.of("Neutral lore"));

        assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE, lore.getFirst().decoration(TextDecoration.ITALIC));
    }
}
