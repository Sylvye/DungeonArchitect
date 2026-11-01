package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.runtime.SpawnAreaResolver;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;
import java.util.function.Predicate;

/** Room authoring can begin even before a usable spawn marker has been authored. */
public final class EditorEntryResolver {
    public enum Source { SPAWN, INTERIOR, WORKSPACE }
    public record Entry(Location location, Source source) { }
    private EditorEntryResolver() { }
    public static Entry resolve(World world, RoomTemplate template, RoomTransform transform, Location fallback, long seed, Predicate<Location> safety) {
        try {
            List<Location> authored = SpawnAreaResolver.safePositions(world, template, transform, safety);
            if (!authored.isEmpty()) return entry(SpawnAreaResolver.spread(authored, 1, seed).getFirst(), Source.SPAWN, fallback);
        } catch (IllegalArgumentException ignored) { /* Invalid marker bounds must not lock users out of repairing a room. */ }
        List<IntVector3> interior = new ArrayList<>(); IntVector3 size = template.size();
        for (int y = 1; y < size.y() - 1; y++) for (int x = 0; x < size.x(); x++) for (int z = 0; z < size.z(); z++) interior.add(new IntVector3(x,y,z));
        double centerX = (size.x()-1)/2.0, centerY = (size.y()-1)/2.0, centerZ = (size.z()-1)/2.0;
        interior.sort(Comparator.comparingDouble((IntVector3 p) -> square(p.x()-centerX)+square(p.y()-centerY)+square(p.z()-centerZ)).thenComparingInt(IntVector3::y).thenComparingInt(IntVector3::x).thenComparingInt(IntVector3::z));
        for (IntVector3 local : interior) { IntVector3 p = transform.transformLocal(local); Location location = new Location(world, p.x()+.5, p.y(), p.z()+.5);
            if (safety.test(location)) return entry(location, Source.INTERIOR, fallback);
        }
        return entry(fallback.clone(), Source.WORKSPACE, fallback);
    }
    private static double square(double value) { return value*value; }
    private static Entry entry(Location location, Source source, Location looking) { location.setYaw(looking.getYaw()); location.setPitch(looking.getPitch()); return new Entry(location, source); }
}
