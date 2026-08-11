package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DoorGeometryTest {
    @Test
    void computesFullHorizontalDoorApertures() {
        assertEquals(
            new BoundingBox3i(new IntVector3(5, 1, 0), new IntVector3(7, 4, 0)),
            DoorGeometry.localBounds(new DoorSocket("north", new IntVector3(5, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4))
        );
        assertEquals(
            new BoundingBox3i(new IntVector3(0, 1, 5), new IntVector3(0, 4, 7)),
            DoorGeometry.localBounds(new DoorSocket("east", new IntVector3(0, 1, 5), Direction3.EAST, SocketType.STANDARD, 3, 4))
        );
    }

    @Test
    void computesFullVerticalDoorApertures() {
        assertEquals(
            new BoundingBox3i(new IntVector3(5, 0, 6), new IntVector3(7, 0, 9)),
            DoorGeometry.localBounds(new DoorSocket("floor", new IntVector3(5, 0, 6), Direction3.DOWN, SocketType.STANDARD, 3, 4))
        );
        assertEquals(
            new BoundingBox3i(new IntVector3(5, 9, 6), new IntVector3(7, 9, 9)),
            DoorGeometry.localBounds(new DoorSocket("ceiling", new IntVector3(5, 9, 6), Direction3.UP, SocketType.STANDARD, 3, 4))
        );
    }

    @Test
    void smallerDoorTransformCentersDoorAndAlignsGatewayToRoomEdge() {
        DoorSocket slot = new DoorSocket("north", new IntVector3(8, 2, 0), new IntVector3(5, 4, 3), Direction3.NORTH, Set.of(), List.of());
        DoorTemplate door = new DoorTemplate(
            "small_arch",
            new IntVector3(3, 4, 2),
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH),
            Path.of("door.nbt")
        );

        RoomTransform transform = DoorGeometry.doorTransform(slot, door, new RoomTransform(IntVector3.ZERO, Rotation.NONE, new IntVector3(21, 10, 21)));

        assertEquals(new BoundingBox3i(new IntVector3(9, 2, 0), new IntVector3(11, 5, 1)), transform.transformedBounds());
        assertEquals(new BoundingBox3i(new IntVector3(10, 3, 0), new IntVector3(10, 4, 0)), DoorGeometry.transformedBounds(door.gateway(), transform));
    }

    @Test
    void smallerHorizontalDoorsAlignToSlotBottomOnEveryWall() {
        for (Direction3 facing : List.of(Direction3.NORTH, Direction3.SOUTH, Direction3.EAST, Direction3.WEST)) {
            IntVector3 slotPosition = switch (facing) {
                case NORTH -> new IntVector3(8, 2, 0);
                case SOUTH -> new IntVector3(8, 2, 20);
                case EAST -> new IntVector3(20, 2, 8);
                case WEST -> new IntVector3(0, 2, 8);
                default -> throw new IllegalStateException();
            };
            IntVector3 slotSize = switch (facing) {
                case NORTH, SOUTH -> new IntVector3(5, 6, 1);
                case EAST, WEST -> new IntVector3(1, 6, 5);
                default -> throw new IllegalStateException();
            };
            DoorSocket slot = new DoorSocket("slot", slotPosition, slotSize, facing, Set.of(), List.of());
            DoorTemplate door = new DoorTemplate(
                "arch_" + facing.name().toLowerCase(),
                new IntVector3(3, 4, 1),
                Set.of(),
                List.of(),
                List.of(),
                new DoorGateway(new IntVector3(1, 0, 0), new IntVector3(1, 4, 1), Direction3.NORTH),
                Path.of("door.nbt")
            );

            RoomTransform transform = DoorGeometry.doorTransform(slot, door, new RoomTransform(IntVector3.ZERO, Rotation.NONE, new IntVector3(21, 10, 21)));

            assertEquals(slotPosition.y(), transform.transformedBounds().min().y(), facing.name());
        }
    }

    @Test
    void smallerVerticalDoorTransformAlignsGatewayToCeilingEdge() {
        DoorSocket slot = new DoorSocket("ceiling", new IntVector3(8, 9, 7), new IntVector3(5, 1, 5), Direction3.UP, Set.of(), List.of());
        DoorTemplate door = new DoorTemplate(
            "small_hatch",
            new IntVector3(3, 1, 3),
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(1, 0, 1), new IntVector3(1, 1, 1), Direction3.UP),
            Path.of("door.nbt")
        );

        RoomTransform transform = DoorGeometry.doorTransform(slot, door, new RoomTransform(IntVector3.ZERO, Rotation.NONE, new IntVector3(21, 10, 21)));

        assertEquals(new BoundingBox3i(new IntVector3(9, 9, 8), new IntVector3(11, 9, 10)), transform.transformedBounds());
        assertEquals(new BoundingBox3i(new IntVector3(10, 9, 9), new IntVector3(10, 9, 9)), DoorGeometry.transformedBounds(door.gateway(), transform));
    }
}
