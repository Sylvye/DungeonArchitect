package com.dungeonarchitect.gui;

import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.loot.LootTableRegistry;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.template.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

final class FeatureDeletionMenuTest {
    @TempDir Path directory;
    PlayerMock player;
    MenuManager menus;
    FeatureTemplateRegistry features;
    AssetRenameCoordinator assets;

    @BeforeEach void setup() throws Exception {
        var server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin(); player = server.addPlayer(); player.setOp(true);
        save("child", List.of());
        features = new FeatureTemplateRegistry(directory.resolve("features"), null);
        var doors = new DoorTemplateRegistry(directory.resolve("doors"), null, features);
        var rooms = new RoomTemplateRegistry(directory.resolve("rooms"), null, features, doors);
        assets = new AssetRenameCoordinator(rooms, features, doors); assets.reloadAll();
        menus = new MenuManager(plugin, mock(AuthoringManager.class), rooms, features, doors,
            new LootTableRegistry(directory.resolve("loot")), mock(DungeonManager.class), new ChatPromptManager(plugin),
            assets, new TagCatalog(directory.resolve("tags.yml")), assets::reloadAll);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void save(String id, List<RoomFeatureSlot> slots) throws Exception {
        Path target = directory.resolve("features").resolve(id); Files.createDirectories(target);
        Files.writeString(target.resolve("feature.nbt"), "fixture");
        FeatureTemplateIO.save(new FeatureTemplate(id, new IntVector3(1, 1, 1), Set.of(), List.of(), slots, Map.of(), target.resolve("feature.nbt")), target);
    }
    private void addParent() throws Exception {
        save("parent", List.of(new RoomFeatureSlot("nested", IntVector3.ZERO, new IntVector3(1, 1, 1),
            Direction3.NORTH, List.of(new FeatureSlotEntry("child", 1)))));
        assets.reloadAll();
    }
    private void click(int slot) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot,
            ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertDoesNotThrow(() -> menus.onClick(event)); assertTrue(event.isCancelled());
    }
    @Test void referencedFeatureShowsItsOwnerInsteadOfOfferingDeletion() throws Exception {
        addParent(); menus.openFeature(player, "child"); click(40);
        assertEquals(Material.BARRIER, player.getOpenInventory().getTopInventory().getItem(11).getType());
        click(11);
        assertTrue(player.nextMessage().contains("feature parent"));
        assertTrue(Files.exists(directory.resolve("features/child/feature.nbt")));
    }
    @Test void unreferencedFeatureDeletesSuccessfully() {
        menus.openFeature(player, "child"); click(40);
        assertEquals(Material.RED_CONCRETE, player.getOpenInventory().getTopInventory().getItem(11).getType());
        click(11);
        assertFalse(Files.exists(directory.resolve("features/child")));
        assertTrue(features.getVisible("child").isEmpty());
        assertEquals("Deleted feature child", player.nextMessage());
    }
    @Test void referenceAddedAfterConfirmationOpenedIsHandledWithoutAnEventError() throws Exception {
        menus.openFeature(player, "child"); click(40); addParent(); click(11);
        assertTrue(player.nextMessage().contains("referenced by feature parent"));
        assertTrue(Files.exists(directory.resolve("features/child/feature.nbt")));
        assertEquals("Feature: child", player.getOpenInventory().getTitle());
    }
}
