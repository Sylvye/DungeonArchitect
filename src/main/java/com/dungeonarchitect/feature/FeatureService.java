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
import java.util.logging.Logger;

public final class FeatureService {
    private final FeatureTemplateRegistry registry;
    private final RoomStructureService structureService;
    private final Logger logger;

    public FeatureService(FeatureTemplateRegistry registry, RoomStructureService structureService) {
        this(registry, structureService, Logger.getLogger("DungeonArchitect"));
    }

    public FeatureService(FeatureTemplateRegistry registry, RoomStructureService structureService, Logger logger) {
        this.registry = registry;
        this.structureService = structureService;
        this.logger = logger;
    }

    public void placeFeatures(World world, RoomTemplate template, RoomTransform roomTransform, long dungeonSeed, int nodeIndex) throws IOException {
        placeFeatureSlots(world, template.id(), template.featureSlots(), roomTransform, dungeonSeed, nodeIndex);
    }

    public void placeFeatureSlots(World world, String ownerId, List<RoomFeatureSlot> slots, RoomTransform roomTransform, long dungeonSeed, int nodeIndex) throws IOException {
        for (RoomFeatureSlot slot : slots) {
            FeatureRollResult roll = roll(slot, new Random(rollSeed(dungeonSeed, nodeIndex, ownerId, slot.id())));
            if (roll.status() == FeatureRollStatus.EMPTY || roll.status() == FeatureRollStatus.NO_ENTRIES) {
                continue;
            }
            if (roll.status() == FeatureRollStatus.UNKNOWN_FEATURE || roll.status() == FeatureRollStatus.SIZE_MISMATCH) {
                logger.warning("Skipped feature slot " + ownerId + "/" + slot.id() + ": " + roll.reason());
                continue;
            }
            FeatureTemplate feature = registry.get(roll.selectedFeatureId()).orElseThrow();
            placeFeature(world, roomTransform, slot, feature, roll.rotation(), dungeonSeed, nodeIndex);
        }
    }

    public FeatureRollResult roll(RoomFeatureSlot slot, Random random) {
        if (slot.entries().isEmpty()) {
            return FeatureRollResult.noEntries(slot);
        }
        FeatureSlotEntry selected = select(slot.entries(), random);
        int total = totalWeight(slot.entries());
        if (selected.featureId().equals(FeatureSlotEntry.EMPTY)) {
            return new FeatureRollResult(slot.id(), slot.entries(), selected.featureId(), selected.weight(), total, FeatureRollStatus.EMPTY, "selected empty", null);
        }
        FeatureTemplate feature = registry.get(selected.featureId()).orElse(null);
        if (feature == null) {
            return new FeatureRollResult(slot.id(), slot.entries(), selected.featureId(), selected.weight(), total, FeatureRollStatus.UNKNOWN_FEATURE, "selected unknown feature " + selected.featureId(), null);
        }
        Rotation rotation = FeatureMatcher.rotationFor(slot.size(), feature.size());
        if (rotation == null) {
            return new FeatureRollResult(slot.id(), slot.entries(), selected.featureId(), selected.weight(), total, FeatureRollStatus.SIZE_MISMATCH, "selected feature " + selected.featureId() + " size " + feature.size() + " does not fit slot size " + slot.size(), null);
        }
        return new FeatureRollResult(slot.id(), slot.entries(), selected.featureId(), selected.weight(), total, FeatureRollStatus.SELECTED, "selected feature " + selected.featureId(), rotation);
    }

    public static FeatureSlotEntry select(List<FeatureSlotEntry> entries, Random random) {
        int total = totalWeight(entries);
        if (total <= 0) {
            throw new IllegalArgumentException("Feature entries must have positive total weight");
        }
        int roll = random.nextInt(total);
        for (FeatureSlotEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    public static long rollSeed(long dungeonSeed, int nodeIndex, String ownerId, String slotId) {
        long seed = dungeonSeed ^ 0x9E3779B97F4A7C15L;
        seed = mix(seed ^ nodeIndex);
        seed = mix(seed ^ stableHash(ownerId));
        return mix(seed ^ stableHash(slotId));
    }

    private static int totalWeight(List<FeatureSlotEntry> entries) {
        return entries.stream().mapToInt(FeatureSlotEntry::weight).sum();
    }

    private static long stableHash(String value) {
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return hash;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
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
        logger.info("Placing feature " + feature.id()
            + " slot=" + slot.id()
            + " origin=" + origin
            + " pasteOrigin=" + pasteOrigin
            + " rotation=" + worldRotation
            + " featureSize=" + feature.size()
            + " slotSize=" + slot.size());
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

    public enum FeatureRollStatus {
        NO_ENTRIES,
        EMPTY,
        UNKNOWN_FEATURE,
        SIZE_MISMATCH,
        SELECTED
    }

    public record FeatureRollResult(
        String slotId,
        List<FeatureSlotEntry> entries,
        String selectedFeatureId,
        int selectedWeight,
        int totalWeight,
        FeatureRollStatus status,
        String reason,
        Rotation rotation
    ) {
        public FeatureRollResult {
            entries = List.copyOf(entries);
        }

        private static FeatureRollResult noEntries(RoomFeatureSlot slot) {
            return new FeatureRollResult(slot.id(), List.of(), null, 0, 0, FeatureRollStatus.NO_ENTRIES, "slot has no feature entries", null);
        }
    }
}
