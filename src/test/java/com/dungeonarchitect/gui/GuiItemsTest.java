package com.dungeonarchitect.gui;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuiItemsTest {
    @Test
    void disablesDefaultItalicStylingOnNamesAndLore() {
        var name = GuiItems.text("Room", net.kyori.adventure.text.format.NamedTextColor.WHITE);
        var lore = GuiItems.lore(List.of("Neutral lore"));

        assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE, lore.getFirst().decoration(TextDecoration.ITALIC));
    }

    @Test
    void wrapsLongLoreLinesBeforeApplyingLineLimit() {
        var lore = GuiItems.lore(List.of("Invalid: stairwell door door_2 cannot use door template cellar because the gateway faces DOWN but the slot faces UP and door templates only rotate around Y."));

        assertTrue(lore.size() > 1);
        assertTrue(lore.size() <= 8);
        for (var line : lore) {
            assertTrue(PlainTextComponentSerializer.plainText().serialize(line).length() <= 42);
        }
    }
}
