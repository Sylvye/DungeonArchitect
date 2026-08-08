package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.SocketType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuthoringSession {
    private String roomId;
    private UUID worldId;
    private IntVector3 pos1;
    private IntVector3 pos2;
    private SelectionBounds roomBounds;
    private final List<DoorSocket> doors = new ArrayList<>();
    private final List<RoomMarker> markers = new ArrayList<>();
    private final List<RoomFeatureSlot> featureSlots = new ArrayList<>();

    public AuthoringSession(String roomId) {
        this.roomId = roomId;
    }

    public String roomId() {
        return roomId;
    }

    public void roomId(String roomId) {
        this.roomId = roomId;
    }

    public void setPosition(int index, Location location) {
        worldId = location.getWorld().getUID();
        IntVector3 vector = new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (index == 1) {
            pos1 = vector;
        } else {
            pos2 = vector;
        }
    }

    public Optional<World> world() {
        return worldId == null ? Optional.empty() : Optional.ofNullable(Bukkit.getWorld(worldId));
    }

    public Optional<Bounds> bounds() {
        return roomBounds().map(bounds -> new Bounds(bounds.min(), bounds.max(), bounds.size()));
    }

    public Optional<SelectionBounds> currentSelection() {
        if (pos1 == null || pos2 == null) {
            return Optional.empty();
        }
        return Optional.of(SelectionBounds.between(pos1, pos2));
    }

    public Optional<SelectionBounds> roomBounds() {
        return Optional.ofNullable(roomBounds);
    }

    public void saveCurrentSelectionAsRoomBounds() {
        roomBounds = currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select two corners first"));
    }

    public int nextDoorNumber() {
        return doors.size() + 1;
    }

    public int nextFeatureNumber() {
        return featureSlots.size() + 1;
    }

    public void addDoor(String id, IntVector3 localPosition, Direction3 facing, SocketType socketType, int width, int height) {
        doors.removeIf(door -> door.id().equalsIgnoreCase(id));
        doors.add(new DoorSocket(id, localPosition, facing, socketType, width, height));
    }

    public void addMarker(String name, String type, IntVector3 localPosition) {
        markers.add(new RoomMarker(name, type, localPosition));
    }

    public void addFeatureSlot(String id, String poolId, IntVector3 localPosition, Direction3 facing) {
        featureSlots.removeIf(slot -> slot.id().equalsIgnoreCase(id));
        featureSlots.add(new RoomFeatureSlot(id, poolId, localPosition, facing));
    }

    public void addFeatureSlot(RoomFeatureSlot slot) {
        featureSlots.removeIf(existing -> existing.id().equalsIgnoreCase(slot.id()));
        featureSlots.add(slot);
    }

    public List<DoorSocket> doors() {
        return List.copyOf(doors);
    }

    public List<RoomMarker> markers() {
        return List.copyOf(markers);
    }

    public List<RoomFeatureSlot> featureSlots() {
        return List.copyOf(featureSlots);
    }

    public record Bounds(IntVector3 min, IntVector3 max, IntVector3 size) {
        public boolean contains(IntVector3 worldPosition) {
            return worldPosition.x() >= min.x() && worldPosition.x() <= max.x()
                && worldPosition.y() >= min.y() && worldPosition.y() <= max.y()
                && worldPosition.z() >= min.z() && worldPosition.z() <= max.z();
        }

        public IntVector3 toLocal(IntVector3 worldPosition) {
            return worldPosition.subtract(min);
        }
    }
}
