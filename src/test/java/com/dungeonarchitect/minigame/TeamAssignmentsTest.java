package com.dungeonarchitect.minigame;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TeamAssignmentsTest {
    @Test void balancesAllSupportedTeamCountsDeterministically() {
        for (int count = 2; count <= 8; count++) {
            List<UUID> players = new ArrayList<>(); for (int i = 0; i < count * 4 - 1; i++) players.add(new UUID(0, i));
            var teams = TeamAssignments.balanced(players, count, 23);
            assertEquals(teams, TeamAssignments.balanced(players.reversed(), count, 23));
            TeamAssignments.validate(teams);
            var sizes = teams.values().stream().collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.stream.Collectors.counting())).values();
            assertTrue(Collections.max(sizes) - Collections.min(sizes) <= 1);
        }
    }
    @Test void rejectsInvalidAndOversizedTeams() {
        assertThrows(IllegalArgumentException.class, () -> TeamAssignments.validate(Map.of(UUID.randomUUID(), 1)));
        assertThrows(IllegalArgumentException.class, () -> TeamAssignments.balanced(List.of(UUID.randomUUID()), 2, 1));
        assertThrows(IllegalArgumentException.class, () -> TeamAssignments.balanced(Collections.nCopies(10, UUID.randomUUID()), 2, 1));
    }
}
