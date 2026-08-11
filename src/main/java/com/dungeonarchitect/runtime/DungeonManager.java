package com.dungeonarchitect.runtime;

import com.dungeonarchitect.api.event.DungeonCreatedEvent;
import com.dungeonarchitect.api.event.DungeonDestroyedEvent;
import com.dungeonarchitect.api.event.DungeonReadyEvent;
import com.dungeonarchitect.api.event.RoomEnteredEvent;
import com.dungeonarchitect.api.event.RoomStateChangeEvent;
import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonState;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomState;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.generation.DeterministicDungeonGenerator;
import com.dungeonarchitect.generation.DungeonGenerationRequest;
import com.dungeonarchitect.generation.DungeonGenerationResult;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DungeonManager {
    private static final int DUNGEON_REGION_SPACING = 2048;
    private final Plugin plugin;
    private final RoomTemplateRegistry templateRegistry;
    private final DeterministicDungeonGenerator generator;
    private final DungeonWorldManager worldManager;
    private final RoomStructurePlacer structurePlacer;
    private final long placementTimeBudgetNanos;
    private final Map<UUID, DungeonInstance> instances = new HashMap<>();
    private final Map<UUID, UUID> playerInstances = new HashMap<>();
    private final Map<UUID, Integer> playerRooms = new HashMap<>();
    private final Map<Integer, UUID> aliases = new HashMap<>();
    private int nextAlias = 1;
    private int nextRegion = 0;

    public DungeonManager(Plugin plugin, RoomTemplateRegistry templateRegistry, DeterministicDungeonGenerator generator, DungeonWorldManager worldManager, RoomStructurePlacer structurePlacer) {
        this(plugin, templateRegistry, generator, worldManager, structurePlacer, 8L);
    }

    public DungeonManager(Plugin plugin, RoomTemplateRegistry templateRegistry, DeterministicDungeonGenerator generator, DungeonWorldManager worldManager, RoomStructurePlacer structurePlacer, long placementTimeBudgetMillis) {
        this.plugin = plugin;
        this.templateRegistry = templateRegistry;
        this.generator = generator;
        this.worldManager = worldManager;
        this.structurePlacer = structurePlacer;
        this.placementTimeBudgetNanos = TimeUnit.MILLISECONDS.toNanos(placementTimeBudgetMillis);
    }

    public DungeonInstance createDungeon(DungeonRequest request) {
        UUID id = UUID.randomUUID();
        DungeonGenerationResult result = generator.generate(templateRegistry.all(), new DungeonGenerationRequest(request.roomCount(), request.seed()));
        if (!result.successful()) {
            throw new IllegalStateException(String.join("; ", result.errors()));
        }
        DungeonGraph graph = translateGraph(result.graph(), reserveDungeonOffset());
        World world = worldManager.createWorld(id);
        DungeonInstance preparing = new DungeonInstance(id, request.seed(), graph, world.getName(), request.playerIds(), List.of(), DungeonState.GENERATING);
        Bukkit.getPluginManager().callEvent(new DungeonCreatedEvent(preparing));

        List<RoomInstance> rooms = new ArrayList<>();
        Map<Integer, List<DungeonEdge>> edgesByNode = indexEdges(graph);
        try {
            for (DungeonNode node : graph.nodes()) {
                RoomTemplate template = templateRegistry.get(node.templateId())
                    .orElseThrow(() -> new IllegalStateException("Template disappeared during generation: " + node.templateId()));
                plugin.getLogger().fine(() -> "Placing dungeon " + id + " node=" + node.index()
                    + " template=" + template.id()
                    + " origin=" + node.transform().origin()
                    + " pasteOrigin=" + RoomStructurePlacer.pasteOrigin(node.transform())
                    + " rotation=" + node.transform().rotation()
                    + " metadataSize=" + template.size()
                    + " bounds=" + node.transform().transformedBounds());
                structurePlacer.place(world, template, node.transform(), request.seed(), node.index(), edgesByNode.getOrDefault(node.index(), List.of()));
                rooms.add(new RoomInstance(node, template));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to place dungeon structures: " + ex.getMessage(), ex);
        }

        DungeonInstance instance = new DungeonInstance(id, request.seed(), graph, world.getName(), request.playerIds(), rooms, DungeonState.READY);
        instances.put(id, instance);
        aliases.put(nextAlias++, id);
        for (UUID playerId : request.playerIds()) {
            playerInstances.put(playerId, id);
        }
        Bukkit.getPluginManager().callEvent(new DungeonReadyEvent(instance));
        teleportPlayersToStart(instance);
        instance.state(DungeonState.ACTIVE);
        return instance;
    }

    public CompletableFuture<DungeonInstance> createDungeonAsync(DungeonRequest request) {
        long startedAt = System.nanoTime();
        UUID id = UUID.randomUUID();
        CompletableFuture<DungeonInstance> future = new CompletableFuture<>();
        List<RoomTemplate> templates = List.copyOf(templateRegistry.all());
        Map<String, RoomTemplate> templatesById = new HashMap<>();
        for (RoomTemplate template : templates) {
            templatesById.put(template.id(), template);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DungeonGenerationResult result = generator.generate(templates, new DungeonGenerationRequest(request.roomCount(), request.seed()));
                long graphGeneratedAt = System.nanoTime();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!result.successful()) {
                        future.completeExceptionally(new IllegalStateException(String.join("; ", result.errors())));
                        return;
                    }
                    beginAsyncPlacement(id, request, translateGraph(result.graph(), reserveDungeonOffset()), templatesById, startedAt, graphGeneratedAt, future);
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> future.completeExceptionally(ex));
            }
        });
        return future;
    }

    private void beginAsyncPlacement(UUID id, DungeonRequest request, DungeonGraph graph, Map<String, RoomTemplate> templatesById, long startedAt, long graphGeneratedAt, CompletableFuture<DungeonInstance> future) {
        World world;
        try {
            world = worldManager.createWorld(id);
        } catch (RuntimeException ex) {
            future.completeExceptionally(ex);
            return;
        }
        DungeonInstance preparing = new DungeonInstance(id, request.seed(), graph, world.getName(), request.playerIds(), List.of(), DungeonState.GENERATING);
        Bukkit.getPluginManager().callEvent(new DungeonCreatedEvent(preparing));
        Set<ChunkCoordinate> chunks = chunksFor(graph);
        preloadDungeonChunks(world, chunks).whenComplete((unused, preloadError) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (preloadError != null) {
                    future.completeExceptionally(new IllegalStateException("Failed to preload dungeon chunks: " + preloadError.getMessage(), preloadError));
                    return;
                }
                try {
                    for (ChunkCoordinate chunk : chunks) {
                        world.addPluginChunkTicket(chunk.x, chunk.z, plugin);
                    }
                    long chunksLoadedAt = System.nanoTime();
                    placeAsyncBatch(new PlacementRun(id, request, graph, world, templatesById, indexEdges(graph), new ArrayList<>(), chunks, startedAt, graphGeneratedAt, chunksLoadedAt, chunksLoadedAt, 0, future));
                } catch (Exception ex) {
                    releaseChunkTickets(world, chunks);
                    future.completeExceptionally(new IllegalStateException("Failed to prepare dungeon chunks: " + ex.getMessage(), ex));
                }
            })
        );
    }

    private void placeAsyncBatch(PlacementRun run) {
        long batchStartedAt = System.nanoTime();
        int index = run.nextIndex;
        int placedThisBatch = 0;
        try {
            while (index < run.graph.nodes().size()
                && canPlaceAnotherRoom(placedThisBatch, System.nanoTime() - batchStartedAt, placementTimeBudgetNanos)) {
                DungeonNode node = run.graph.nodes().get(index);
                RoomTemplate template = Optional.ofNullable(run.templatesById.get(node.templateId()))
                    .orElseThrow(() -> new IllegalStateException("Template disappeared during generation: " + node.templateId()));
                plugin.getLogger().fine(() -> "Placing dungeon " + run.id + " node=" + node.index()
                        + " template=" + template.id()
                        + " origin=" + node.transform().origin()
                        + " pasteOrigin=" + RoomStructurePlacer.pasteOrigin(node.transform())
                        + " rotation=" + node.transform().rotation()
                        + " metadataSize=" + template.size()
                        + " bounds=" + node.transform().transformedBounds());
                structurePlacer.place(run.world, template, node.transform(), run.request.seed(), node.index(), run.edgesByNode.getOrDefault(node.index(), List.of()));
                run.rooms.add(new RoomInstance(node, template));
                index++;
                placedThisBatch++;
            }
        } catch (Exception ex) {
            releaseChunkTickets(run.world, run.chunks);
            run.future.completeExceptionally(new IllegalStateException("Failed to place dungeon room " + index + ": " + ex.getMessage(), ex));
            return;
        }

        if (index < run.graph.nodes().size()) {
            int nextIndex = index;
            Bukkit.getScheduler().runTask(plugin, () -> placeAsyncBatch(run.withNextIndex(nextIndex)));
            return;
        }

        DungeonInstance instance = new DungeonInstance(run.id, run.request.seed(), run.graph, run.world.getName(), run.request.playerIds(), run.rooms, DungeonState.READY);
        instances.put(run.id, instance);
        aliases.put(nextAlias++, run.id);
        for (UUID playerId : run.request.playerIds()) {
            playerInstances.put(playerId, run.id);
        }
        Exception activationFailure = null;
        try {
            Bukkit.getPluginManager().callEvent(new DungeonReadyEvent(instance));
            teleportPlayersToStart(instance);
            instance.state(DungeonState.ACTIVE);
            long teleportedAt = System.nanoTime();
            plugin.getLogger().info("Generated dungeon " + run.id
                + " rooms=" + run.rooms.size()
                + " graphMs=" + elapsedMillis(run.startedAt, run.graphGeneratedAt)
                + " chunkMs=" + elapsedMillis(run.graphGeneratedAt, run.chunksLoadedAt)
                + " placementMs=" + elapsedMillis(run.placementStartedAt, teleportedAt)
                + " totalMs=" + elapsedMillis(run.startedAt, teleportedAt));
        } catch (Exception ex) {
            instance.state(DungeonState.FAILED);
            activationFailure = ex;
        } finally {
            releaseChunkTickets(run.world, run.chunks);
        }
        if (activationFailure == null) {
            run.future.complete(instance);
        } else {
            run.future.completeExceptionally(new IllegalStateException("Failed to activate dungeon: " + activationFailure.getMessage(), activationFailure));
        }
    }

    public void destroyDungeon(UUID id) {
        DungeonInstance instance = instances.get(id);
        if (instance == null) {
            return;
        }
        instance.state(DungeonState.DESTROYING);
        for (UUID playerId : instance.playerIds()) {
            playerInstances.remove(playerId);
            playerRooms.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (player != null && defaultWorld != null && player.getWorld().getName().equals(instance.worldName())) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
        }
        try {
            clearDungeonBlocks(instance);
            worldManager.destroyWorld(instance.worldName());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to delete dungeon world " + instance.worldName() + ": " + ex.getMessage());
        }
        instance.state(DungeonState.DESTROYED);
        instances.remove(id);
        aliases.entrySet().removeIf(entry -> entry.getValue().equals(id));
        Bukkit.getPluginManager().callEvent(new DungeonDestroyedEvent(instance));
    }

    public boolean exitDungeon(Player player) {
        Optional<DungeonInstance> instance = getDungeon(player);
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (instance.isPresent()) {
            playerInstances.remove(player.getUniqueId());
            playerRooms.remove(player.getUniqueId());
            if (defaultWorld != null && player.getWorld().getName().equals(instance.get().worldName())) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
            return true;
        }
        if (defaultWorld != null && worldManager.isDungeonWorld(player.getWorld())) {
            player.teleport(defaultWorld.getSpawnLocation());
            return true;
        }
        return false;
    }

    public boolean isDungeonWorld(World world) {
        return worldManager.isDungeonWorld(world);
    }

    public Optional<DungeonInstance> getDungeonByAliasOrId(String value) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        try {
            int alias = Integer.parseInt(normalized);
            UUID id = aliases.get(alias);
            if (id != null) {
                return getDungeon(id);
            }
        } catch (NumberFormatException ignored) {
            // Try UUID prefix below.
        }
        List<DungeonInstance> matches = instances.values().stream()
            .filter(instance -> instance.id().toString().startsWith(value))
            .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public Optional<Integer> alias(DungeonInstance instance) {
        return aliases.entrySet().stream()
            .filter(entry -> entry.getValue().equals(instance.id()))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public Optional<DungeonInstance> getDungeon(UUID id) {
        return Optional.ofNullable(instances.get(id));
    }

    public Optional<DungeonInstance> getDungeon(Player player) {
        UUID id = playerInstances.get(player.getUniqueId());
        return id == null ? Optional.empty() : getDungeon(id);
    }

    public Collection<DungeonInstance> instances() {
        return List.copyOf(instances.values());
    }

    public List<String> instanceLabels() {
        return instances.values().stream()
            .map(instance -> "#" + alias(instance).orElse(0))
            .toList();
    }

    public Optional<RoomInstance> getRoom(Player player) {
        return getDungeon(player).flatMap(instance -> roomAt(instance, player.getLocation()));
    }

    public void updatePlayerRoom(Player player) {
        Optional<DungeonInstance> dungeon = getDungeon(player);
        if (dungeon.isEmpty()) {
            return;
        }
        Optional<RoomInstance> room = roomAt(dungeon.get(), player.getLocation());
        if (room.isEmpty()) {
            return;
        }
        int index = room.get().node().index();
        Integer previous = playerRooms.put(player.getUniqueId(), index);
        if (previous == null || previous != index) {
            if (room.get().state() == RoomState.UNSEEN) {
                setRoomState(dungeon.get(), room.get(), RoomState.ENTERED);
            }
            Bukkit.getPluginManager().callEvent(new RoomEnteredEvent(dungeon.get(), room.get(), player));
        }
    }

    public void setRoomState(DungeonInstance instance, RoomInstance room, RoomState state) {
        RoomState oldState = room.state();
        room.setState(state);
        Bukkit.getPluginManager().callEvent(new RoomStateChangeEvent(instance, room, oldState, state));
    }

    private Optional<RoomInstance> roomAt(DungeonInstance instance, Location location) {
        if (!location.getWorld().getName().equals(instance.worldName())) {
            return Optional.empty();
        }
        IntVector3 position = new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return instance.rooms().stream().filter(room -> room.bounds().contains(position)).findFirst();
    }

    private void teleportPlayersToStart(DungeonInstance instance) {
        RoomInstance start = instance.rooms().get(instance.graph().startNode().index());
        IntVector3 localSpawn = start.template().spawn() == null ? new IntVector3(1, 1, 1) : start.template().spawn();
        IntVector3 worldSpawn = start.node().transform().transformLocal(localSpawn);
        World world = Bukkit.getWorld(instance.worldName());
        if (world == null) {
            return;
        }
        Location spawn = new Location(world, worldSpawn.x() + 0.5, worldSpawn.y(), worldSpawn.z() + 0.5);
        for (UUID playerId : instance.playerIds()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(spawn);
                updatePlayerRoom(player);
            }
        }
    }

    private synchronized IntVector3 reserveDungeonOffset() {
        return new IntVector3(nextRegion++ * DUNGEON_REGION_SPACING, 0, 0);
    }

    private DungeonGraph translateGraph(DungeonGraph graph, IntVector3 offset) {
        List<DungeonNode> nodes = graph.nodes().stream()
            .map(node -> new DungeonNode(
                node.index(),
                node.templateId(),
                node.category(),
                node.depth(),
                new RoomTransform(node.transform().origin().add(offset), node.transform().rotation(), node.transform().templateSize())
            ))
            .toList();
        return new DungeonGraph(nodes, graph.edges());
    }

    private void clearDungeonBlocks(DungeonInstance instance) {
        World world = Bukkit.getWorld(instance.worldName());
        if (world == null) {
            return;
        }
        for (RoomInstance room : instance.rooms()) {
            var bounds = room.bounds();
            removeNonPlayerEntities(world, bounds);
            for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
                for (int y = bounds.min().y(); y <= bounds.max().y(); y++) {
                    for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                        world.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR, false);
                    }
                }
            }
        }
    }

    private void removeNonPlayerEntities(World world, BoundingBox3i bounds) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            Location location = entity.getLocation();
            IntVector3 position = new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (bounds.contains(position)) {
                entity.remove();
            }
        }
    }

    private CompletableFuture<Void> preloadDungeonChunks(World world, Set<ChunkCoordinate> chunks) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (ChunkCoordinate chunk : chunks) {
            futures.add(world.getChunkAtAsyncUrgently(chunk.x, chunk.z));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    static Set<ChunkCoordinate> chunksFor(DungeonGraph graph) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (DungeonNode node : graph.nodes()) {
            BoundingBox3i bounds = node.transform().transformedBounds();
            int minChunkX = Math.floorDiv(bounds.min().x(), 16);
            int maxChunkX = Math.floorDiv(bounds.max().x(), 16);
            int minChunkZ = Math.floorDiv(bounds.min().z(), 16);
            int maxChunkZ = Math.floorDiv(bounds.max().z(), 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    static Map<Integer, List<DungeonEdge>> indexEdges(DungeonGraph graph) {
        Map<Integer, List<DungeonEdge>> edgesByNode = new HashMap<>();
        for (DungeonEdge edge : graph.edges()) {
            edgesByNode.computeIfAbsent(edge.fromNode(), ignored -> new ArrayList<>()).add(edge);
            edgesByNode.computeIfAbsent(edge.toNode(), ignored -> new ArrayList<>()).add(edge);
        }
        return edgesByNode;
    }

    static boolean canPlaceAnotherRoom(int placedThisBatch, long elapsedNanos, long budgetNanos) {
        return placedThisBatch == 0 || elapsedNanos < budgetNanos;
    }

    private void releaseChunkTickets(World world, Set<ChunkCoordinate> chunks) {
        for (ChunkCoordinate chunk : chunks) {
            world.removePluginChunkTicket(chunk.x, chunk.z, plugin);
        }
    }

    private long elapsedMillis(long start, long end) {
        return TimeUnit.NANOSECONDS.toMillis(end - start);
    }

    record ChunkCoordinate(int x, int z) {
    }

    private record PlacementRun(
        UUID id,
        DungeonRequest request,
        DungeonGraph graph,
        World world,
        Map<String, RoomTemplate> templatesById,
        Map<Integer, List<DungeonEdge>> edgesByNode,
        List<RoomInstance> rooms,
        Set<ChunkCoordinate> chunks,
        long startedAt,
        long graphGeneratedAt,
        long chunksLoadedAt,
        long placementStartedAt,
        int nextIndex,
        CompletableFuture<DungeonInstance> future
    ) {
        private PlacementRun withNextIndex(int index) {
            return new PlacementRun(id, request, graph, world, templatesById, edgesByNode, rooms, chunks, startedAt, graphGeneratedAt, chunksLoadedAt, placementStartedAt, index, future);
        }
    }
}
