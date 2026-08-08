package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

public final class FeatureService {
    private final FeaturePoolRegistry registry;

    public FeatureService(FeaturePoolRegistry registry) {
        this.registry = registry;
    }

    public void placeFeatures(World world, RoomTemplate template, RoomTransform transform, long dungeonSeed, int nodeIndex) {
        for (RoomFeatureSlot slot : template.featureSlots()) {
            List<FeatureEntry> entries = registry.get(slot.poolId()).orElse(List.of());
            if (entries.isEmpty()) {
                continue;
            }
            FeatureEntry selected = select(entries, new Random(dungeonSeed ^ slot.id().hashCode() ^ nodeIndex));
            if (selected.type() == FeatureType.EMPTY) {
                continue;
            }
            IntVector3 worldPosition = transform.transformLocal(slot.position());
            Location location = new Location(world, worldPosition.x(), worldPosition.y(), worldPosition.z());
            location.getBlock().setType(selected.material(), false);
        }
    }

    private FeatureEntry select(List<FeatureEntry> entries, Random random) {
        int total = entries.stream().mapToInt(FeatureEntry::weight).sum();
        int roll = random.nextInt(total);
        for (FeatureEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }
}
