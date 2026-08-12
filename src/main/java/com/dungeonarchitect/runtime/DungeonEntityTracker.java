package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks every non-player entity belonging to a generating or active dungeon. */
public final class DungeonEntityTracker implements Listener, Runnable {
    private final NamespacedKey ownerKey;
    private final Map<UUID, TrackedDungeon> tracked = new HashMap<>();

    public DungeonEntityTracker(Plugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "dungeon_id");
    }

    public void begin(UUID dungeonId, World world, DungeonGraph graph) {
        tracked.put(dungeonId, new TrackedDungeon(world, DungeonFootprint.from(graph)));
        purgeOrphaned(world);
    }

    public void claimRoomEntities(UUID dungeonId, World world, BoundingBox3i bounds) {
        if (!tracked.containsKey(dungeonId)) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && contains(bounds, entity.getLocation())) {
                claim(entity, dungeonId);
            }
        }
    }

    public void removeOwned(UUID dungeonId, World world) {
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && dungeonId.toString().equals(owner(entity))) {
                entity.remove();
            }
        }
        tracked.remove(dungeonId);
        purgeOrphaned(world);
    }

    public void abandon(UUID dungeonId, World world) {
        removeOwned(dungeonId, world);
    }

    public void purgeOrphaned(World world) {
        Set<UUID> currentOwners = new HashSet<>(tracked.keySet());
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player) && !DungeonEntityOwnership.hasCurrentOwner(owner(entity), currentOwners)) {
                entity.remove();
            }
        }
    }

    @Override
    public void run() {
        Set<World> worlds = new HashSet<>();
        for (TrackedDungeon dungeon : tracked.values()) {
            worlds.add(dungeon.world);
        }
        worlds.forEach(this::purgeOrphaned);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        IntVector3 position = position(entity.getLocation());
        for (Map.Entry<UUID, TrackedDungeon> entry : tracked.entrySet()) {
            TrackedDungeon dungeon = entry.getValue();
            if (entity.getWorld().equals(dungeon.world) && dungeon.footprint.contains(position)) {
                claim(entity, entry.getKey());
                return;
            }
        }
    }

    private void claim(Entity entity, UUID dungeonId) {
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, dungeonId.toString());
    }

    private String owner(Entity entity) {
        return entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    private static boolean contains(BoundingBox3i bounds, Location location) {
        return bounds.contains(position(location));
    }

    private static IntVector3 position(Location location) {
        return new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private record TrackedDungeon(World world, DungeonFootprint footprint) {
    }
}
