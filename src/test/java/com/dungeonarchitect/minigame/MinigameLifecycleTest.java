package com.dungeonarchitect.minigame;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.runtime.*;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonitems.api.DungeonItemsAPI;
import com.dungeonitems.model.KitDefinition;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class MinigameLifecycleTest {
    @TempDir Path directory;
    ServerMock server;
    org.bukkit.plugin.Plugin plugin;
    DungeonManager dungeons;
    RoomTemplateRegistry templates;
    MinigameService games;
    PlayerMock first, second;
    World world;
    CompletableFuture<DungeonInstance> preparation;

    @BeforeEach void setup() {
        server = MockBukkit.mock(); world = server.addSimpleWorld("world");
        plugin = spy(MockBukkit.createMockPlugin("MinigameTests")); doReturn(directory.toFile()).when(plugin).getDataFolder();
        first = addPlayer("First"); second = addPlayer("Second");
        dungeons = mock(DungeonManager.class); templates = mock(RoomTemplateRegistry.class);
        when(dungeons.getDungeon(any(org.bukkit.entity.Player.class))).thenReturn(Optional.empty());
        RoomTemplate root = room("root", RoomCategory.START), ordinary = room("ordinary", RoomCategory.GENERIC), spawn = room("spawn", RoomCategory.TEAM_SPAWN);
        for (RoomTemplate template : List.of(root, ordinary, spawn)) when(templates.get(template.id())).thenReturn(Optional.of(template));
        preparation = new CompletableFuture<>(); when(dungeons.prepareDungeonAsync(any())).thenReturn(preparation);
        games = new MinigameService(plugin, dungeons, templates, location -> location.clone().add(0, -1, 0).getBlock().getType() == Material.STONE);
        server.getPluginManager().registerEvents(new MinigameListener(plugin, games), plugin);
        var menus = new MinigameMenus(plugin, games, templates, new com.dungeonarchitect.gui.ChatPromptManager(plugin), player -> { });
        server.getPluginManager().registerEvents(menus, plugin);
        games.presets().create(new MinigamePreset("test", "root", List.of("ordinary"), List.of("spawn"), 12, 4, 1.25, List.of()));
    }
    @AfterEach void cleanup() { if (games != null) games.close(); MockBukkit.unmock(); }
    private PlayerMock addPlayer(String name) {
        PlayerMock player = new PlayerMock(server, name) { @Override public void saveData() { } };
        server.addPlayer(player); return player;
    }
    private RoomTemplate room(String id, RoomCategory category) {
        return new RoomTemplate(id, category, 1, Set.of(), new IntVector3(7, 5, 7), new IntVector3(2, 1, 2), List.of(),
            List.of(new RoomMarker("one", "player_spawn", new IntVector3(2, 1, 2)), new RoomMarker("two", "player_spawn", new IntVector3(4, 1, 2))), List.of(), Path.of(id + ".nbt"));
    }
    private DungeonInstance dungeon(int offset) {
        List<DungeonNode> nodes = List.of(
            new DungeonNode(0, "root", RoomCategory.START, 0, new RoomTransform(new IntVector3(offset, 80, 0), Rotation.NONE, new IntVector3(7, 5, 7))),
            new DungeonNode(1, "spawn", RoomCategory.TEAM_SPAWN, 1, new RoomTransform(new IntVector3(offset + 10, 80, 0), Rotation.NONE, new IntVector3(7, 5, 7))),
            new DungeonNode(2, "spawn", RoomCategory.TEAM_SPAWN, 1, new RoomTransform(new IntVector3(offset + 20, 80, 0), Rotation.CLOCKWISE_90, new IntVector3(7, 5, 7))));
        List<RoomInstance> rooms = nodes.stream().map(n -> new RoomInstance(n, room(n.templateId(), n.category()))).toList();
        for (RoomInstance room : rooms) for (IntVector3 local : MinigameService.spawnMarkers(room.template())) {
            IntVector3 point = room.node().transform().transformLocal(local); world.getBlockAt(point.x(), point.y() - 1, point.z()).setType(Material.STONE);
        }
        return new DungeonInstance(UUID.randomUUID(), 42, new DungeonGraph(nodes, List.of(new DungeonEdge(0, "a", 1, "b"), new DungeonEdge(0, "c", 2, "d"))), "world", Set.of(first.getUniqueId(), second.getUniqueId()), rooms, DungeonState.READY);
    }
    private MinigameRun draft(List<String> kits) {
        MinigameRun run = games.createRun("test", 42); games.assign(run.id(), first.getUniqueId(), 1); games.assign(run.id(), second.getUniqueId(), 2); games.allowedKits(run.id(), kits); return run;
    }
    private MinigameRun launch(List<String> kits) {
        MinigameRun run = draft(kits); var future = games.prepare(run.id()); games.start(run.id()); preparation.complete(dungeon(0));
        if (run.message().contains("Not implemented")) future.join();
        return run;
    }
    private void provider() {
        var itemsPlugin = MockBukkit.createMockPlugin("DungeonItems"); DungeonItemsAPI api = mock(DungeonItemsAPI.class);
        for (String id : List.of("sword", "bow")) {
            KitDefinition kit = new KitDefinition(id); kit.slots().put("inventory.0", new ItemStack(id.equals("sword") ? Material.IRON_SWORD : Material.BOW));
            when(api.kit(id)).thenReturn(Optional.of(kit));
        }
        when(api.kits()).thenReturn(List.of("sword", "bow")); server.getServicesManager().register(DungeonItemsAPI.class, api, itemsPlugin, ServicePriority.Normal);
    }
    @Test void noKitLaunchCountsDownTogetherAndRestoresOriginalState() {
        first.getInventory().setItem(3, new ItemStack(Material.DIAMOND, 7)); first.getInventory().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        first.setLevel(8); first.setExp(.4f); first.setHealth(13); first.setFoodLevel(12); first.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        Location original = first.getLocation();
        MinigameRun run = launch(List.of());
        assertEquals(RunState.COUNTDOWN, run.state(), run.message()); assertNull(first.getInventory().getItem(3)); assertTrue(run.contains(first.getLocation()));
        server.getScheduler().performTicks(100); assertEquals(RunState.RUNNING, run.state());
        assertTrue(games.exit(first)); assertFalse(games.exit(first));
        assertEquals(7, first.getInventory().getItem(3).getAmount()); assertEquals(Material.GOLDEN_HELMET, first.getInventory().getHelmet().getType());
        assertEquals(8, first.getLevel()); assertEquals(.4f, first.getExp(), .001); assertEquals(13, first.getHealth()); assertEquals(12, first.getFoodLevel());
        assertTrue(first.hasPotionEffect(PotionEffectType.SPEED)); assertEquals(original, first.getLocation());
        assertTrue(first.isOnline()); assertFalse(java.nio.file.Files.exists(directory.resolve("minigames/recovery/" + first.getUniqueId() + ".yml")));
        games.end(run.id(), "test"); assertEquals(RunState.ENDED, run.state()); verify(dungeons).destroyDungeon(run.dungeon().orElseThrow().id());
    }
    @Test void oneKitIsAutomaticAndDetachedFromProvider() {
        provider(); MinigameRun run = launch(List.of("sword"));
        assertEquals(RunState.COUNTDOWN, run.state(), run.message()); assertEquals(Material.IRON_SWORD, first.getInventory().getItem(0).getType());
        first.getInventory().getItem(0).setAmount(2); assertEquals(1, second.getInventory().getItem(0).getAmount());
    }
    @Test void multipleKitsTimeoutAfterTenSecondsAndDoNotEquipTwice() {
        provider(); MinigameRun run = launch(List.of("sword", "bow"));
        assertEquals(RunState.PREPARING, run.state(), run.message()); games.chooseKit(first, "bow");
        server.getScheduler().performTicks(180); assertEquals(RunState.PREPARING, run.state(), run.message());
        server.getScheduler().performTicks(20); assertEquals(RunState.COUNTDOWN, run.state(), run.message());
        assertEquals(Material.BOW, first.getInventory().getItem(0).getType()); assertNotNull(second.getInventory().getItem(0));
        assertThrows(IllegalStateException.class, () -> games.chooseKit(first, "sword"));
    }
    @Test void allChoicesFinishSelectionEarlyAndRejectUnlistedKits() {
        provider(); MinigameRun run = launch(List.of("sword", "bow"));
        assertThrows(IllegalArgumentException.class, () -> games.chooseKit(first, "missing"));
        games.chooseKit(first, "random"); games.chooseKit(second, "bow"); assertEquals(RunState.COUNTDOWN, run.state(), run.message());
    }
    @Test void nonAdminPlayersCanChooseThroughTheProtectedKitMenu() {
        provider(); MinigameRun run = launch(List.of("sword", "bow"));
        assertFalse(first.hasPermission("dungeonarchitect.admin"));
        assertTrue(first.getOpenInventory().getTopInventory().getHolder() instanceof MinigameMenus.View);
        first.simulateInventoryClick(49); server.getScheduler().performTicks(1);
        second.simulateInventoryClick(1); server.getScheduler().performTicks(1);
        assertEquals(RunState.COUNTDOWN, run.state(), run.message());
        assertEquals(Material.BOW, second.getInventory().getItem(0).getType());
    }
    @Test void deathRespawnsAsSpectatorAndExitRestoresOriginalGear() {
        first.getInventory().setItem(2, new ItemStack(Material.EMERALD, 3));
        MinigameRun run = launch(List.of()); server.getScheduler().performTicks(100);
        first.setHealth(0); assertTrue(run.eliminated(first.getUniqueId()));
        first.respawn(); server.getScheduler().performTicks(1); assertEquals(GameMode.SPECTATOR, first.getGameMode());
        games.exit(first); assertEquals(GameMode.SURVIVAL, first.getGameMode()); assertEquals(3, first.getInventory().getItem(2).getAmount());
    }
    @Test void failedEntryRollsEveryoneBack() {
        first.getInventory().setItem(0, new ItemStack(Material.EMERALD, 5));
        Listener blocker = new Listener() {
            @EventHandler public void block(PlayerTeleportEvent event) { if (event.getPlayer() == second && event.getTo().getY() == 81) event.setCancelled(true); }
        };
        server.getPluginManager().registerEvents(blocker, plugin);
        MinigameRun run = launch(List.of()); assertEquals(RunState.ENDED, run.state());
        assertEquals(5, first.getInventory().getItem(0).getAmount()); assertTrue(games.getRun(first).isEmpty());
    }
    @Test void cancellingDuringGenerationCleansUpEventualDungeon() {
        MinigameRun run = draft(List.of()); games.prepare(run.id()); games.end(run.id(), "cancel");
        DungeonInstance instance = dungeon(0); preparation.complete(instance);
        verify(dungeons).destroyDungeon(instance.id()); assertEquals(RunState.ENDED, run.state()); assertTrue(games.getRun(first).isEmpty());
    }
    @Test void disconnectDoesNotDelayStartAndReconnectPreservesProgress() {
        provider(); MinigameRun run = launch(List.of("sword", "bow")); second.disconnect();
        games.chooseKit(first, "sword"); assertEquals(RunState.COUNTDOWN, run.state(), run.message());
        server.getScheduler().performTicks(100); assertEquals(RunState.RUNNING, run.state());
        second.reconnect(); server.getScheduler().performTicks(1); assertNotNull(second.getInventory().getItem(0));
        second.getInventory().setItem(8, new ItemStack(Material.DIAMOND, 3)); second.disconnect();
        server.getScheduler().performTicks(20); second.reconnect(); server.getScheduler().performTicks(1);
        assertEquals(3, second.getInventory().getItem(8).getAmount());
    }
    @Test void expiredGraceRestoresOriginalInventoryOnNextJoin() {
        first.getInventory().setItem(0, new ItemStack(Material.EMERALD, 9)); MinigameRun run = launch(List.of());
        first.disconnect(); server.getScheduler().performTicks(2400); assertTrue(games.getRun(first).isEmpty());
        first.reconnect(); server.getScheduler().performTicks(1); assertEquals(9, first.getInventory().getItem(0).getAmount());
        assertEquals(RunState.RUNNING, run.state());
    }
    @Test void originalRecoverySurvivesServiceRestart() {
        first.getInventory().setItem(0, new ItemStack(Material.AMETHYST_SHARD, 4)); launch(List.of()); first.disconnect();
        games.close(); games = new MinigameService(plugin, dungeons, templates);
        first.reconnect(); server.getScheduler().performTicks(1); games.join(first);
        assertEquals(4, first.getInventory().getItem(0).getAmount());
    }
    @Test void eliminationPersistsAcrossReconnectAndSpectatorsCannotLeaveFootprint() {
        MinigameRun run = launch(List.of()); server.getScheduler().performTicks(100);
        games.eliminate(run.id(), first.getUniqueId()); assertEquals(GameMode.SPECTATOR, first.getGameMode());
        first.disconnect(); first.reconnect(); server.getScheduler().performTicks(1); assertEquals(GameMode.SPECTATOR, first.getGameMode());
        assertFalse(first.teleport(new Location(world, 1000, 81, 1000))); assertTrue(run.contains(first.getLocation()));
    }
    @Test void missingKitBlocksBeforeReservingPlayers() {
        MinigameRun run = draft(List.of("missing")); assertThrows(IllegalStateException.class, () -> games.prepare(run.id()));
        assertEquals(RunState.DRAFT, run.state()); assertTrue(games.getRun(first).isEmpty());
    }
    @Test void rosterAndSettingsLockAndPreparationDoesNotMovePlayers() {
        MinigameRun run = draft(List.of()); Location original = first.getLocation();
        games.prepare(run.id());
        assertThrows(IllegalStateException.class, () -> games.assign(run.id(), first.getUniqueId(), 2));
        assertThrows(IllegalStateException.class, () -> games.allowedKits(run.id(), List.of()));
        preparation.complete(dungeon(0)); assertEquals(RunState.PREPARING, run.state()); assertEquals(original, first.getLocation());
        games.end(run.id(), "cancel"); assertEquals(original, first.getLocation());
    }
    @Test void concurrentRunsCannotDamageOrRestoreOneAnother() {
        MinigameRun one = launch(List.of());
        PlayerMock third = addPlayer("Third"), fourth = addPlayer("Fourth");
        MinigameRun two = games.createRun("test", 10); games.assign(two.id(), third.getUniqueId(), 1); games.assign(two.id(), fourth.getUniqueId(), 2);
        CompletableFuture<DungeonInstance> other = new CompletableFuture<>(); when(dungeons.prepareDungeonAsync(any())).thenReturn(other);
        games.start(two.id()); other.complete(dungeon(200)); server.getScheduler().performTicks(100);
        assertEquals(RunState.RUNNING, one.state()); assertEquals(RunState.RUNNING, two.state());
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(first).withDirectEntity(first).build();
        EntityDamageByEntityEvent attack = new EntityDamageByEntityEvent(first, third, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 3);
        server.getPluginManager().callEvent(attack); assertTrue(attack.isCancelled());
        Location thirdLocation = third.getLocation(); third.getInventory().setItem(9, new ItemStack(Material.GOLD_INGOT, 2));
        games.end(one.id(), "test"); assertEquals(RunState.RUNNING, two.state()); assertEquals(thirdLocation, third.getLocation());
        assertEquals(2, third.getInventory().getItem(9).getAmount()); assertEquals(two.id(), games.getRun(third).orElseThrow().id());
    }
    @Test void preparationFreezesMovementAndOnlyEnemyCombatIsAllowedAfterRelease() {
        MinigameRun run = launch(List.of());
        Location location = first.getLocation();
        var move = new org.bukkit.event.player.PlayerMoveEvent(first, location, location.clone().add(1, 0, 0));
        server.getPluginManager().callEvent(move); assertEquals(location, move.getTo());
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(first).withDirectEntity(first).build();
        var before = new EntityDamageByEntityEvent(first, second, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 3);
        server.getPluginManager().callEvent(before); assertTrue(before.isCancelled());
        server.getScheduler().performTicks(100);
        var after = new EntityDamageByEntityEvent(first, second, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 3);
        server.getPluginManager().callEvent(after); assertFalse(after.isCancelled());
        run.teams.put(second.getUniqueId(), 1);
        var friendly = new EntityDamageByEntityEvent(first, second, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 3);
        server.getPluginManager().callEvent(friendly); assertTrue(friendly.isCancelled());
    }
}
