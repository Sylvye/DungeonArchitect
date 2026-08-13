package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.door.DoorService;
import com.dungeonarchitect.feature.FeatureService;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.loot.LootService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.util.List;
import java.util.Random;

public final class RoomStructurePlacer {
    public static final float STRUCTURE_INTEGRITY = 1.0f;
    private final RoomStructureService structureService;
    private final FeatureService featureService;
    private final DoorService doorService;
    private final LootService lootService;

    public RoomStructurePlacer(RoomStructureService structureService, FeatureService featureService) {
        this(structureService, featureService, null, null);
    }

    public RoomStructurePlacer(RoomStructureService structureService, FeatureService featureService, DoorService doorService) {
        this(structureService, featureService, doorService, null);
    }
    public RoomStructurePlacer(RoomStructureService structureService, FeatureService featureService, DoorService doorService, LootService lootService) {
        this.structureService = structureService;
        this.featureService = featureService;
        this.doorService = doorService;
        this.lootService = lootService;
    }

    public void place(World world, RoomTemplate template, RoomTransform transform, long dungeonSeed, int nodeIndex) throws IOException {
        place(world, template, transform, dungeonSeed, nodeIndex, List.of());
    }

    public void place(World world, RoomTemplate template, RoomTransform transform, long dungeonSeed, int nodeIndex, List<DungeonEdge> edges) throws IOException {
        Structure structure = structureService.loadStructure(template.structureFile());
        IntVector3 nbtSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!nbtSize.equals(template.size())) {
            throw new IOException("Template " + template.id() + " room.nbt size " + nbtSize + " does not match room.yml size " + template.size() + ". Re-save this room.");
        }
        IntVector3 origin = pasteOrigin(transform);
        structure.place(
            new Location(world, origin.x(), origin.y(), origin.z()),
            true,
            toBukkit(transform.rotation()),
            Mirror.NONE,
            0,
            STRUCTURE_INTEGRITY,
            new Random(dungeonSeed ^ nodeIndex)
        );
        featureService.placeFeatures(world, template, transform, dungeonSeed, nodeIndex);
        if (doorService != null) {
            doorService.placeDoors(world, template, transform, dungeonSeed, nodeIndex, edges);
        }
        if (lootService != null) {
            lootService.placeLoot(world, template.id(), template.markers(), template.lootBindings(), transform, dungeonSeed, "room:" + nodeIndex);
        }
    }

    public static IntVector3 pasteOrigin(RoomTransform transform) {
        IntVector3 origin = transform.origin();
        IntVector3 size = transform.templateSize();
        IntVector3 offset = switch (transform.rotation()) {
            case NONE -> IntVector3.ZERO;
            case CLOCKWISE_90 -> new IntVector3(size.z() - 1, 0, 0);
            case CLOCKWISE_180 -> new IntVector3(size.x() - 1, 0, size.z() - 1);
            case COUNTERCLOCKWISE_90 -> new IntVector3(0, 0, size.x() - 1);
        };
        return origin.add(offset);
    }

    private StructureRotation toBukkit(Rotation rotation) {
        return switch (rotation) {
            case NONE -> StructureRotation.NONE;
            case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> StructureRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
        };
    }

}
