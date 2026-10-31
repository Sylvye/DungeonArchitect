package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.*;
import java.util.function.Predicate;

/** Shared floor-area capacity, safety, and deterministic entry allocation. */
public final class SpawnAreaResolver {
    private static final Comparator<IntVector3> ORDER = Comparator.comparingInt(IntVector3::x).thenComparingInt(IntVector3::y).thenComparingInt(IntVector3::z);
    private static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.WATER, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE);
    private SpawnAreaResolver() { }
    public static long areaCount(RoomTemplate template) { return template.markers().stream().filter(m -> m.purpose() == RoomMarker.Purpose.PLAYER_SPAWN).count(); }
    public static List<IntVector3> candidates(RoomTemplate template) {
        Set<IntVector3> positions = new TreeSet<>(ORDER);
        BoundingBox3i room = BoundingBox3i.fromMinAndSize(IntVector3.ZERO, template.size());
        for (RoomMarker marker : template.markers()) {
            if (marker.purpose() != RoomMarker.Purpose.PLAYER_SPAWN) continue;
            if (!room.contains(marker.position()) || !room.contains(marker.bounds().max())) throw new IllegalArgumentException("Spawn area '" + marker.name() + "' extends outside room '" + template.id() + "'");
            IntVector3 min = marker.position(), max = marker.bounds().max();
            for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) {
                IntVector3 feet = new IntVector3(x, y + 1, z);
                if (room.contains(feet) && room.contains(feet.add(new IntVector3(0, 1, 0)))) positions.add(feet);
            }
        }
        return List.copyOf(positions);
    }
    public static List<Location> safePositions(World world, RoomTemplate template, RoomTransform transform, Predicate<Location> safety) {
        return candidates(template).stream().map(transform::transformLocal).map(p -> new Location(world, p.x() + .5, p.y(), p.z() + .5)).filter(safety).toList();
    }
    public static List<Location> resolve(World world, RoomTemplate template, RoomTransform transform, int count, long seed, Predicate<Location> safety) {
        List<Location> safe = safePositions(world, template, transform, safety);
        if (areaCount(template) == 0 || safe.size() < count) throw new com.dungeonarchitect.runtime.SpawnAreaException(template.id(), safe.size(), count);
        return spread(safe, count, seed);
    }
    public static List<Location> spread(List<Location> candidates, int count, long seed) {
        if (count < 0 || count > candidates.size()) throw new IllegalArgumentException("Not enough safe spawn positions");
        List<Location> remaining = new ArrayList<>(candidates);
        remaining.sort(Comparator.comparingDouble(Location::getX).thenComparingDouble(Location::getY).thenComparingDouble(Location::getZ));
        Collections.shuffle(remaining, new Random(seed));
        List<Location> chosen = new ArrayList<>();
        while (chosen.size() < count) {
            Location best = remaining.getFirst(); double bestDistance = -1;
            for (Location candidate : remaining) {
                double distance = chosen.stream().mapToDouble(p -> horizontalDistance(p, candidate)).min().orElse(0);
                if (distance > bestDistance) { best = candidate; bestDistance = distance; }
            }
            chosen.add(best.clone()); remaining.remove(best);
        }
        return List.copyOf(chosen);
    }
    private static double horizontalDistance(Location a, Location b) { double x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x*x + z*z; }
    public static boolean safe(Location feet) {
        Block body = feet.getBlock(), head = feet.clone().add(0, 1, 0).getBlock(), floor = feet.clone().add(0, -1, 0).getBlock();
        return floor.getType().isSolid() && Math.abs(floor.getBoundingBox().getMaxY() - floor.getY() - 1) < .001
            && body.isPassable() && head.isPassable() && !body.isLiquid() && !head.isLiquid()
            && !HAZARDS.contains(floor.getType()) && !HAZARDS.contains(body.getType()) && !HAZARDS.contains(head.getType());
    }
}
