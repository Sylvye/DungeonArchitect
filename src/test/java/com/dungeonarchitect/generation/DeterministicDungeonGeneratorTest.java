package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorConnectionRules;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeterministicDungeonGeneratorTest {
    @Test
    void generatesDeterministicGraphForSameSeed() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(250, 80);
        List<RoomTemplate> templates = List.of(startRoom(), combatRoom("combat_a", 10), combatRoom("combat_b", 5));

        var first = generator.generate(templates, new DungeonGenerationRequest(5, 12345L));
        var second = generator.generate(templates, new DungeonGenerationRequest(5, 12345L));

        assertTrue(first.successful(), first.errors().toString());
        assertEquals(first.graph(), second.graph());
    }

    @Test
    void failsWithoutExactlyOneStartRoom() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80);

        var result = generator.generate(List.of(combatRoom("combat", 1)), new DungeonGenerationRequest(2, 1L));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("START"));
    }

    @Test
    void connectedDoorsAreAdjacentAndRoomsDoNotOverlap() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(250, 80);
        List<RoomTemplate> templates = List.of(startRoom(), combatRoom("combat_a", 10), combatRoom("combat_b", 5));

        var result = generator.generate(templates, new DungeonGenerationRequest(4, 77L));

        assertTrue(result.successful(), result.errors().toString());
        Map<String, RoomTemplate> byId = templates.stream().collect(Collectors.toMap(RoomTemplate::id, Function.identity()));
        for (DungeonEdge edge : result.graph().edges()) {
            DungeonNode from = result.graph().nodes().get(edge.fromNode());
            DungeonNode to = result.graph().nodes().get(edge.toNode());
            DoorSocket fromDoor = byId.get(from.templateId()).doors().stream().filter(door -> door.id().equals(edge.fromDoorId())).findFirst().orElseThrow();
            DoorSocket toDoor = byId.get(to.templateId()).doors().stream().filter(door -> door.id().equals(edge.toDoorId())).findFirst().orElseThrow();

            var fromFacing = from.transform().transformFacing(fromDoor.facing());
            var toFacing = to.transform().transformFacing(toDoor.facing());
            var fromPosition = from.transform().transformLocal(fromDoor.position());
            var toPosition = to.transform().transformLocal(toDoor.position());

            assertEquals(fromFacing.opposite(), toFacing);
            assertEquals(fromPosition.add(fromFacing.vector()), toPosition);
            assertEquals(DoorGeometry.shifted(DoorGeometry.transformedBounds(fromDoor, from.transform()), fromFacing.vector()), DoorGeometry.transformedBounds(toDoor, to.transform()));
        }

        for (int i = 0; i < result.graph().nodes().size(); i++) {
            for (int j = i + 1; j < result.graph().nodes().size(); j++) {
                assertFalse(result.graph().nodes().get(i).transform().transformedBounds().intersects(result.graph().nodes().get(j).transform().transformedBounds()));
            }
        }
    }

    @Test
    void graphValidatorRejectsDisconnectedDoorPositions() {
        var graph = new com.dungeonarchitect.domain.DungeonGraph(
            List.of(
                new DungeonNode(0, "start", RoomCategory.START, 0, new RoomTransform(new IntVector3(0, 80, 0), Rotation.NONE, new IntVector3(5, 4, 5))),
                new DungeonNode(1, "combat_a", RoomCategory.COMBAT, 1, new RoomTransform(new IntVector3(20, 80, 20), Rotation.NONE, new IntVector3(5, 4, 5)))
            ),
            List.of(new DungeonEdge(0, "north", 1, "south"))
        );

        var errors = new DungeonGraphValidator().validate(graph, List.of(startRoom(), combatRoom("combat_a", 1)));

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("door rectangles are not aligned")));
    }

    @Test
    void graphValidatorRejectsConnectionRuleViolation() {
        DoorSocket startDoor = taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "locked_blue")
            .withConnectionRules(new DoorConnectionRules(Set.of("treasure"), Set.of(), Set.of("boss"), Set.of(), false));
        DoorSocket combatDoor = taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "treasure");
        RoomTemplate start = taggedCompactRoom("start", RoomCategory.START, 1, Set.of("entry"), List.of(startDoor));
        RoomTemplate combat = taggedCompactRoom("combat", RoomCategory.COMBAT, 1, Set.of("treasure"), List.of(combatDoor));
        var graph = new com.dungeonarchitect.domain.DungeonGraph(
            List.of(
                new DungeonNode(0, "start", RoomCategory.START, 0, new RoomTransform(new IntVector3(0, 80, 0), Rotation.NONE, new IntVector3(5, 4, 5))),
                new DungeonNode(1, "combat", RoomCategory.COMBAT, 1, new RoomTransform(new IntVector3(0, 80, -5), Rotation.NONE, new IntVector3(5, 4, 5)))
            ),
            List.of(new DungeonEdge(0, "north", 1, "south"))
        );

        var errors = new DungeonGraphValidator().validate(graph, List.of(start, combat));

        assertTrue(errors.stream().anyMatch(error -> error.contains("violates connection rules")), errors.toString());
    }

    @Test
    void alignsThreeWideDoorRectanglesWithoutLateralOffset() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80);
        List<RoomTemplate> templates = List.of(
            largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4)),
            largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("north", new IntVector3(9, 3, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4))
        );

        var result = generator.generate(templates, new DungeonGenerationRequest(2, 1L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        DoorSocket fromDoor = templates.getFirst().doors().getFirst();
        DoorSocket toDoor = templates.get(1).doors().getFirst();
        var fromFacing = from.transform().transformFacing(fromDoor.facing());
        assertEquals(DoorGeometry.shifted(DoorGeometry.transformedBounds(fromDoor, from.transform()), fromFacing.vector()), DoorGeometry.transformedBounds(toDoor, to.transform()));
    }

    @Test
    void alignsEastWestDoorRectanglesUsingZWidth() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80);
        List<RoomTemplate> templates = List.of(
            largeRoom("start", RoomCategory.START, new DoorSocket("east", new IntVector3(20, 3, 9), Direction3.EAST, SocketType.STANDARD, 3, 4)),
            largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("west", new IntVector3(0, 3, 9), Direction3.WEST, SocketType.STANDARD, 3, 4))
        );

        var result = generator.generate(templates, new DungeonGenerationRequest(2, 2L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        DoorSocket fromDoor = templates.getFirst().doors().getFirst();
        DoorSocket toDoor = templates.get(1).doors().getFirst();
        var fromFacing = from.transform().transformFacing(fromDoor.facing());
        assertEquals(DoorGeometry.shifted(DoorGeometry.transformedBounds(fromDoor, from.transform()), fromFacing.vector()), DoorGeometry.transformedBounds(toDoor, to.transform()));
    }

    @Test
    void mismatchedDoorWidthsDoNotConnect() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(50, 80);
        List<RoomTemplate> templates = List.of(
            largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4)),
            largeRoom("hall", RoomCategory.COMBAT, new DoorSocket("south", new IntVector3(8, 3, 20), Direction3.SOUTH, SocketType.STANDARD, 5, 4))
        );

        var result = generator.generate(templates, new DungeonGenerationRequest(2, 3L));

        assertFalse(result.successful());
    }

    @Test
    void templateDoorModeConnectsUsingGatewaysInsideFullDoorBounds() {
        DoorTemplate arch = doorTemplate("arch", new IntVector3(3, 4, 3), new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), new IntVector3(3, 4, 3), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry("arch", 1))));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("south", new IntVector3(9, 3, 18), new IntVector3(3, 4, 3), Direction3.SOUTH, Set.of(), List.of(new DoorSlotEntry("arch", 1))));
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80, () -> List.of(arch));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 4L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        assertEquals("arch", edge.fromDoorTemplateId());
        assertEquals("arch", edge.toDoorTemplateId());

        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        DoorSocket fromSlot = start.doors().getFirst();
        DoorSocket toSlot = combat.doors().getFirst();
        var fromDoorTransform = DoorGeometry.doorTransform(fromSlot, arch, from.transform());
        var toDoorTransform = DoorGeometry.doorTransform(toSlot, arch, to.transform());
        var fromGateway = DoorGeometry.transformedBounds(arch.gateway(), fromDoorTransform);
        var toGateway = DoorGeometry.transformedBounds(arch.gateway(), toDoorTransform);

        assertEquals(DoorGeometry.shifted(fromGateway, DoorGeometry.gatewayFacing(arch, fromDoorTransform).vector()), toGateway);
    }

    @Test
    void templateDoorModeAllowsDoorBoundsSmallerThanSlotsWhenGatewaysAlign() {
        DoorTemplate arch = doorTemplate("arch", new IntVector3(3, 4, 2), new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(8, 3, 0), new IntVector3(5, 4, 3), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry("arch", 1))));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("south", new IntVector3(8, 3, 18), new IntVector3(5, 4, 3), Direction3.SOUTH, Set.of(), List.of(new DoorSlotEntry("arch", 1))));
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80, () -> List.of(arch));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 12L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        var fromDoorTransform = DoorGeometry.doorTransform(start.doors().getFirst(), arch, from.transform());
        var toDoorTransform = DoorGeometry.doorTransform(combat.doors().getFirst(), arch, to.transform());
        var fromGateway = DoorGeometry.transformedBounds(arch.gateway(), fromDoorTransform);
        var toGateway = DoorGeometry.transformedBounds(arch.gateway(), toDoorTransform);

        assertEquals(DoorGeometry.shifted(fromGateway, DoorGeometry.gatewayFacing(arch, fromDoorTransform).vector()), toGateway);
    }

    @Test
    void templateDoorModeRejectsIncompatibleGatewaySizes() {
        DoorTemplate small = doorTemplate("small", new IntVector3(3, 4, 3), new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH);
        DoorTemplate wide = doorTemplate("wide", new IntVector3(3, 4, 3), new IntVector3(0, 1, 0), new IntVector3(3, 2, 1), Direction3.NORTH);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), new IntVector3(3, 4, 3), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry("small", 1))));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("south", new IntVector3(9, 3, 18), new IntVector3(3, 4, 3), Direction3.SOUTH, Set.of(), List.of(new DoorSlotEntry("wide", 1))));
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80, () -> List.of(small, wide));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 5L));

        assertFalse(result.successful());
    }

    @Test
    void templateDoorModeDoesNotUseEmptyEntriesForExpansion() {
        DoorTemplate arch = doorTemplate("arch", new IntVector3(3, 4, 3), new IntVector3(1, 1, 0), new IntVector3(1, 2, 1), Direction3.NORTH);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), new IntVector3(3, 4, 3), Direction3.NORTH, Set.of(), List.of(new DoorSlotEntry(DoorSlotEntry.EMPTY, 1))));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("south", new IntVector3(9, 3, 18), new IntVector3(3, 4, 3), Direction3.SOUTH, Set.of(), List.of(new DoorSlotEntry("arch", 1))));
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80, () -> List.of(arch));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 6L));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("No open doors"));
    }

    @Test
    void connectsFloorToCeilingDoorRectangles() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("ceiling", new IntVector3(9, 9, 9), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of()));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 7L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        var fromFacing = from.transform().transformFacing(start.doors().getFirst().facing());
        assertEquals(Direction3.DOWN, fromFacing);
        assertEquals(DoorGeometry.shifted(DoorGeometry.transformedBounds(start.doors().getFirst(), from.transform()), fromFacing.vector()), DoorGeometry.transformedBounds(combat.doors().getFirst(), to.transform()));
        assertEquals(from.transform().transformedBounds().min().y() - 1, to.transform().transformedBounds().max().y());
    }

    @Test
    void connectsCeilingToFloorDoorRectangles() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("ceiling", new IntVector3(9, 9, 9), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 8L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        var fromFacing = from.transform().transformFacing(start.doors().getFirst().facing());
        assertEquals(Direction3.UP, fromFacing);
        assertEquals(DoorGeometry.shifted(DoorGeometry.transformedBounds(start.doors().getFirst(), from.transform()), fromFacing.vector()), DoorGeometry.transformedBounds(combat.doors().getFirst(), to.transform()));
        assertEquals(from.transform().transformedBounds().max().y() + 1, to.transform().transformedBounds().min().y());
    }

    @Test
    void templateDoorModeConnectsVerticalGateways() {
        DoorTemplate floorHatch = doorTemplate("floor_hatch", new IntVector3(3, 1, 5), new IntVector3(1, 0, 2), new IntVector3(1, 1, 1), Direction3.DOWN);
        DoorTemplate ceilingHatch = doorTemplate("ceiling_hatch", new IntVector3(3, 1, 5), new IntVector3(1, 0, 2), new IntVector3(1, 1, 1), Direction3.UP);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 0, 8), new IntVector3(3, 1, 5), Direction3.DOWN, Set.of(), List.of(new DoorSlotEntry("floor_hatch", 1))));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("ceiling", new IntVector3(9, 9, 8), new IntVector3(3, 1, 5), Direction3.UP, Set.of(), List.of(new DoorSlotEntry("ceiling_hatch", 1))));
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(100, 80, () -> List.of(floorHatch, ceilingHatch));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 9L));

        assertTrue(result.successful(), result.errors().toString());
        DungeonEdge edge = result.graph().edges().getFirst();
        assertEquals("floor_hatch", edge.fromDoorTemplateId());
        assertEquals("ceiling_hatch", edge.toDoorTemplateId());
        DungeonNode from = result.graph().nodes().get(edge.fromNode());
        DungeonNode to = result.graph().nodes().get(edge.toNode());
        var fromDoorTransform = DoorGeometry.doorTransform(start.doors().getFirst(), floorHatch, from.transform());
        var toDoorTransform = DoorGeometry.doorTransform(combat.doors().getFirst(), ceilingHatch, to.transform());
        var fromGateway = DoorGeometry.transformedBounds(floorHatch.gateway(), fromDoorTransform);
        var toGateway = DoorGeometry.transformedBounds(ceilingHatch.gateway(), toDoorTransform);

        assertEquals(DoorGeometry.shifted(fromGateway, DoorGeometry.gatewayFacing(floorHatch, fromDoorTransform).vector()), toGateway);
    }

    @Test
    void sameDirectionVerticalDoorPairDoesNotConnect() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 10L));

        assertFalse(result.successful());
    }

    @Test
    void verticalPlacementStillRejectsRoomBoundsCollisions() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80);
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 5, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("ceiling", new IntVector3(9, 9, 9), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of()));

        var result = generator.generate(List.of(start, combat), new DungeonGenerationRequest(2, 11L));

        assertFalse(result.successful());
    }

    @Test
    void graphValidatorRejectsSinglePointAdjacentButRectangleOffset() {
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("north", new IntVector3(9, 3, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("north", new IntVector3(9, 3, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4));
        var graph = new com.dungeonarchitect.domain.DungeonGraph(
            List.of(
                new DungeonNode(0, "start", RoomCategory.START, 0, new RoomTransform(new IntVector3(0, 80, 0), Rotation.NONE, new IntVector3(21, 10, 21))),
                new DungeonNode(1, "combat", RoomCategory.COMBAT, 1, new RoomTransform(new IntVector3(-2, 80, -21), Rotation.CLOCKWISE_180, new IntVector3(21, 10, 21)))
            ),
            List.of(new DungeonEdge(0, "north", 1, "north"))
        );

        var errors = new DungeonGraphValidator().validate(graph, List.of(start, combat));

        assertTrue(errors.stream().anyMatch(error -> error.contains("door rectangles are not aligned") && error.contains("delta ")), errors.toString());
    }

    @Test
    void graphValidatorRejectsMismatchedVerticalApertures() {
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("ceiling", new IntVector3(9, 9, 9), new IntVector3(5, 1, 3), Direction3.UP, Set.of(), List.of()));
        var graph = new com.dungeonarchitect.domain.DungeonGraph(
            List.of(
                new DungeonNode(0, "start", RoomCategory.START, 0, new RoomTransform(new IntVector3(0, 80, 0), Rotation.NONE, new IntVector3(21, 10, 21))),
                new DungeonNode(1, "combat", RoomCategory.COMBAT, 1, new RoomTransform(new IntVector3(0, 70, 0), Rotation.NONE, new IntVector3(21, 10, 21)))
            ),
            List.of(new DungeonEdge(0, "floor", 1, "ceiling"))
        );

        var errors = new DungeonGraphValidator().validate(graph, List.of(start, combat));

        assertTrue(errors.stream().anyMatch(error -> error.contains("mismatched aperture sizes")), errors.toString());
    }

    @Test
    void graphValidatorRejectsOverlappingStackedRooms() {
        RoomTemplate start = largeRoom("start", RoomCategory.START, new DoorSocket("floor", new IntVector3(9, 0, 9), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of()));
        RoomTemplate combat = largeRoom("combat", RoomCategory.COMBAT, new DoorSocket("ceiling", new IntVector3(9, 9, 9), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of()));
        var graph = new com.dungeonarchitect.domain.DungeonGraph(
            List.of(
                new DungeonNode(0, "start", RoomCategory.START, 0, new RoomTransform(new IntVector3(0, 80, 0), Rotation.NONE, new IntVector3(21, 10, 21))),
                new DungeonNode(1, "combat", RoomCategory.COMBAT, 1, new RoomTransform(new IntVector3(0, 75, 0), Rotation.NONE, new IntVector3(21, 10, 21)))
            ),
            List.of(new DungeonEdge(0, "floor", 1, "ceiling"))
        );

        var errors = new DungeonGraphValidator().validate(graph, List.of(start, combat));

        assertTrue(errors.stream().anyMatch(error -> error.contains("Room bounds overlap")), errors.toString());
    }

    @Test
    void generatesExactLargeDungeonWithManyOpenDoors() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(25_000, 80);
        List<RoomTemplate> templates = List.of(
            multiDoorRoom("start", RoomCategory.START, 1),
            multiDoorRoom("combat_a", RoomCategory.COMBAT, 10),
            multiDoorRoom("combat_b", RoomCategory.COMBAT, 7)
        );

        var result = generator.generate(templates, new DungeonGenerationRequest(120, 42L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals(120, result.graph().nodes().size());
    }

    @Test
    void exhaustsDeadOpenDoorsAndContinuesThroughOtherFrontierDoors() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(200, 80);
        RoomTemplate start = compactRoom("start", RoomCategory.START, 1, List.of(
            taggedDoor("dead", new IntVector3(2, 1, 0), Direction3.NORTH, "dead"),
            taggedDoor("ok", new IntVector3(2, 1, 4), Direction3.SOUTH, "ok")
        ));
        RoomTemplate hall = compactRoom("hall", RoomCategory.COMBAT, 1, List.of(
            taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "ok"),
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "ok")
        ));

        var result = generator.generate(List.of(start, hall), new DungeonGenerationRequest(2, 1L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals(2, result.graph().nodes().size());
    }

    @Test
    void prioritizesRequiredDoorConnections() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(200, 80);
        DoorSocket required = taggedDoor("required", new IntVector3(2, 1, 0), Direction3.NORTH, "required")
            .withConnectionRules(new DoorConnectionRules(Set.of(), Set.of(), true));
        RoomTemplate start = compactRoom("start", RoomCategory.START, 1, List.of(
            required,
            taggedDoor("optional", new IntVector3(2, 1, 4), Direction3.SOUTH, "optional")
        ));
        RoomTemplate deadEnd = compactRoom("dead_end", RoomCategory.COMBAT, 100, List.of(
            taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "optional")
        ));
        RoomTemplate requiredEnd = compactRoom("required_end", RoomCategory.COMBAT, 1, List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "required")
        ));

        var result = generator.generate(List.of(start, deadEnd, requiredEnd), new DungeonGenerationRequest(2, 1L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals("required_end", result.graph().nodes().get(1).templateId());
    }

    @Test
    void rejectsDungeonWithUnconnectedRequiredDoorAtTargetCount() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(20, 80);
        RoomTemplate start = compactRoom("start", RoomCategory.START, 1, List.of(
            taggedDoor("required", new IntVector3(2, 1, 0), Direction3.NORTH, "required")
                .withConnectionRules(new DoorConnectionRules(Set.of(), Set.of(), true))
        ));
        RoomTemplate unrelated = compactRoom("unrelated", RoomCategory.COMBAT, 1, List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "other")
        ));

        var result = generator.generate(List.of(start, unrelated), new DungeonGenerationRequest(1, 1L));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("Required door slots remain unconnected"));
    }

    @Test
    void roomTagRulesRejectBlockedConnectiveRooms() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(200, 80);
        DoorSocket startDoor = taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "route")
            .withConnectionRules(new DoorConnectionRules(Set.of(), Set.of(), Set.of("treasure"), Set.of("connector"), false));
        RoomTemplate start = taggedCompactRoom("start", RoomCategory.START, 1, Set.of("entry"), List.of(startDoor));
        RoomTemplate connector = taggedCompactRoom("connector", RoomCategory.COMBAT, 100, Set.of("connector"), List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "route")
        ));
        RoomTemplate treasure = taggedCompactRoom("treasure", RoomCategory.COMBAT, 1, Set.of("treasure"), List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "route")
        ));

        var result = generator.generate(List.of(start, connector, treasure), new DungeonGenerationRequest(2, 1L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals("treasure", result.graph().nodes().get(1).templateId());
    }

    @Test
    void differentSeedsCanProduceDifferentGraphs() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(25_000, 80);
        List<RoomTemplate> templates = List.of(
            multiDoorRoom("start", RoomCategory.START, 1),
            multiDoorRoom("combat_a", RoomCategory.COMBAT, 10),
            multiDoorRoom("combat_b", RoomCategory.COMBAT, 10)
        );

        var first = generator.generate(templates, new DungeonGenerationRequest(10, 1L));
        var second = generator.generate(templates, new DungeonGenerationRequest(10, 2L));

        assertTrue(first.successful(), first.errors().toString());
        assertTrue(second.successful(), second.errors().toString());
        assertNotEquals(first.graph(), second.graph());
    }

    @Test
    void backtracksFromDeadEndCandidateToCompleteExactRoomCount() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(1_000, 80);
        RoomTemplate start = compactRoom("start", RoomCategory.START, 1, List.of(
            new DoorSocket("north", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)
        ));
        RoomTemplate deadEnd = compactRoom("dead_end", RoomCategory.COMBAT, 500, List.of(
            new DoorSocket("south", new IntVector3(2, 1, 4), Direction3.SOUTH, SocketType.STANDARD, 1, 2)
        ));
        RoomTemplate passThrough = compactRoom("pass_through", RoomCategory.COMBAT, 1, List.of(
            new DoorSocket("south", new IntVector3(2, 1, 4), Direction3.SOUTH, SocketType.STANDARD, 1, 2),
            new DoorSocket("north", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)
        ));

        var result = generator.generate(List.of(start, deadEnd, passThrough), new DungeonGenerationRequest(3, 3L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals(3, result.graph().nodes().size());
    }

    @Test
    void budgetFailureReportsUsefulDiagnostics() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(1, 80);

        var result = generator.generate(List.of(startRoom(), combatRoom("combat", 1)), new DungeonGenerationRequest(5, 99L));

        assertFalse(result.successful());
        String error = result.errors().getFirst();
        assertTrue(error.contains("Search budget exhausted"), error);
        assertTrue(error.contains("rooms placed="), error);
        assertTrue(error.contains("open doors="), error);
        assertTrue(error.contains("exhausted doors="), error);
        assertTrue(error.contains("collision rejects="), error);
        assertTrue(error.contains("compatibility rejects="), error);
        assertTrue(error.contains("search steps="), error);
    }

    @Test
    void connectsMinimumTwoConnectorBeforeAllowingItToEnd() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(200, 80);
        RoomTemplate start = taggedCompactRoom("start", RoomCategory.START, 1, Set.of(), List.of(
            taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "start")
        ));
        RoomTemplate connector = withMinimumConnections(taggedCompactRoom("connector", RoomCategory.COMBAT, 1, Set.of(), List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "start"),
            taggedDoor("north", new IntVector3(2, 1, 0), Direction3.NORTH, "end")
        )), 2);
        RoomTemplate end = taggedCompactRoom("end", RoomCategory.COMBAT, 1, Set.of(), List.of(
            taggedDoor("south", new IntVector3(2, 1, 4), Direction3.SOUTH, "end")
        ));

        var result = generator.generate(List.of(start, connector, end), new DungeonGenerationRequest(3, 1L));

        assertTrue(result.successful(), result.errors().toString());
        assertEquals("connector", result.graph().nodes().get(1).templateId());
        assertEquals(2, result.graph().edges().stream()
            .filter(edge -> edge.fromNode() == 1 || edge.toNode() == 1)
            .count());
    }

    @Test
    void rejectsImpossibleMinimumConnectionsWithDiagnostic() {
        DeterministicDungeonGenerator generator = new DeterministicDungeonGenerator(200, 80);
        RoomTemplate start = withMinimumConnections(startRoom(), 2);

        var result = generator.generate(List.of(start, combatRoom("combat", 1)), new DungeonGenerationRequest(2, 1L));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("minimum connection rejects=1"), result.errors().toString());
    }

    private RoomTemplate startRoom() {
        return new RoomTemplate(
            "start",
            RoomCategory.START,
            1,
            Set.of(),
            new IntVector3(5, 4, 5),
            new IntVector3(2, 1, 2),
            List.of(new DoorSocket("north", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)),
            List.of(),
            List.of(),
            Path.of("start.nbt")
        );
    }

    private RoomTemplate combatRoom(String id, int weight) {
        return new RoomTemplate(
            id,
            RoomCategory.COMBAT,
            weight,
            Set.of("test"),
            new IntVector3(5, 4, 5),
            null,
            List.of(
                new DoorSocket("north", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("south", new IntVector3(2, 1, 4), Direction3.SOUTH, SocketType.STANDARD, 1, 2)
            ),
            List.of(),
            List.of(),
            Path.of(id + ".nbt")
        );
    }

    private RoomTemplate largeRoom(String id, RoomCategory category, DoorSocket door) {
        return new RoomTemplate(
            id,
            category,
            1,
            Set.of(),
            new IntVector3(21, 10, 21),
            category == RoomCategory.START ? new IntVector3(10, 1, 10) : null,
            List.of(door),
            List.of(),
            List.of(),
            Path.of(id + ".nbt")
        );
    }

    private RoomTemplate compactRoom(String id, RoomCategory category, int weight, List<DoorSocket> doors) {
        return taggedCompactRoom(id, category, weight, Set.of(), doors);
    }

    private RoomTemplate taggedCompactRoom(String id, RoomCategory category, int weight, Set<String> tags, List<DoorSocket> doors) {
        return new RoomTemplate(
            id,
            category,
            weight,
            tags,
            new IntVector3(5, 4, 5),
            category == RoomCategory.START ? new IntVector3(2, 1, 2) : null,
            doors,
            List.of(),
            List.of(),
            Path.of(id + ".nbt")
        );
    }

    private RoomTemplate multiDoorRoom(String id, RoomCategory category, int weight) {
        return new RoomTemplate(
            id,
            category,
            weight,
            Set.of(),
            new IntVector3(7, 4, 7),
            category == RoomCategory.START ? new IntVector3(3, 1, 3) : null,
            List.of(
                new DoorSocket("north", new IntVector3(3, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("south", new IntVector3(3, 1, 6), Direction3.SOUTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("east", new IntVector3(6, 1, 3), Direction3.EAST, SocketType.STANDARD, 1, 2),
                new DoorSocket("west", new IntVector3(0, 1, 3), Direction3.WEST, SocketType.STANDARD, 1, 2)
            ),
            List.of(),
            List.of(),
            Path.of(id + ".nbt")
        );
    }

    private RoomTemplate withMinimumConnections(RoomTemplate template, int minimumConnections) {
        return new RoomTemplate(template.id(), template.category(), template.weight(), minimumConnections, template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile());
    }

    private DoorSocket taggedDoor(String id, IntVector3 position, Direction3 facing, String tag) {
        return new DoorSocket(id, position, facing, SocketType.STANDARD, 1, 2).withTags(Set.of(tag));
    }

    private DoorTemplate doorTemplate(String id, IntVector3 size, IntVector3 gatewayPosition, IntVector3 gatewaySize, Direction3 gatewayFacing) {
        return new DoorTemplate(
            id,
            size,
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(gatewayPosition, gatewaySize, gatewayFacing),
            Path.of(id + ".nbt")
        );
    }
}
