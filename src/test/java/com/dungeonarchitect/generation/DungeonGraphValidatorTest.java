package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonGraphValidatorTest {
    @Test
    void reportsRoomsBelowTheirMinimumConnectionCount() {
        RoomTemplate template = new RoomTemplate(
            "connector",
            RoomCategory.COMBAT,
            1,
            2,
            Set.of(),
            new IntVector3(5, 4, 5),
            null,
            List.of(
                new DoorSocket("north", new IntVector3(2, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("south", new IntVector3(2, 1, 4), Direction3.SOUTH, SocketType.STANDARD, 1, 2)
            ),
            List.of(),
            List.of(),
            Path.of("connector.nbt")
        );
        DungeonGraph graph = new DungeonGraph(
            List.of(new DungeonNode(0, "connector", RoomCategory.COMBAT, 0, new RoomTransform(IntVector3.ZERO, Rotation.NONE, template.size()))),
            List.of()
        );

        List<String> errors = new DungeonGraphValidator().validate(graph, List.of(template));

        assertTrue(errors.stream().anyMatch(error -> error.equals("Room 0 (connector) has 0 connections; requires 2")), errors.toString());
    }
}
