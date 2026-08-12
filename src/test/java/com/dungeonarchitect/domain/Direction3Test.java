package com.dungeonarchitect.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Direction3Test {
    @Test
    void parsesAbbreviatedAndFullDirections() {
        assertEquals(Direction3.NORTH, Direction3.parse("N"));
        assertEquals(Direction3.WEST, Direction3.parse("west"));
        assertEquals(Direction3.UP, Direction3.parse("UP"));
        assertEquals(Direction3.DOWN, Direction3.parse("down"));
    }

    @Test
    void rejectsUnknownDirections() {
        assertThrows(IllegalArgumentException.class, () -> Direction3.parse("northeast"));
    }
}
