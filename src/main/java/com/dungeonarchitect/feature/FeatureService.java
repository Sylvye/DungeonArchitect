package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.runtime.RoomStructurePlacer;
import com.dungeonarchitect.template.RoomStructureService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class FeatureService {
    private final FeatureTemplateRegistry registry;
    private final RoomStructureService structureService;

    public FeatureService(FeatureTemplateRegistry registry, RoomStructureService structureService) {
        this.registry = registry;
        this.structureService = structureService;
    }

    public void placeFeatures(World world, RoomTemplate template, RoomTransform roomTransform, long dungeonSeed, int nodeIndex) throws IOException {
        for (RoomFeatureSlot slot : template.featureSlots()) {
            FeatureSlotEntry selected = select(slot.entries(), new Random(dungeonSeed ^ slot.id().hashCode() ^ nodeIndex));
            if (selected.featureId().equals(FeatureSlotEntry.EMPTY)) {
                continue;
            }
            FeatureTemplate feature = registry.get(selected.featureId()).orElse(null);
            if (feature == null) {
                continue;
            }
            Rotation featureRotation = FeatureMatcher.rotationFor(slot.size(), feature.size());
            if (featureRotation == null) {
                continue;
            }
            placeFeature(world, roomTransform, slot, feature, featureRotation, dungeonSeed, nodeIndex);
        }
    }

    public static FeatureSlotEntry select(List<FeatureSlotEntry> entries, Random random) {
        int total = entries.stream().mapToInt(FeatureSlotEntry::weight).sum();
        int roll = random.nextInt(total);
        for (FeatureSlotEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    private void placeFeature(World world, RoomTransform roomTransform, RoomFeatureSlot slot, FeatureTemplate feature, Rotation featureRotation, long dungeonSeed, int nodeIndex) throws IOException {
        Structure structure = structureService.server().getStructureManager().loadStructure(feature.structureFile().toFile());
        IntVector3 nbtSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!nbtSize.equals(feature.size())) {
            throw new IOException("Feature " + feature.id() + " feature.nbt size " + nbtSize + " does not match feature.yml size " + feature.size() + ". Re-save this feature.");
        }
        Rotation worldRotation = compose(roomTransform.rotation(), featureRotation);
        IntVector3 rotatedFeatureSize = featureRotation.rotateSize(feature.size());
        IntVector3 localFeatureMin = slot.position().add(FeatureMatcher.placementOffset(slot.size(), rotatedFeatureSize));
        IntVector3 origin = transformedLocalBounds(roomTransform, localFeatureMin, rotatedFeatureSize).min();
        RoomTransform featureTransform = new RoomTransform(origin, worldRotation, feature.size());
        IntVector3 pasteOrigin = RoomStructurePlacer.pasteOrigin(featureTransform);
        structure.place(
            new Location(world, pasteOrigin.x(), pasteOrigin.y(), pasteOrigin.z()),
            true,
            toBukkit(worldRotation),
            Mirror.NONE,
            0,
            RoomStructurePlacer.STRUCTURE_INTEGRITY,
            new Random(dungeonSeed ^ nodeIndex ^ feature.id().hashCode())
        );
    }

    private static BoundingBox3i transformedLocalBounds(RoomTransform roomTransform, IntVector3 localMin, IntVector3 size) {
        IntVector3 max = localMin.add(size).subtract(new IntVector3(1, 1, 1));
        List<IntVector3> points = new BoundingBox3i(localMin, max).corners().stream()
            .map(roomTransform::transformLocal)
            .toList();
        int minX = points.stream().min(Comparator.comparingInt(IntVector3::x)).orElseThrow().x();
        int minY = points.stream().min(Comparator.comparingInt(IntVector3::y)).orElseThrow().y();
        int minZ = points.stream().min(Comparator.comparingInt(IntVector3::z)).orElseThrow().z();
        int maxX = points.stream().max(Comparator.comparingInt(IntVector3::x)).orElseThrow().x();
        int maxY = points.stream().max(Comparator.comparingInt(IntVector3::y)).orElseThrow().y();
        int maxZ = points.stream().max(Comparator.comparingInt(IntVector3::z)).orElseThrow().z();
        return new BoundingBox3i(new IntVector3(minX, minY, minZ), new IntVector3(maxX, maxY, maxZ));
    }

    private static Rotation compose(Rotation first, Rotation second) {
        Rotation[] values = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
        return values[(index(first) + index(second)) % 4];
    }

    private static int index(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    private static StructureRotation toBukkit(Rotation rotation) {
        return switch (rotation) {
            case NONE -> StructureRotation.NONE;
            case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> StructureRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
        };
    }
}
