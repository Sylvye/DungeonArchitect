package com.dungeonarchitect.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoorSocketConnectionRulesTest {
    @Test
    void preservesLegacyTagOverlapWhenNeitherDoorHasPolicy() {
        assertTrue(door("first", Set.of("Stone"), DoorConnectionRules.DEFAULT)
            .compatibleWith(door("second", Set.of("stone"), DoorConnectionRules.DEFAULT)));
        assertFalse(door("first", Set.of("stone"), DoorConnectionRules.DEFAULT)
            .compatibleWith(door("second", Set.of("wood"), DoorConnectionRules.DEFAULT)));
    }

    @Test
    void explicitAllowPolicyPermitsCrossTagConnection() {
        DoorSocket locked = door("locked", Set.of("locked_blue"), new DoorConnectionRules(Set.of("treasure"), Set.of(), false));
        DoorSocket treasure = door("treasure", Set.of("treasure"), DoorConnectionRules.DEFAULT);

        assertTrue(locked.compatibleWith(treasure));
    }

    @Test
    void denyAndMutualPoliciesRejectConnections() {
        DoorSocket locked = door("locked", Set.of("locked_blue"), new DoorConnectionRules(Set.of("treasure"), Set.of(), false));
        DoorSocket denied = door("treasure", Set.of("treasure"), new DoorConnectionRules(Set.of(), Set.of("locked_blue"), false));
        DoorSocket wrongAllow = door("treasure", Set.of("treasure"), new DoorConnectionRules(Set.of("hall"), Set.of(), false));

        assertFalse(locked.compatibleWith(denied));
        assertFalse(locked.compatibleWith(wrongAllow));
    }

    @Test
    void roomPoliciesFilterOppositeRoomsAndAreMutual() {
        DoorSocket first = door("first", Set.of("route"), new DoorConnectionRules(Set.of(), Set.of(), Set.of("Treasure"), Set.of("connector"), false));
        DoorSocket second = door("second", Set.of("route"), new DoorConnectionRules(Set.of(), Set.of(), Set.of("start"), Set.of(), false));

        assertTrue(first.compatibleWith(second, Set.of("start"), Set.of("treasure")));
        assertFalse(first.compatibleWith(second, Set.of("start"), Set.of("connector", "treasure")));
        assertFalse(first.compatibleWith(second, Set.of("entry"), Set.of("treasure")));
    }

    private DoorSocket door(String id, Set<String> tags, DoorConnectionRules rules) {
        return new DoorSocket(id, new IntVector3(0, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)
            .withTags(tags)
            .withConnectionRules(rules);
    }
}
