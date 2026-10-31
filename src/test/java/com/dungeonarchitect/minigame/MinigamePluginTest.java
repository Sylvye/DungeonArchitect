package com.dungeonarchitect.minigame;

import com.dungeonarchitect.DungeonArchitectPlugin;
import com.dungeonarchitect.api.DungeonArchitectAPI;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

final class MinigamePluginTest {
    private ServerMock server;
    @BeforeEach void setup() { server = MockBukkit.mock(); server.addSimpleWorld("world"); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void pluginRegistersServiceAndAdminMenusWithoutDungeonItems() {
        var plugin = MockBukkit.load(DungeonArchitectPlugin.class);
        var admin = server.addPlayer(); admin.setOp(true);
        assertTrue(admin.performCommand("da")); admin.simulateInventoryClick(24);
        assertEquals("Minigames", admin.getOpenInventory().getTitle());
        admin.simulateInventoryClick(20); server.getScheduler().performTicks(1);
        assertTrue(admin.getOpenInventory().getTitle().startsWith("Minigame presets"));
        assertTrue(admin.performCommand("da minigame preset create smoke"));
        assertTrue(admin.performCommand("da minigame create smoke 12"));
        var api = Bukkit.getServicesManager().load(DungeonArchitectAPI.class);
        assertNotNull(api); assertSame(api, plugin.api()); assertEquals(1, api.minigames().runs().size());
        assertEquals("smoke", api.minigames().runs().iterator().next().preset().id());
    }
    @Test void participantCommandsDoNotRequireAdminPermission() {
        var plugin = MockBukkit.load(DungeonArchitectPlugin.class);
        var player = server.addPlayer();
        assertFalse(player.hasPermission("dungeonarchitect.admin")); assertTrue(player.hasPermission("dungeonarchitect.play"));
        assertTrue(player.performCommand("dplay exit"));
        assertNotNull(player.nextMessage());
        assertTrue(player.performCommand("dplay kit"));
        assertTrue(plugin.api().minigames().runs().isEmpty());
    }
}
