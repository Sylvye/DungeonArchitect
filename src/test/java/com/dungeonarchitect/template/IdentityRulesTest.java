package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class IdentityRulesTest {
    @Test
    void roomComponentsShareOneCaseInsensitiveNamespace() {
        DoorSocket door = new DoorSocket("North", IntVector3.ZERO, Direction3.NORTH, SocketType.STANDARD, 1, 2);
        assertThrows(IllegalArgumentException.class, () -> IdentityRules.requireRoomComponentAvailable(" north ", List.of(door), List.of(), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> IdentityRules.requireRoomComponentAvailable("north", List.of(), List.of(new RoomMarker("NORTH", "generic", IntVector3.ZERO)), List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> IdentityRules.requireRoomComponentAvailable("north", List.of(), List.of(), List.of(new RoomFeatureSlot("North", IntVector3.ZERO, new IntVector3(1, 1, 1), Direction3.NORTH)), null));
    }

    @Test
    void doorGatewayNameIsReservedAndExistingCollisionsAreInvalid() {
        assertThrows(IllegalArgumentException.class, () -> IdentityRules.requireDoorComponentAvailable("gateway", List.of(), List.of(), null));
        TemplateValidationResult result = new TemplateValidationResult();
        IdentityRules.validateFeatureMarkers("feature", List.of(new RoomMarker("Chest", "generic", IntVector3.ZERO), new RoomMarker(" chest ", "generic", new IntVector3(1, 0, 0))), result);
        assertFalse(result.valid());
    }
}
