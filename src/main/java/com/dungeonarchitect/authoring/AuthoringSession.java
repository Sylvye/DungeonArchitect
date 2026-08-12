package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.domain.TagDomain;
import com.dungeonarchitect.gui.TagCleanupService;
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
    private int minimumConnections;
    private Set<String> tags = new LinkedHashSet<>();
    private IntVector3 spawn;
    private boolean editingExistingRoom;
    private boolean featureSession;
    private boolean doorSession;
    private boolean editingExistingFeature;
    private boolean editingExistingDoor;
    private DoorGateway gateway;
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

    public boolean roomSession() {
        return !featureSession && !doorSession;
    }

    public boolean featureSession() {
        return featureSession;
    }

    public boolean editingExistingFeature() {
        return editingExistingFeature;
    }

    public boolean doorSession() {
        return doorSession;
    }

    public boolean editingExistingDoor() {
        return editingExistingDoor;
    }

    public void featureSession(boolean featureSession) {
        this.featureSession = featureSession;
        if (featureSession) {
            doorSession = false;
        }
    }

    public void doorSession(boolean doorSession) {
        this.doorSession = doorSession;
        if (doorSession) {
            featureSession = false;
        }
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

    public int minimumConnections() {
        return minimumConnections;
    }

    public void minimumConnections(int minimumConnections) {
        this.minimumConnections = minimumConnections;
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
    }

    public void selectComponent(String type, String id) {
        selectedComponent = new SelectedComponent(type, id);
    }

    public Optional<SelectedComponent> selectedComponent() {
        return Optional.ofNullable(selectedComponent);
    }

    public void clearSelectedComponent() {
        selectedComponent = null;
    }

    public void loadTemplateForEdit(RoomTemplate template, World world, IntVector3 origin) {
        roomId = template.id();
        worldId = world.getUID();
        featureSession = false;
        doorSession = false;
        editingExistingFeature = false;
        editingExistingDoor = false;
        gateway = null;
        roomBounds = SelectionBounds.between(origin, origin.add(template.size()).subtract(new IntVector3(1, 1, 1)));
        pos1 = roomBounds.min();
        pos2 = roomBounds.max();
        category = template.category();
        weight = template.weight();
        minimumConnections = template.minimumConnections();
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
        doorSession = false;
        editingExistingFeature = true;
        editingExistingRoom = false;
        editingExistingDoor = false;
        gateway = null;
        roomBounds = SelectionBounds.between(origin, origin.add(template.size()).subtract(new IntVector3(1, 1, 1)));
        pos1 = roomBounds.min();
        pos2 = roomBounds.max();
        tags = new LinkedHashSet<>(template.tags());
        doors.clear();
        markers.clear();
        featureSlots.clear();
    }

    public void loadDoorForEdit(DoorTemplate template, World world, IntVector3 origin) {
        roomId = template.id();
        worldId = world.getUID();
        featureSession = false;
        doorSession = true;
        editingExistingFeature = false;
        editingExistingRoom = false;
        editingExistingDoor = true;
        roomBounds = SelectionBounds.between(origin, origin.add(template.size()).subtract(new IntVector3(1, 1, 1)));
        pos1 = roomBounds.min();
        pos2 = roomBounds.max();
        tags = new LinkedHashSet<>(template.tags());
        gateway = template.gateway();
        doors.clear();
        markers.clear();
        markers.addAll(template.markers());
        featureSlots.clear();
        featureSlots.addAll(template.featureSlots());
    }

    public DoorGateway gateway() {
        return gateway;
    }

    public void gateway(DoorGateway gateway) {
        this.gateway = gateway;
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

    public void addDoorSlot(String id, IntVector3 localPosition, IntVector3 size, Direction3 facing) {
        addDoorSlot(id, localPosition, size, facing, SocketType.STANDARD);
    }

    public void addDoorSlot(String id, IntVector3 localPosition, IntVector3 size, Direction3 facing, SocketType socketType) {
        doors.removeIf(door -> door.id().equalsIgnoreCase(id));
        doors.add(new DoorSocket(id, localPosition, facing, socketType, displayWidth(facing, size), displayHeight(facing, size), size, java.util.Set.of(), java.util.List.of()));
    }

    public void addDoorSlot(DoorSocket slot) {
        doors.removeIf(door -> door.id().equalsIgnoreCase(slot.id()));
        doors.add(slot);
    }

    public void addMarker(String name, String type, IntVector3 localPosition) {
        markers.add(new RoomMarker(name, type, localPosition));
    }

    public boolean removeDoor(String id) {
        boolean removed = doors.removeIf(door -> door.id().equalsIgnoreCase(id));
        clearSelectedComponent("door", id, removed);
        return removed;
    }

    public boolean renameDoor(String oldId, String newId) {
        if (doors.stream().anyMatch(door -> door.id().equalsIgnoreCase(newId))) {
            throw new IllegalArgumentException("Door already exists: " + newId);
        }
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(oldId)) {
                doors.set(i, new DoorSocket(newId, door.position(), door.facing(), door.socketType(), door.width(), door.height(), door.size(), door.tags(), door.entries(), door.connectionRules()));
                selectedComponent = null;
                return true;
            }
        }
        return false;
    }

    public boolean updateDoorBounds(String id, SelectionBounds localBounds, Direction3 facing) {
        IntVector3 size = localBounds.size();
        int width = switch (facing) {
            case NORTH, SOUTH -> size.x();
            case EAST, WEST -> size.z();
            case UP, DOWN -> size.x();
        };
        int height = switch (facing) {
            case NORTH, SOUTH, EAST, WEST -> size.y();
            case UP, DOWN -> size.z();
        };
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(id)) {
                doors.set(i, new DoorSocket(door.id(), localBounds.min(), facing, door.socketType(), width, height, localBounds.size(), door.tags(), door.entries(), door.connectionRules()));
                return true;
            }
        }
        return false;
    }

    public boolean removeMarker(String id) {
        boolean removed = markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
        clearSelectedComponent("marker", id, removed);
        return removed;
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

    public boolean updateMarkerPosition(String id, IntVector3 localPosition) {
        for (int i = 0; i < markers.size(); i++) {
            RoomMarker marker = markers.get(i);
            if (marker.name().equalsIgnoreCase(id)) {
                markers.set(i, new RoomMarker(marker.name(), marker.type(), localPosition));
                return true;
            }
        }
        return false;
    }

    public boolean removeFeature(String id) {
        boolean removed = featureSlots.removeIf(slot -> slot.id().equalsIgnoreCase(id));
        clearSelectedComponent("feature", id, removed);
        return removed;
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

    public boolean updateFeatureSlotBounds(String id, SelectionBounds localBounds) {
        for (int i = 0; i < featureSlots.size(); i++) {
            RoomFeatureSlot slot = featureSlots.get(i);
            if (slot.id().equalsIgnoreCase(id)) {
                featureSlots.set(i, new RoomFeatureSlot(slot.id(), localBounds.min(), localBounds.size(), slot.facing(), slot.entries()));
                return true;
            }
        }
        return false;
    }

    public boolean updateGatewayBounds(String id, SelectionBounds localBounds, Direction3 facing) {
        if (gateway == null || !id.equalsIgnoreCase("gateway")) {
            return false;
        }
        gateway = new DoorGateway(localBounds.min(), localBounds.size(), facing);
        return true;
    }

    public boolean rotateComponent(String type, String id, Direction3 facing, IntVector3 roomSize) {
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> rotateDoor(id, facing, roomSize);
            case "feature" -> rotateFeatureSlot(id, facing);
            case "gateway" -> rotateGateway(id, facing, roomSize);
            case "marker" -> throw new IllegalArgumentException("Markers cannot be rotated");
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
    }

    public boolean faceComponent(String type, String id, Direction3 facing, SelectionBounds roomBounds) {
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> faceDoor(id, facing, roomBounds);
            case "feature" -> rotateFeatureSlot(id, facing);
            case "gateway" -> faceGateway(id, facing, roomBounds);
            case "marker" -> throw new IllegalArgumentException("Markers cannot have a facing direction");
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
    }

    public void addFeatureSlot(String id, String poolId, IntVector3 localPosition, Direction3 facing) {
        featureSlots.removeIf(slot -> slot.id().equalsIgnoreCase(id));
        featureSlots.add(new RoomFeatureSlot(id, poolId, localPosition, facing));
    }

    public void addFeatureSlot(RoomFeatureSlot slot) {
        featureSlots.removeIf(existing -> existing.id().equalsIgnoreCase(slot.id()));
        featureSlots.add(slot);
    }

    public boolean updateDoorEntries(String id, List<DoorSlotEntry> entries) {
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(id)) {
                doors.set(i, door.withEntries(entries));
                return true;
            }
        }
        return false;
    }

    public List<DoorSocket> doors() {
        return List.copyOf(doors);
    }

    public void removeTag(TagDomain domain, String tag) {
        if (domain == TagDomain.ROOM && roomSession()) {
            tags = new LinkedHashSet<>(TagCleanupService.without(tags, tag));
        }
        if (domain == TagDomain.DOOR && doorSession()) {
            tags = new LinkedHashSet<>(TagCleanupService.without(tags, tag));
        }
        if (!roomSession()) {
            return;
        }
        for (int i = 0; i < doors.size(); i++) {
            doors.set(i, TagCleanupService.remove(domain, tag, doors.get(i)));
        }
    }

    public List<RoomMarker> markers() {
        return List.copyOf(markers);
    }

    public List<RoomFeatureSlot> featureSlots() {
        return List.copyOf(featureSlots);
    }

    private boolean rotateDoor(String id, Direction3 facing, IntVector3 roomSize) {
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(id)) {
                int depth = depth(door);
                IntVector3 size = sizeFor(facing, door.width(), door.height(), depth);
                if (size.x() > roomSize.x() || size.y() > roomSize.y() || size.z() > roomSize.z()) {
                    throw new IllegalArgumentException("Door " + id + " cannot rotate to " + facing + " because it does not fit within the room bounds");
                }
                IntVector3 position = new IntVector3(
                    relativeOrigin(door.position().x(), door.size().x(), roomSize.x(), size.x()),
                    relativeOrigin(door.position().y(), door.size().y(), roomSize.y(), size.y()),
                    relativeOrigin(door.position().z(), door.size().z(), roomSize.z(), size.z())
                );
                position = switch (facing) {
                    case NORTH -> new IntVector3(position.x(), position.y(), 0);
                    case SOUTH -> new IntVector3(position.x(), position.y(), roomSize.z() - size.z());
                    case EAST -> new IntVector3(roomSize.x() - size.x(), position.y(), position.z());
                    case WEST -> new IntVector3(0, position.y(), position.z());
                    case UP -> new IntVector3(position.x(), roomSize.y() - size.y(), position.z());
                    case DOWN -> new IntVector3(position.x(), 0, position.z());
                };
                doors.set(i, new DoorSocket(door.id(), position, facing, door.socketType(), door.width(), door.height(), size, door.tags(), door.entries(), door.connectionRules()));
                return true;
            }
        }
        return false;
    }

    private boolean faceDoor(String id, Direction3 facing, SelectionBounds roomBounds) {
        for (int i = 0; i < doors.size(); i++) {
            DoorSocket door = doors.get(i);
            if (door.id().equalsIgnoreCase(id)) {
                SelectionBounds bounds = SelectionBounds.between(door.position(), door.position().add(door.size()).subtract(new IntVector3(1, 1, 1)));
                com.dungeonarchitect.door.BoundaryFacing.requireValidFace(facing, bounds, roomBounds, "Door " + id);
                int width = switch (facing) {
                    case NORTH, SOUTH, UP, DOWN -> door.size().x();
                    case EAST, WEST -> door.size().z();
                };
                int height = switch (facing) {
                    case NORTH, SOUTH, EAST, WEST -> door.size().y();
                    case UP, DOWN -> door.size().z();
                };
                doors.set(i, new DoorSocket(door.id(), door.position(), facing, door.socketType(), width, height, door.size(), door.tags(), door.entries(), door.connectionRules()));
                return true;
            }
        }
        return false;
    }

    private boolean rotateGateway(String id, Direction3 facing, IntVector3 roomSize) {
        if (gateway == null || !id.equalsIgnoreCase("gateway")) {
            return false;
        }
        IntVector3 size = rotatedGatewaySize(gateway, facing);
        if (size.x() > roomSize.x() || size.y() > roomSize.y() || size.z() > roomSize.z()) {
            throw new IllegalArgumentException("Gateway cannot rotate to " + facing + " because it does not fit within the door bounds");
        }
        IntVector3 position = reorientedPosition(gateway.position(), gateway.size(), roomSize, size, facing);
        gateway = new DoorGateway(position, size, facing);
        return true;
    }

    private boolean faceGateway(String id, Direction3 facing, SelectionBounds roomBounds) {
        if (gateway == null || !id.equalsIgnoreCase("gateway")) {
            return false;
        }
        SelectionBounds bounds = SelectionBounds.between(gateway.position(), gateway.position().add(gateway.size()).subtract(new IntVector3(1, 1, 1)));
        com.dungeonarchitect.door.BoundaryFacing.requireValidFace(facing, bounds, roomBounds, "Gateway");
        gateway = new DoorGateway(gateway.position(), gateway.size(), facing);
        return true;
    }

    private static int depth(DoorSocket door) {
        return switch (door.facing()) {
            case NORTH, SOUTH -> door.size().z();
            case EAST, WEST -> door.size().x();
            case UP, DOWN -> door.size().y();
        };
    }

    private static IntVector3 sizeFor(Direction3 facing, int width, int height, int depth) {
        return switch (facing) {
            case NORTH, SOUTH -> new IntVector3(width, height, depth);
            case EAST, WEST -> new IntVector3(depth, height, width);
            case UP, DOWN -> new IntVector3(width, depth, height);
        };
    }

    private static IntVector3 rotatedGatewaySize(DoorGateway gateway, Direction3 facing) {
        int width = switch (gateway.facing()) {
            case NORTH, SOUTH, UP, DOWN -> gateway.size().x();
            case EAST, WEST -> gateway.size().z();
        };
        int height = switch (gateway.facing()) {
            case NORTH, SOUTH, EAST, WEST -> gateway.size().y();
            case UP, DOWN -> gateway.size().z();
        };
        int depth = switch (gateway.facing()) {
            case NORTH, SOUTH -> gateway.size().z();
            case EAST, WEST -> gateway.size().x();
            case UP, DOWN -> gateway.size().y();
        };
        return sizeFor(facing, width, height, depth);
    }

    private static IntVector3 reorientedPosition(IntVector3 oldPosition, IntVector3 oldSize, IntVector3 roomSize, IntVector3 newSize, Direction3 facing) {
        IntVector3 position = new IntVector3(
            relativeOrigin(oldPosition.x(), oldSize.x(), roomSize.x(), newSize.x()),
            relativeOrigin(oldPosition.y(), oldSize.y(), roomSize.y(), newSize.y()),
            relativeOrigin(oldPosition.z(), oldSize.z(), roomSize.z(), newSize.z())
        );
        return switch (facing) {
            case NORTH -> new IntVector3(position.x(), position.y(), 0);
            case SOUTH -> new IntVector3(position.x(), position.y(), roomSize.z() - newSize.z());
            case EAST -> new IntVector3(roomSize.x() - newSize.x(), position.y(), position.z());
            case WEST -> new IntVector3(0, position.y(), position.z());
            case UP -> new IntVector3(position.x(), roomSize.y() - newSize.y(), position.z());
            case DOWN -> new IntVector3(position.x(), 0, position.z());
        };
    }

    private static int relativeOrigin(int oldOrigin, int oldSize, int roomSize, int newSize) {
        if (roomSize == newSize) {
            return 0;
        }
        double oldCenter = oldOrigin + (oldSize - 1) / 2.0;
        double fraction = roomSize == 1 ? 0 : oldCenter / (roomSize - 1);
        int origin = (int) Math.round(fraction * (roomSize - newSize));
        return Math.max(0, Math.min(roomSize - newSize, origin));
    }

    private boolean rotateFeatureSlot(String id, Direction3 facing) {
        for (int i = 0; i < featureSlots.size(); i++) {
            RoomFeatureSlot slot = featureSlots.get(i);
            if (slot.id().equalsIgnoreCase(id)) {
                featureSlots.set(i, new RoomFeatureSlot(slot.id(), slot.position(), slot.size(), facing, slot.entries()));
                return true;
            }
        }
        return false;
    }

    private void clearSelectedComponent(String type, String id, boolean changed) {
        if (!changed || selectedComponent == null) {
            return;
        }
        if (selectedComponent.type().equalsIgnoreCase(type) && selectedComponent.id().equalsIgnoreCase(id)) {
            selectedComponent = null;
        }
    }

    private static int displayWidth(Direction3 facing, IntVector3 size) {
        return switch (facing) {
            case NORTH, SOUTH -> size.x();
            case EAST, WEST -> size.z();
            case UP, DOWN -> size.x();
        };
    }

    private static int displayHeight(Direction3 facing, IntVector3 size) {
        return switch (facing) {
            case NORTH, SOUTH, EAST, WEST -> size.y();
            case UP, DOWN -> size.z();
        };
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
