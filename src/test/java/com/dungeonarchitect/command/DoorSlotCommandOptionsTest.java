package com.dungeonarchitect.command;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DoorSlotCommandOptionsTest {
    @Test
    void parsesDefaultsWhenOptionalValuesAreOmitted() {
        DoorSlotCommandOptions options = DoorSlotCommandOptions.parse(new String[] {"room", "door"}, 2);

        assertNull(options.id());
        assertEquals(SocketType.STANDARD, options.socketType());
        assertNull(options.facing());
    }

    @Test
    void parsesIdSocketTypeAndFacing() {
        DoorSlotCommandOptions options = DoorSlotCommandOptions.parse(new String[] {"room", "door", "floor_hatch", "stairs_down", "down"}, 2);

        assertEquals("floor_hatch", options.id());
        assertEquals(SocketType.STAIRS_DOWN, options.socketType());
        assertEquals(Direction3.DOWN, options.facing());
    }

    @Test
    void preservesLegacyAddAlias() {
        DoorSlotCommandOptions options = DoorSlotCommandOptions.parse(new String[] {"room", "door", "add", "door_a"}, 2);

        assertEquals("door_a", options.id());
        assertEquals(SocketType.STANDARD, options.socketType());
        assertNull(options.facing());
    }

    @Test
    void rejectsInvalidEnumsAndExtraArguments() {
        assertThrows(IllegalArgumentException.class, () -> DoorSlotCommandOptions.parse(new String[] {"room", "door", "slot", "not_socket"}, 2));
        assertThrows(IllegalArgumentException.class, () -> DoorSlotCommandOptions.parse(new String[] {"room", "door", "slot", "standard", "north", "extra"}, 2));
    }
}
