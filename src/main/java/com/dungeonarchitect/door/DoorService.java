package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.feature.FeatureService;
import com.dungeonarchitect.generation.DoorGeometry;
import com.dungeonarchitect.runtime.RoomStructurePlacer;
import com.dungeonarchitect.template.RoomStructureService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public final class DoorService {
    private final DoorTemplateRegistry registry;
    private final RoomStructureService structureService;
    private final FeatureService featureService;
    private final Logger logger;

    public DoorService(DoorTemplateRegistry registry, RoomStructureService structureService, FeatureService featureService, Logger logger) {
        this.registry = registry;
        this.structureService = structureService;
        this.featureService = featureService;
        this.logger = logger;
    }

    public void placeDoors(World world, RoomTemplate room, RoomTransform roomTransform, long dungeonSeed, int nodeIndex) throws IOException {
        BoundingBox3i roomBounds = roomTransform.transformedBounds();
        for (DoorSocket slot : room.doors()) {
            DoorRollResult roll = roll(slot, new Random(dungeonSeed ^ nodeIndex ^ slot.id().hashCode()));
            if (roll.status() == DoorRollStatus.EMPTY || roll.status() == DoorRollStatus.NO_ENTRIES) {
                continue;
            }
            if (roll.status() != DoorRollStatus.SELECTED) {
                logger.warning("Skipped door slot " + room.id() + "/" + slot.id() + ": " + roll.reason());
                continue;
            }
            DoorTemplate door = registry.get(roll.selectedDoorId()).orElseThrow();
            placeDoor(world, roomTransform, roomBounds, slot, door, dungeonSeed, nodeIndex);
        }
    }

    public DoorRollResult roll(DoorSocket slot, Random random) {
        if (slot.entries().isEmpty()) {
            return new DoorRollResult(slot.id(), slot.entries(), null, 0, 0, DoorRollStatus.NO_ENTRIES, "slot has no door entries");
        }
        DoorSlotEntry selected = select(slot.entries(), random);
        int total = totalWeight(slot.entries());
        if (selected.doorId().equals(DoorSlotEntry.EMPTY)) {
            return new DoorRollResult(slot.id(), slot.entries(), selected.doorId(), selected.weight(), total, DoorRollStatus.EMPTY, "selected empty");
        }
        DoorTemplate door = registry.get(selected.doorId()).orElse(null);
        if (door == null) {
            return new DoorRollResult(slot.id(), slot.entries(), selected.doorId(), selected.weight(), total, DoorRollStatus.UNKNOWN_DOOR, "selected unknown door " + selected.doorId());
        }
        DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(slot, door);
        if (!match.matched()) {
            return new DoorRollResult(slot.id(), slot.entries(), selected.doorId(), selected.weight(), total, DoorRollStatus.SIZE_MISMATCH, "selected door " + selected.doorId() + " does not match slot: " + match.reason());
        }
        return new DoorRollResult(slot.id(), slot.entries(), selected.doorId(), selected.weight(), total, DoorRollStatus.SELECTED, "selected door " + selected.doorId());
    }

    public static DoorSlotEntry select(List<DoorSlotEntry> entries, Random random) {
        int total = totalWeight(entries);
        if (total <= 0) {
            throw new IllegalArgumentException("Door entries must have positive total weight");
        }
        int roll = random.nextInt(total);
        for (DoorSlotEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    private static int totalWeight(List<DoorSlotEntry> entries) {
        return entries.stream().mapToInt(DoorSlotEntry::weight).sum();
    }

    private void placeDoor(World world, RoomTransform roomTransform, BoundingBox3i roomBounds, DoorSocket slot, DoorTemplate door, long dungeonSeed, int nodeIndex) throws IOException {
        DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(slot, door);
        if (!match.matched()) {
            logger.warning("Skipped door " + door.id() + " for slot " + slot.id() + ": " + match.reason());
            return;
        }
        Rotation rotation = rotationTo(door.gateway().facing(), roomTransform.transformFacing(slot.facing()));
        BoundingBox3i slotBounds = DoorGeometry.transformedBounds(slot, roomTransform);
        RoomTransform doorTransform = new RoomTransform(slotBounds.min(), rotation, door.size());
        BoundingBox3i doorBounds = doorTransform.transformedBounds();
        if (!contains(roomBounds, doorBounds)) {
            logger.warning("Skipped door " + door.id() + " for slot " + slot.id() + ": transformed bounds " + doorBounds + " do not fit room bounds " + roomBounds);
            return;
        }
        Structure structure = structureService.server().getStructureManager().loadStructure(door.structureFile().toFile());
        IntVector3 nbtSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!nbtSize.equals(door.size())) {
            throw new IOException("Door " + door.id() + " door.nbt size " + nbtSize + " does not match door.yml size " + door.size() + ". Re-save this door.");
        }
        IntVector3 pasteOrigin = RoomStructurePlacer.pasteOrigin(doorTransform);
        structure.place(
            new Location(world, pasteOrigin.x(), pasteOrigin.y(), pasteOrigin.z()),
            true,
            toBukkit(rotation),
            Mirror.NONE,
            0,
            RoomStructurePlacer.STRUCTURE_INTEGRITY,
            new Random(dungeonSeed ^ nodeIndex ^ door.id().hashCode())
        );
        featureService.placeFeatureSlots(world, "door:" + door.id(), door.featureSlots(), doorTransform, dungeonSeed ^ door.id().hashCode(), nodeIndex);
    }

    private static boolean contains(BoundingBox3i outer, BoundingBox3i inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }

    private static Rotation rotationTo(Direction3 from, Direction3 to) {
        for (Rotation rotation : Rotation.values()) {
            if (from.rotateY(rotation) == to) {
                return rotation;
            }
        }
        throw new IllegalArgumentException("Cannot rotate " + from + " to " + to);
    }

    private static StructureRotation toBukkit(Rotation rotation) {
        return switch (rotation) {
            case NONE -> StructureRotation.NONE;
            case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> StructureRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
        };
    }

    public enum DoorRollStatus {
        NO_ENTRIES,
        EMPTY,
        UNKNOWN_DOOR,
        SIZE_MISMATCH,
        SELECTED
    }

    public record DoorRollResult(String slotId, List<DoorSlotEntry> entries, String selectedDoorId, int selectedWeight, int totalWeight, DoorRollStatus status, String reason) {
        public DoorRollResult {
            entries = List.copyOf(entries);
        }
    }
}
