package com.dungeonarchitect.runtime;

import com.dungeonarchitect.api.event.DungeonCreatedEvent;
import com.dungeonarchitect.api.event.DungeonDestroyedEvent;
import com.dungeonarchitect.api.event.DungeonReadyEvent;
import com.dungeonarchitect.api.event.RoomEnteredEvent;
import com.dungeonarchitect.api.event.RoomStateChangeEvent;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.DungeonState;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomState;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.generation.DeterministicDungeonGenerator;
import com.dungeonarchitect.generation.DungeonGenerationRequest;
import com.dungeonarchitect.generation.DungeonGenerationResult;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DungeonManager {
    private final Plugin plugin;
    private final RoomTemplateRegistry templateRegistry;
    private final DeterministicDungeonGenerator generator;
    private final DungeonWorldManager worldManager;
    private final RoomStructurePlacer structurePlacer;
    private final Map<UUID, DungeonInstance> instances = new HashMap<>();
    private final Map<UUID, UUID> playerInstances = new HashMap<>();
    private final Map<UUID, Integer> playerRooms = new HashMap<>();
    private final Map<Integer, UUID> aliases = new HashMap<>();
    private int nextAlias = 1;

    public DungeonManager(Plugin plugin, RoomTemplateRegistry templateRegistry, DeterministicDungeonGenerator generator, DungeonWorldManager worldManager, RoomStructurePlacer structurePlacer) {
        this.plugin = plugin;
        this.templateRegistry = templateRegistry;
        this.generator = generator;
        this.worldManager = worldManager;
        this.structurePlacer = structurePlacer;
    }

    public DungeonInstance createDungeon(DungeonRequest request) {
        UUID id = UUID.randomUUID();
        DungeonGenerationResult result = generator.generate(templateRegistry.all(), new DungeonGenerationRequest(request.roomCount(), request.seed()));
        if (!result.successful()) {
            throw new IllegalStateException(String.join("; ", result.errors()));
        }
        World world = worldManager.createWorld(id);
        DungeonInstance preparing = new DungeonInstance(id, request.seed(), result.graph(), world.getName(), request.playerIds(), List.of(), DungeonState.GENERATING);
        Bukkit.getPluginManager().callEvent(new DungeonCreatedEvent(preparing));

        List<RoomInstance> rooms = new ArrayList<>();
        try {
            for (DungeonNode node : result.graph().nodes()) {
                RoomTemplate template = templateRegistry.get(node.templateId())
                    .orElseThrow(() -> new IllegalStateException("Template disappeared during generation: " + node.templateId()));
                plugin.getLogger().info("Placing dungeon " + id + " node=" + node.index()
                    + " template=" + template.id()
                    + " origin=" + node.transform().origin()
                    + " pasteOrigin=" + RoomStructurePlacer.pasteOrigin(node.transform())
                    + " rotation=" + node.transform().rotation()
                    + " metadataSize=" + template.size()
                    + " bounds=" + node.transform().transformedBounds());
                structurePlacer.place(world, template, node.transform(), request.seed(), node.index());
                rooms.add(new RoomInstance(node, template));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to place dungeon structures: " + ex.getMessage(), ex);
        }

        DungeonInstance instance = new DungeonInstance(id, request.seed(), result.graph(), world.getName(), request.playerIds(), rooms, DungeonState.READY);
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
            worldManager.destroyWorld(instance.worldName());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to delete dungeon world " + instance.worldName() + ": " + ex.getMessage());
        }
        instance.state(DungeonState.DESTROYED);
        instances.remove(id);
        aliases.entrySet().removeIf(entry -> entry.getValue().equals(id));
        Bukkit.getPluginManager().callEvent(new DungeonDestroyedEvent(instance));
    }

    public void exitDungeon(Player player) {
        Optional<DungeonInstance> instance = getDungeon(player);
        if (instance.isEmpty()) {
            return;
        }
        playerInstances.remove(player.getUniqueId());
        playerRooms.remove(player.getUniqueId());
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (defaultWorld != null && player.getWorld().getName().equals(instance.get().worldName())) {
            player.teleport(defaultWorld.getSpawnLocation());
        }
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
}
