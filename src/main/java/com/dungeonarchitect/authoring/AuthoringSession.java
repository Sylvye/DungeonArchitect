package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuthoringSession {
    private String roomId;
    private UUID worldId;
    private IntVector3 pos1;
    private IntVector3 pos2;
    private SelectionBounds roomBounds;
    private RoomCategory category;
    private int weight;
    private Set<String> tags = new LinkedHashSet<>();
    private IntVector3 spawn;
    private boolean editingExistingRoom;
    private boolean featureSession;
    private boolean editingExistingFeature;
    private SelectedComponent selectedComponent;
    private final List<DoorSocket> doors = new ArrayList<>();
    private final List<RoomMarker> markers = new ArrayList<>();
    private final List<RoomFeatureSlot> featureSlots = new ArrayList<>();

    public AuthoringSession(String roomId) {
        this.roomId = roomId;
        this.category = RoomCategory.GENERIC;
        this.weight = 10;
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
        selectedComponent = null;
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

    public boolean editingExistingRoom() {
        return editingExistingRoom;
    }

    public boolean featureSession() {
        return featureSession;
    }

    public boolean editingExistingFeature() {
        return editingExistingFeature;
    }

    public void featureSession(boolean featureSession) {
        this.featureSession = featureSession;
    }

    public RoomCategory category() {
        return category;
    }

    public void category(RoomCategory category) {
        this.category = category;
    }

    public int weight() {
        return weight;
    }

    public void weight(int weight) {
        this.weight = weight;
    }

    public Set<String> tags() {
        return Set.copyOf(tags);
    }

    public void tags(Set<String> tags) {
        this.tags = new LinkedHashSet<>(tags);
    }

    public IntVector3 spawn() {
        return spawn;
    }

    public void spawn(IntVector3 spawn) {
        this.spawn = spawn;
    }

    public void saveCurrentSelectionAsRoomBounds() {
        roomBounds = currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select two corners first"));
    }

    public void selectCurrentBounds(SelectionBounds bounds) {
        pos1 = bounds.min();
        pos2 = bounds.max();
        selectedComponent = null;
    }

    public void selectComponentBounds(String type, String id, SelectionBounds bounds) {
        pos1 = bounds.min();
        pos2 = bounds.max();
        selectedComponent = new SelectedComponent(type, id);
    }

    public Optional<SelectedComponent> selectedComponent() {
        return Optional.ofNullable(selectedComponent);
    }

    public void loadTemplateForEdit(RoomTemplate template, World world, IntVector3 origin) {
        roomId = template.id();
        worldId = world.getUID();
        featureSession = false;
        editingExistingFeature = false;
        roomBounds = SelectionBounds.between(origin, origin.add(template.size()).subtract(new IntVector3(1, 1, 1)));
        pos1 = roomBounds.min();
        pos2 = roomBounds.max();
        category = template.category();
        weight = template.weight();
        tags = new LinkedHashSet<>(template.tags());
        spawn = template.spawn();
        doors.clear();
        doors.addAll(template.doors());
        markers.clear();
        markers.addAll(template.markers());
        featureSlots.clear();
        featureSlots.addAll(template.featureSlots());
        editingExistingRoom = true;
    }

    public void loadFeatureForEdit(FeatureTemplate template, World world, IntVector3 origin) {
        roomId = template.id();
        worldId = world.getUID();
        featureSession = true;
        editingExistingFeature = true;
        editingExistingRoom = false;
        roomBounds = SelectionBounds.between(origin, origin.add(template.size()).subtract(new IntVector3(1, 1, 1)));
        pos1 = roomBounds.min();
        pos2 = roomBounds.max();
        tags = new LinkedHashSet<>(template.tags());
        doors.clear();
        markers.clear();
        featureSlots.clear();
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

    public boolean removeDoor(String id) {
        return doors.removeIf(door -> door.id().equalsIgnoreCase(id));
    }

    public boolean renameDoor(String oldId, String newId) {
        if (doors.stream().anyMatch(door -> door.id().equalsIgnoreCase(newId))) {
            throw new IllegalArgumentException("Door already exists: " + newId);
        }
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(oldId)) {
                doors.set(i, new DoorSocket(newId, door.position(), door.facing(), door.socketType(), door.width(), door.height()));
                selectedComponent = null;
                return true;
            }
        }
        return false;
    }

    public boolean removeMarker(String id) {
        return markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
    }

    public boolean renameMarker(String oldId, String newId) {
        if (markers.stream().anyMatch(marker -> marker.name().equalsIgnoreCase(newId))) {
            throw new IllegalArgumentException("Marker already exists: " + newId);
        }
        for (int i = 0; i < markers.size(); i++) {
            RoomMarker marker = markers.get(i);
            if (marker.name().equalsIgnoreCase(oldId)) {
                markers.set(i, new RoomMarker(newId, marker.type(), marker.position()));
                selectedComponent = null;
                return true;
            }
        }
        return false;
    }

    public boolean removeFeature(String id) {
        return featureSlots.removeIf(slot -> slot.id().equalsIgnoreCase(id));
    }

    public boolean renameFeatureSlot(String oldId, String newId) {
        if (featureSlots.stream().anyMatch(slot -> slot.id().equalsIgnoreCase(newId))) {
            throw new IllegalArgumentException("Feature slot already exists: " + newId);
        }
        for (int i = 0; i < featureSlots.size(); i++) {
            RoomFeatureSlot slot = featureSlots.get(i);
            if (slot.id().equalsIgnoreCase(oldId)) {
                featureSlots.set(i, new RoomFeatureSlot(newId, slot.position(), slot.size(), slot.facing(), slot.entries()));
                selectedComponent = null;
                return true;
            }
        }
        return false;
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

    public record SelectedComponent(String type, String id) {
    }
}
