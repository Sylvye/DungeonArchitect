package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AuthoringManagerComponentSelectionTest {
    @Test
    void selectedComponentBoundsAreDerivedFromMetadataWithoutChangingCurrentSelection() {
        Player player = fakePlayer();
        World world = fakeWorld();
        AuthoringManager manager = manager();
        AuthoringSession session = manager.session(player);
        session.loadTemplateForEdit(template(), world, new IntVector3(10, 80, 10));
        SelectionBounds originalSelection = session.currentSelection().orElseThrow();

        AuthoringManager.ComponentSelection selected = manager.selectComponent(player, "door", "door_a");

        assertEquals(originalSelection, session.currentSelection().orElseThrow());
        assertEquals(SelectionBounds.between(new IntVector3(11, 81, 10), new IntVector3(13, 84, 10)), selected.worldBounds());
        assertEquals(selected, manager.selectedComponentSelection(player).orElseThrow());
    }

    @Test
    void updateComponentBoundsUsesCurrentSelectionAndKeepsComponentSelected() {
        Player player = fakePlayer();
        World world = fakeWorld();
        AuthoringManager manager = manager();
        AuthoringSession session = manager.session(player);
        session.loadTemplateForEdit(template(), world, new IntVector3(10, 80, 10));
        manager.selectComponent(player, "door", "door_a");
        session.setPosition(1, new Location(world, 15, 82, 10));
        session.setPosition(2, new Location(world, 16, 85, 10));

        AuthoringManager.ComponentSelection updated = manager.updateComponentBounds(player, "door", "door_a");

        DoorSocket door = session.doors().getFirst();
        assertEquals(new IntVector3(5, 2, 0), door.position());
        assertEquals(2, door.width());
        assertEquals(4, door.height());
        assertEquals(new AuthoringSession.SelectedComponent("door", "door_a"), session.selectedComponent().orElseThrow());
        assertEquals(SelectionBounds.between(new IntVector3(15, 82, 10), new IntVector3(16, 85, 10)), updated.worldBounds());
    }

    @Test
    void doorEditSessionExposesMarkersAndFeatureSlotsForSelection() {
        Player player = fakePlayer();
        World world = fakeWorld();
        AuthoringManager manager = manager();
        AuthoringSession session = manager.session(player);
        DoorTemplate door = new DoorTemplate(
            "arch",
            new IntVector3(8, 6, 4),
            Set.of(),
            List.of(new RoomMarker("spark", "generic", new IntVector3(2, 2, 1))),
            List.of(new RoomFeatureSlot("trim", new IntVector3(3, 1, 1), new IntVector3(2, 2, 1), Direction3.NORTH)),
            new DoorGateway(new IntVector3(2, 1, 0), new IntVector3(3, 4, 1), Direction3.NORTH),
            Path.of("door.nbt")
        );
        session.loadDoorForEdit(door, world, new IntVector3(100, 80, 100));
        SelectionBounds originalSelection = session.currentSelection().orElseThrow();

        assertEquals(3, manager.componentSelections(player).size());
        assertEquals(List.of("gateway"), manager.componentIds(player, "gateway"));
        AuthoringManager.ComponentSelection selected = manager.selectComponent(player, "feature", "trim");

        assertEquals(originalSelection, session.currentSelection().orElseThrow());
        assertEquals(SelectionBounds.between(new IntVector3(103, 81, 101), new IntVector3(104, 82, 101)), selected.worldBounds());
        assertEquals(selected, manager.selectedComponentSelection(player).orElseThrow());

        AuthoringManager.ComponentSelection gateway = manager.selectComponent(player, "gateway", "gateway");
        assertEquals(SelectionBounds.between(new IntVector3(102, 81, 100), new IntVector3(104, 84, 100)), gateway.worldBounds());
    }

    private AuthoringManager manager() {
        return new AuthoringManager(null, null, Path.of("build/test-rooms"), Path.of("build/test-features"), null, null, Material.BLAZE_ROD, RoomCategory.GENERIC, 10);
    }

    private RoomTemplate template() {
        return new RoomTemplate(
            "room",
            RoomCategory.GENERIC,
            10,
            Set.of(),
            new IntVector3(10, 6, 10),
            null,
            List.of(new DoorSocket("door_a", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 3, 4)),
            List.of(),
            List.of(),
            Path.of("room.nbt")
        );
    }

    private Player fakePlayer() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "toString" -> "fakePlayer";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private World fakeWorld() {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "getName" -> "da_edit";
                case "toString" -> "fakeWorld";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
