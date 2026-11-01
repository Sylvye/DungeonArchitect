package com.dungeonarchitect.minigame;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.gui.*;
import com.dungeonarchitect.runtime.*;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class MinigameSetupMenuTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock admin, other;
    MinigameService games;
    MinigameMenus menus;
    DungeonManager dungeons;
    RoomTemplateRegistry templates;
    org.bukkit.plugin.Plugin plugin;
    final MinigamePreset preset = new MinigamePreset("test", "root", List.of("ordinary"), List.of("spawn"), 16, 4, 1.25, List.of());

    @BeforeEach void setup() {
        server = MockBukkit.mock(); server.addSimpleWorld("world"); admin = server.addPlayer("Admin"); admin.setOp(true); other = server.addPlayer("Other");
        plugin = spy(MockBukkit.createMockPlugin()); doReturn(directory.toFile()).when(plugin).getDataFolder();
        dungeons = mock(DungeonManager.class); templates = mock(RoomTemplateRegistry.class);
        when(dungeons.getDungeon(any(org.bukkit.entity.Player.class))).thenReturn(Optional.empty());
        games = new MinigameService(plugin, dungeons, templates); games.presets().create(preset);
        var prompts = new ChatPromptManager(plugin); menus = new MinigameMenus(plugin, games, templates, prompts, p -> { });
        server.getPluginManager().registerEvents(prompts, plugin); server.getPluginManager().registerEvents(menus, plugin);
    }
    @AfterEach void cleanup() {
        menus.close(); games.close(); org.bukkit.event.HandlerList.unregisterAll(plugin);
        server.getScheduler().cancelTasks(plugin); MockBukkit.unmock();
    }
    private void click(int slot) { click(slot, ClickType.LEFT); }
    private void click(int slot, ClickType type) {
        server.getPluginManager().callEvent(new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL));
        server.getScheduler().performTicks(1);
    }
    private void chat(String text) {
        server.getPluginManager().callEvent(new AsyncPlayerChatEvent(false, admin, text, new HashSet<>())); server.getScheduler().performTicks(1);
    }
    private void edit() { menus.open(admin); click(20); click(0); }
    private void match() { edit(); click(42); }
    private static String name(ItemStack item) { return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()); }
    private List<String> lore(int slot) { return admin.getOpenInventory().getTopInventory().getItem(slot).getItemMeta().lore().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList(); }
    @Test void closingOrLeavingMatchCreatesNoRegisteredDrafts() {
        for (int i = 0; i < 3; i++) { match(); assertEquals("Match Setup", admin.getOpenInventory().getTitle()); assertTrue(games.runs().isEmpty()); admin.closeInventory(); server.getScheduler().performTicks(1); }
        match(); click(10); click(49); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(4)).contains("Temporary"));
        click(49); assertTrue(games.runs().isEmpty());
    }
    @Test void presetEditsAreNotSavedUntilSaveAndInternalPromptsPreserveThem() {
        edit(); click(20); chat("40"); assertEquals(16, games.presets().require("test").roomCount());
        click(12); click(49); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(20)).endsWith("40"));
        click(40); assertEquals(40, games.presets().require("test").roomCount());
        click(20); chat("41"); admin.closeInventory(); server.getScheduler().performTicks(1); edit();
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(20)).endsWith("40"));
    }
    @Test void newAndDuplicatedPresetsStayTemporaryUntilSaved() {
        menus.open(admin); click(20); click(48); chat("new_game");
        assertEquals(1, games.presets().all().size()); click(40); assertEquals(2, games.presets().all().size());
        click(38); chat("copy_game"); assertEquals(2, games.presets().all().size());
        click(49); assertEquals(2, games.presets().all().size());
    }
    @Test void bulkSelectionUsesHeadsAndBalancesAndFailureLeavesSelectionUntouched() {
        match(); click(10); assertEquals(Material.PLAYER_HEAD, admin.getOpenInventory().getTopInventory().getItem(0).getType());
        SkullMeta skull = (SkullMeta) admin.getOpenInventory().getTopInventory().getItem(0).getItemMeta(); assertEquals(admin.getUniqueId(), skull.getPlayerProfile().getId());
        click(46); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(0)).startsWith("✓"));
        assertTrue(lore(0).getFirst().contains("Team")); assertNotEquals(lore(0).getFirst(), lore(1).getFirst());
        for (int i = 0; i < 7; i++) server.addPlayer("Extra" + i);
        click(46); click(49); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("2"));
        click(10); click(47); click(46); click(49); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("9"));
    }
    @Test void bulkSelectionIncludesEligiblePlayersBeyondTheVisiblePage() {
        for (int i = 0; i < 46; i++) {
            var player = server.addPlayer(String.format("Player%02d", i));
            if (i < 44) when(dungeons.getDungeon(player)).thenReturn(Optional.of(mock(DungeonInstance.class)));
        }
        match(); click(10); click(46); click(49);
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("4"));
        click(10); click(53);
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(1)).startsWith("✓"));
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(2)).startsWith("✓"));
    }
    @Test void failedValidationKeepsTheSetupButCreatesNoRun() {
        match(); click(10); click(46); click(49); click(29);
        assertTrue(games.runs().isEmpty()); assertEquals("Match Setup", admin.getOpenInventory().getTitle());
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("2"));
    }
    @Test void queuedDoubleClicksCannotOpenDuplicateSetupsOrCreateRuns() {
        edit(); var event = new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER, 42, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event); server.getPluginManager().callEvent(event); server.getScheduler().performTicks(1);
        assertEquals("Match Setup", admin.getOpenInventory().getTitle()); assertTrue(games.runs().isEmpty());
    }
    @Test void cancellingNewPresetAndOpeningUnrelatedMenusDiscardsTemporaryEdits() {
        menus.open(admin); click(20); click(48); chat("cancel");
        assertTrue(admin.getOpenInventory().getTitle().startsWith("Minigame presets")); assertEquals(1, games.presets().all().size());
        edit(); click(20); chat("50"); admin.openInventory(Bukkit.createInventory(null, 27)); server.getScheduler().performTicks(1);
        edit(); assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(20)).endsWith("16"));
    }
    @Test void disconnectDiscardsTemporarySetup() {
        match(); click(10); click(46); admin.disconnect(); server.getScheduler().performTicks(1);
        assertTrue(games.runs().isEmpty()); admin.reconnect(); match();
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("0"));
    }
    @Test void bulkSelectionExcludesDeadAndRecoveryPendingPlayers() {
        var dead = server.addPlayer("Dead"); dead.setHealth(0);
        var recovering = server.addPlayer("Recovering"); new RecoveryStore(directory.resolve("minigames/recovery")).save(recovering);
        match(); click(10); click(46); click(49);
        assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("2"));
    }
    @Test void doublePrepareClicksRegisterOnlyOneRun() {
        for (var room : List.of(room("root", RoomCategory.START), room("ordinary", RoomCategory.GENERIC), room("spawn", RoomCategory.TEAM_SPAWN))) when(templates.get(room.id())).thenReturn(Optional.of(room));
        CompletableFuture<DungeonInstance> result = new CompletableFuture<>(); when(dungeons.prepareDungeonAsync(any())).thenReturn(result);
        match(); click(10); click(46); click(49);
        var event = new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER, 29, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event); server.getPluginManager().callEvent(event); server.getScheduler().performTicks(1);
        assertEquals(1, games.runs().size()); verify(dungeons, times(1)).prepareDungeonAsync(any());
        admin.closeInventory(); server.getScheduler().performTicks(1); assertEquals(1, games.runs().size());
        result.completeExceptionally(new IllegalStateException("test failure")); assertTrue(games.runs().isEmpty());
    }
    @Test void spawnCommandOpensSharedMarkersThroughTheMenuIntegration() {
        var commands = new MinigameCommand(games, menus); var editor = mock(MenuManager.class); menus.markerMenus(editor);
        commands.executeAdmin(admin, new String[] { "spawns", "spawn" });
        verify(editor).openRoomSpawnMarkers(eq(admin), eq("spawn"), any());
    }
    @Test void sharedMarkerNavigationPreservesRosterAndClosingItDiscardsSetup() {
        match(); click(10); click(46); click(49);
        MenuManager markerMenus=mock(MenuManager.class); menus.markerMenus(markerMenus);
        Inventory shared=Bukkit.createInventory(new MenuHolder("da:markers:room:spawn:0"),54,"Spawn Areas");
        when(markerMenus.ownsMarkerNavigation(eq(admin),any())).thenAnswer(invocation -> invocation.getArgument(1)==shared);
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<org.bukkit.entity.Player>> done=new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation->{done.set(invocation.getArgument(2));admin.openInventory(shared);return null;}).when(markerMenus).openRoomSpawnMarkers(eq(admin),eq("spawn"),any());
        menus.spawnPoints(admin,"spawn");server.getScheduler().performTicks(1);done.get().accept(admin);server.getScheduler().performTicks(1);
        assertEquals("Match Setup",admin.getOpenInventory().getTitle());assertTrue(name(admin.getOpenInventory().getTopInventory().getItem(10)).endsWith("2"));
        menus.spawnPoints(admin,"spawn");server.getScheduler().performTicks(1);admin.closeInventory();server.getScheduler().performTicks(1);verify(markerMenus).closeMarkerNavigation(admin);assertTrue(games.runs().isEmpty());
    }
    @Test void authoringWorldAndMarkerPromptsAreInternalMatchNavigation() {
        match();MenuManager markerMenus=mock(MenuManager.class);menus.markerMenus(markerMenus);
        Inventory shared=Bukkit.createInventory(new MenuHolder("da:markers:room:spawn:0"),54,"Spawn Areas");
        when(markerMenus.ownsMarkerNavigation(eq(admin),any())).thenAnswer(invocation->invocation.getArgument(1)==shared);
        when(markerMenus.retainsMarkerNavigation(admin)).thenReturn(true);
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<org.bukkit.entity.Player>> done=new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation->{done.set(invocation.getArgument(2));admin.openInventory(shared);return null;}).when(markerMenus).openRoomSpawnMarkers(eq(admin),eq("spawn"),any());
        menus.spawnPoints(admin,"spawn");admin.closeInventory();server.getScheduler().performTicks(2);verify(markerMenus,never()).closeMarkerNavigation(admin);
        done.get().accept(admin);server.getScheduler().performTicks(1);assertEquals("Match Setup",admin.getOpenInventory().getTitle());
    }
    @Test void oneOffSettingsNeedNoSavedPresetAndRegisterOnlyAfterPreflight() {
        RoomTemplate root = room("root", RoomCategory.START), ordinary = room("ordinary", RoomCategory.GENERIC), spawn = room("spawn", RoomCategory.TEAM_SPAWN);
        for (var room : List.of(root, ordinary, spawn)) when(templates.get(room.id())).thenReturn(Optional.of(room));
        var result = new CompletableFuture<DungeonInstance>(); when(dungeons.prepareDungeonAsync(any())).thenReturn(result);
        MatchSetup setup = new MatchSetup(preset.copyAs("unsaved"), 3); setup.assign(admin.getUniqueId(), 1); setup.assign(other.getUniqueId(), 2);
        var future = games.prepare(setup); assertEquals(1, games.runs().size()); assertEquals(RunState.GENERATING, games.runs().iterator().next().state());
        assertEquals(1, games.presets().all().size()); result.completeExceptionally(new IllegalStateException("test failure"));
        assertTrue(future.isCompletedExceptionally()); assertTrue(games.runs().isEmpty()); assertTrue(games.getRun(admin).isEmpty());
    }
    @Test void capacityErrorHasAnActionableShortcutAndNoRunIsRegistered() {
        for (var room : List.of(room("root", RoomCategory.START), room("ordinary", RoomCategory.GENERIC), room("spawn", RoomCategory.TEAM_SPAWN))) when(templates.get(room.id())).thenReturn(Optional.of(room));
        var spawn = room("spawn", RoomCategory.TEAM_SPAWN);
        when(templates.get("spawn")).thenReturn(Optional.of(new RoomTemplate(spawn.id(), spawn.category(), 1, Set.of(), spawn.size(), null, List.of(), List.of(), List.of(), spawn.structureFile())));
        MatchSetup setup = new MatchSetup(preset, 1); setup.assign(admin.getUniqueId(), 1); setup.assign(other.getUniqueId(), 2);
        var error = assertThrows(SpawnAreaException.class, () -> games.prepare(setup));
        assertTrue(error.getMessage().contains("has no usable Spawn areas")); assertTrue(error.getMessage().contains("entering group needs 1"));
        assertEquals("/da minigame spawns spawn", error.guidance().children().getFirst().clickEvent().value()); assertTrue(games.runs().isEmpty());
    }
    @Test void presetEditorShowsReversedCategoryErrorsWithoutChangingSavedSettings() {
        for (var room : List.of(room("root",RoomCategory.START),room("spawn",RoomCategory.TEAM_SPAWN))) when(templates.getVisible(room.id())).thenReturn(Optional.of(room));
        MinigamePreset invalid=preset.with("root","spawn").with("spawns","root");games.presets().save(invalid);
        edit();assertTrue(lore(10).stream().anyMatch(line->line.contains("is TEAM_SPAWN")&&line.contains("requires START")));
        assertTrue(lore(14).stream().anyMatch(line->line.contains("is START")&&line.contains("require TEAM_SPAWN")));
        assertEquals(invalid,games.presets().require("test"));assertTrue(games.runs().isEmpty());
    }
    private RoomTemplate room(String id, RoomCategory category) {
        return new RoomTemplate(id, category, 1, Set.of(), new IntVector3(5, 5, 5), null, List.of(), List.of(new RoomMarker("one", "player_spawn", new IntVector3(2, 1, 2))), List.of(), Path.of(id + ".nbt"));
    }
}
