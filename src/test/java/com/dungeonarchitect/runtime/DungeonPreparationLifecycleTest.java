package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.generation.*;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class DungeonPreparationLifecycleTest {
    ServerMock server;
    DungeonManager manager;
    World world;
    Block block;
    RoomStructurePlacer placer;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); server.addSimpleWorld("world");
        Plugin plugin = MockBukkit.createMockPlugin();
        RoomTemplateRegistry templates = mock(RoomTemplateRegistry.class);
        var generator = mock(DeterministicDungeonGenerator.class);
        var worlds = mock(DungeonWorldManager.class); placer = mock(RoomStructurePlacer.class);
        RoomTemplate room = new RoomTemplate("root", RoomCategory.START, 1, Set.of(), new IntVector3(3, 3, 3), new IntVector3(1, 1, 1), List.of(), List.of(), List.of(), Path.of("root.nbt"));
        when(templates.all()).thenReturn(List.of(room)); when(templates.get("root")).thenReturn(Optional.of(room));
        DungeonGraph graph = new DungeonGraph(List.of(new DungeonNode(0, "root", RoomCategory.START, 0,
            new RoomTransform(new IntVector3(0, 80, 0), com.dungeonarchitect.domain.Rotation.NONE, room.size()))), List.of());
        when(generator.generate(anyCollection(), any())).thenReturn(DungeonGenerationResult.success(graph));
        world = mock(World.class); block = mock(Block.class); when(world.getName()).thenReturn("world"); when(world.getEntities()).thenReturn(List.of());
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getChunkAtAsyncUrgently(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(worlds.createWorld(any())).thenReturn(world);
        manager = new DungeonManager(plugin, templates, generator, worlds, placer);
    }
    @AfterEach void cleanup() { manager.cancelPreparations(); MockBukkit.unmock(); }
    private void finishAsync() {
        server.getScheduler().performTicks(1); server.getScheduler().waitAsyncTasksFinished(); server.getScheduler().performTicks(4);
    }
    @Test void preparationReachesReadyWithoutEntryOrMembership() {
        var player = server.addPlayer(); Location before = player.getLocation();
        var future = manager.prepareDungeonAsync(new DungeonRequest(1, 12, Set.of(player.getUniqueId()))); finishAsync();
        DungeonInstance instance = future.join(); assertEquals(DungeonState.READY, instance.state());
        assertEquals(before, player.getLocation()); assertTrue(manager.getDungeon(player).isEmpty());
        manager.attachPlayer(instance, player); assertEquals(instance.id(), manager.getDungeon(player).orElseThrow().id());
    }
    @Test void classicAsyncGenerationStillEntersAndActivates() {
        var player = server.addPlayer();
        var future = manager.createDungeonAsync(new DungeonRequest(1, 12, Set.of(player.getUniqueId()))); finishAsync();
        assertEquals(DungeonState.ACTIVE, future.join().state()); assertTrue(manager.getDungeon(player).isPresent());
        assertEquals(81, player.getLocation().getY());
    }
    @Test void retiredDungeonCannotDetachPlayerFromAnotherInstance() {
        var player = server.addPlayer();
        var first = manager.createDungeon(new DungeonRequest(1, 1, Set.of(player.getUniqueId())));
        manager.detachPlayer(player.getUniqueId());
        var second = manager.createDungeon(new DungeonRequest(1, 2, Set.of(player.getUniqueId()))); Location location = player.getLocation();
        manager.destroyDungeon(first.id());
        assertEquals(second.id(), manager.getDungeon(player).orElseThrow().id()); assertEquals(location, player.getLocation());
    }
    @Test void placementFailureRemovesBlocksAndDoesNotEnterPlayers() throws Exception {
        var player = server.addPlayer();
        doThrow(new java.io.IOException("broken structure")).when(placer).place(any(), any(), any(), anyLong(), anyInt(), anyList());
        var future = manager.prepareDungeonAsync(new DungeonRequest(1, 12, Set.of(player.getUniqueId()))); finishAsync();
        assertTrue(future.isCompletedExceptionally()); assertTrue(manager.instances().isEmpty()); assertTrue(manager.getDungeon(player).isEmpty());
        verify(block, atLeastOnce()).setType(Material.AIR, false);
    }
    @Test void cancelledPreloadDoesNotPlaceStructuresLater() throws Exception {
        CompletableFuture<Chunk> loading = new CompletableFuture<>(); when(world.getChunkAtAsyncUrgently(anyInt(), anyInt())).thenReturn(loading);
        var future = manager.prepareDungeonAsync(new DungeonRequest(1, 12, Set.of())); finishAsync();
        assertFalse(future.isDone()); future.cancel(false); loading.complete(mock(Chunk.class)); server.getScheduler().performTicks(3);
        verify(placer, never()).place(any(), any(), any(), anyLong(), anyInt(), anyList());
        assertTrue(manager.instances().isEmpty()); verify(block, atLeastOnce()).setType(Material.AIR, false);
    }
}
