package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TeamSpreadGenerationTest {
    static RoomTemplate room(String id, RoomCategory category) {
        return new RoomTemplate(id, category, 10, Set.of(), new IntVector3(7, 5, 7), new IntVector3(3, 1, 3), List.of(
            new DoorSocket("n", new IntVector3(3, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
            new DoorSocket("s", new IntVector3(3, 1, 6), Direction3.SOUTH, SocketType.STANDARD, 1, 2),
            new DoorSocket("e", new IntVector3(6, 1, 3), Direction3.EAST, SocketType.STANDARD, 1, 2),
            new DoorSocket("w", new IntVector3(0, 1, 3), Direction3.WEST, SocketType.STANDARD, 1, 2)), List.of(), List.of(), Path.of(id + ".nbt"));
    }
    private final List<RoomTemplate> rooms = List.of(room("root", RoomCategory.START), room("ordinary", RoomCategory.GENERIC), room("spawn", RoomCategory.TEAM_SPAWN), room("other_spawn", RoomCategory.TEAM_SPAWN));
    @Test void producesDeterministicFairSpawnsUsingOnlyChosenTemplate() {
        TeamSpread spread = new TeamSpread("root", Set.of("ordinary"), "spawn", 3, 4, 1.25);
        var generator = new DeterministicDungeonGenerator(25000, 80);
        var request = new DungeonGenerationRequest(20, 47, spread);
        var first = generator.generate(rooms, request); var second = generator.generate(rooms, request);
        assertTrue(first.successful(), first.errors().toString()); assertEquals(first.graph(), second.graph());
        assertEquals(20, first.graph().nodes().size()); assertTrue(first.graph().edges().size() >= 19);
        assertTrue(SpreadReport.measure(first.graph()).accepts(spread));
        assertFalse(first.graph().nodes().stream().anyMatch(n -> n.templateId().equals("other_spawn")));
        assertTrue(new DungeonGraphValidator().validate(first.graph(), rooms).isEmpty());
    }
    @Test void classicGenerationNeverUsesTeamSpawnTemplates() {
        var result = new DeterministicDungeonGenerator(1000, 80).generate(rooms, new DungeonGenerationRequest(10, 3));
        assertTrue(result.successful(), result.errors().toString());
        assertTrue(result.graph().nodes().stream().noneMatch(n -> n.category() == RoomCategory.TEAM_SPAWN));
    }
    @Test void supportsTwoFourAndEightTeams() {
        List<DoorSocket> hubDoors = new ArrayList<>();
        for (int offset : List.of(8, 22)) {
            hubDoors.add(new DoorSocket("n" + offset, new IntVector3(offset, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2));
            hubDoors.add(new DoorSocket("s" + offset, new IntVector3(offset, 1, 30), Direction3.SOUTH, SocketType.STANDARD, 1, 2));
            hubDoors.add(new DoorSocket("e" + offset, new IntVector3(30, 1, offset), Direction3.EAST, SocketType.STANDARD, 1, 2));
            hubDoors.add(new DoorSocket("w" + offset, new IntVector3(0, 1, offset), Direction3.WEST, SocketType.STANDARD, 1, 2));
        }
        RoomTemplate hub = new RoomTemplate("root", RoomCategory.START, 1, Set.of(), new IntVector3(31, 5, 31), null, hubDoors, List.of(), List.of(), Path.of("hub.nbt"));
        for (int teams : List.of(2, 4, 8)) {
            var spread = new TeamSpread("root", Set.of("ordinary"), "spawn", teams, 4, 1.25);
            var result = new DeterministicDungeonGenerator(25000, 80).generate(List.of(hub, rooms.get(1), rooms.get(2)), new DungeonGenerationRequest(teams * 6, 81, spread));
            assertTrue(result.successful(), teams + " teams: " + result.errors());
            assertTrue(SpreadReport.measure(result.graph()).accepts(spread));
        }
    }
    @Test void impossibleDistancesRefuseGenerationAfterBoundedRetries() {
        var spread = new TeamSpread("root", Set.of("ordinary"), "spawn", 2, 99, 1.25);
        var result = new DeterministicDungeonGenerator(30, 80).generate(rooms, new DungeonGenerationRequest(5, 9, spread));
        assertFalse(result.successful()); assertTrue(result.errors().toString().contains("20 attempts"));
    }
    @Test void insufficientRoomCountIsActionable() {
        var spread = new TeamSpread("root", Set.of("ordinary"), "spawn", 8, 4, 1.25);
        assertThrows(IllegalArgumentException.class, () -> new DungeonGenerationRequest(8, 9, spread));
    }
    @Test void shortestRoutesUseConnectionsAndRejectDisconnectedSpawns() {
        List<DungeonNode> nodes = new ArrayList<>();
        for (int i = 0; i < 6; i++) nodes.add(new DungeonNode(i, "r", i == 0 || i == 5 ? RoomCategory.TEAM_SPAWN : RoomCategory.GENERIC, i, new RoomTransform(new IntVector3(i * 100, 80, 0), Rotation.NONE, new IntVector3(3, 3, 3))));
        List<DungeonEdge> edges = new ArrayList<>(); for (int i = 0; i < 5; i++) edges.add(new DungeonEdge(i, "a", i + 1, "b"));
        assertEquals(5, SpreadReport.measure(new DungeonGraph(nodes, edges)).shortest());
        edges.add(new DungeonEdge(0, "c", 5, "d"));
        assertEquals(1, SpreadReport.measure(new DungeonGraph(nodes, edges)).shortest());
        assertEquals(-1, SpreadReport.measure(new DungeonGraph(nodes, List.of())).shortest());
    }
    @Test void ratioBoundaryIsInclusive() {
        var spread = new TeamSpread("root", Set.of("ordinary"), "spawn", 3, 4, 1.25);
        assertTrue(new SpreadReport(List.of(1, 2, 3), Map.of(), 4, 5).accepts(spread));
        assertFalse(new SpreadReport(List.of(1, 2, 3), Map.of(), 4, 6).accepts(spread));
    }
}
