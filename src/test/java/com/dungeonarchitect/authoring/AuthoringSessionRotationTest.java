package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthoringSessionRotationTest {
    @Test
    void rotatesDoorAndFeatureFacing() {
        AuthoringSession session = new AuthoringSession("room");
        session.addDoorSlot(new DoorSocket("door", new IntVector3(0, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 2));
        session.addFeatureSlot(new RoomFeatureSlot("feature", new IntVector3(1, 1, 1), new IntVector3(2, 2, 2), Direction3.NORTH));

        assertTrue(session.rotateComponent("door", "door", Direction3.EAST, new IntVector3(6, 5, 6)));
        assertTrue(session.rotateComponent("feature", "feature", Direction3.DOWN, new IntVector3(6, 5, 6)));

        assertEquals(Direction3.EAST, session.doors().getFirst().facing());
        assertEquals(new IntVector3(1, 2, 3), session.doors().getFirst().size());
        assertEquals(5, session.doors().getFirst().position().x());
        assertEquals(Direction3.DOWN, session.featureSlots().getFirst().facing());
    }

    @Test
    void rejectsMarkerRotation() {
        AuthoringSession session = new AuthoringSession("room");
        session.addMarker("spawn", "generic", IntVector3.ZERO);

        assertThrows(IllegalArgumentException.class, () -> session.rotateComponent("marker", "spawn", Direction3.NORTH, new IntVector3(2, 2, 2)));
    }

    @Test
    void facesDoorWithoutMovingItWhenItsBoundsTouchThatFace() {
        AuthoringSession session = new AuthoringSession("room");
        session.addDoorSlot(new DoorSocket("door", new IntVector3(0, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 2));

        assertTrue(session.faceComponent("door", "door", Direction3.NORTH, SelectionBounds.between(IntVector3.ZERO, new IntVector3(5, 4, 5))));
        assertEquals(Direction3.NORTH, session.doors().getFirst().facing());
        assertThrows(IllegalArgumentException.class, () -> session.faceComponent("door", "door", Direction3.EAST, SelectionBounds.between(IntVector3.ZERO, new IntVector3(5, 4, 5))));
    }

    @Test
    void rotatesAndFacesGatewayWithinDoorBounds() {
        AuthoringSession session = new AuthoringSession("door");
        session.gateway(new DoorGateway(new IntVector3(0, 1, 0), new IntVector3(3, 2, 1), Direction3.NORTH));

        assertTrue(session.rotateComponent("gateway", "gateway", Direction3.EAST, new IntVector3(6, 5, 6)));
        assertEquals(Direction3.EAST, session.gateway().facing());
        assertEquals(new IntVector3(1, 2, 3), session.gateway().size());
        assertEquals(5, session.gateway().position().x());

        session.gateway(new DoorGateway(new IntVector3(0, 1, 0), new IntVector3(3, 2, 1), Direction3.NORTH));
        assertTrue(session.faceComponent("gateway", "gateway", Direction3.NORTH, SelectionBounds.between(IntVector3.ZERO, new IntVector3(5, 4, 5))));
        assertThrows(IllegalArgumentException.class, () -> session.faceComponent("gateway", "gateway", Direction3.EAST, SelectionBounds.between(IntVector3.ZERO, new IntVector3(5, 4, 5))));
    }

    @Test
    void updatesGatewayBounds() {
        AuthoringSession session = new AuthoringSession("door");
        session.gateway(new DoorGateway(IntVector3.ZERO, new IntVector3(1, 1, 1), Direction3.NORTH));

        assertTrue(session.updateGatewayBounds("gateway", SelectionBounds.between(new IntVector3(1, 2, 3), new IntVector3(3, 4, 3)), Direction3.SOUTH));
        assertEquals(new IntVector3(1, 2, 3), session.gateway().position());
        assertEquals(new IntVector3(3, 3, 1), session.gateway().size());
        assertEquals(Direction3.SOUTH, session.gateway().facing());
        assertTrue(!session.updateGatewayBounds("other", SelectionBounds.between(IntVector3.ZERO, IntVector3.ZERO), Direction3.NORTH));
    }

    @Test
    void rejectsGatewayRotationsThatDoNotFit() {
        AuthoringSession session = new AuthoringSession("door");
        session.gateway(new DoorGateway(IntVector3.ZERO, new IntVector3(3, 4, 1), Direction3.NORTH));

        assertThrows(IllegalArgumentException.class, () -> session.rotateComponent("gateway", "gateway", Direction3.EAST, new IntVector3(6, 3, 6)));
    }
}
