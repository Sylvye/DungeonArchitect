package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
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
        assertTrue(errors.stream().anyMatch(error -> error.contains("not adjacent")));
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
}
