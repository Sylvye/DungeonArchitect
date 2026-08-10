package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.door.BoundaryFacing;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateValidator;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateValidator;
import com.dungeonarchitect.gui.GuiItems;
import com.dungeonarchitect.runtime.RoomStructurePlacer;
import com.dungeonarchitect.runtime.VoidChunkGenerator;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateValidator;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.util.BukkitVectors;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class AuthoringManager {
    private static final String EDIT_WORLD_NAME = "da_edit";
    private static final int CLEAR_BLOCKS_PER_TICK = 250_000;
    public static final Material SELECTOR_MATERIAL = Material.BREEZE_ROD;
    private final Plugin plugin;
    private final Server server;
    private final Path roomsDirectory;
    private final Path featuresDirectory;
    private final Path doorsDirectory;
    private final EditWorkspaceStore workspaceStore;
    private final NamespacedKey wandKey;
    private final NamespacedKey selectorKey;
    private final Material wandMaterial;
    private final RoomCategory defaultCategory;
    private final int defaultWeight;
    private final Map<UUID, AuthoringSession> sessions = new HashMap<>();
    private final Map<UUID, Boolean> preparingWorkspaces = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();
    private final FeatureTemplateValidator featureValidator;
    private final DoorTemplateValidator doorValidator;

    public AuthoringManager(Plugin plugin, Server server, Path roomsDirectory, Path featuresDirectory, NamespacedKey wandKey, NamespacedKey selectorKey, Material wandMaterial, RoomCategory defaultCategory, int defaultWeight) {
        this.plugin = plugin;
        this.server = server;
        this.roomsDirectory = roomsDirectory;
        this.featuresDirectory = featuresDirectory;
        this.doorsDirectory = roomsDirectory.getParent().resolve("doors");
        this.workspaceStore = new EditWorkspaceStore(roomsDirectory.getParent().resolve("edit-workspaces.yml"));
        this.wandKey = wandKey;
        this.selectorKey = selectorKey;
        this.wandMaterial = wandMaterial;
        this.defaultCategory = defaultCategory;
        this.defaultWeight = defaultWeight;
        this.featureValidator = new FeatureTemplateValidator(new com.dungeonarchitect.template.RoomStructureService(server));
        this.doorValidator = new DoorTemplateValidator(new com.dungeonarchitect.template.RoomStructureService(server));
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(wandMaterial);
        var meta = item.getItemMeta();
        meta.displayName(GuiItems.text("Architect's Wand", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != wandMaterial || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public ItemStack createSelector() {
        ItemStack item = new ItemStack(SELECTOR_MATERIAL);
        var meta = item.getItemMeta();
        meta.displayName(GuiItems.text("Architect's Selector", net.kyori.adventure.text.format.NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSelector(ItemStack item) {
        if (item == null || item.getType() != SELECTOR_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }

    public AuthoringSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new AuthoringSession("unnamed_room"));
    }

    public CompletableFuture<AuthoringSession> createSession(Player player, String roomId) {
        return prepareWorkspace(player).thenApply(workspace -> {
            placeScaffold(player, editWorld(), workspace.buildOrigin());
            AuthoringSession session = new AuthoringSession(roomId);
            session.featureSession(false);
            session.category(defaultCategory);
            session.weight(defaultWeight);
            sessions.put(player.getUniqueId(), session);
            teleportToWorkspace(player, workspace);
            return session;
        });
    }

    public CompletableFuture<AuthoringSession> createFeatureSession(Player player, String featureId) {
        if (featureId.equalsIgnoreCase(com.dungeonarchitect.domain.FeatureSlotEntry.EMPTY)) {
            throw new IllegalArgumentException("empty is reserved");
        }
        return prepareWorkspace(player).thenApply(workspace -> {
            placeScaffold(player, editWorld(), workspace.buildOrigin());
            AuthoringSession session = new AuthoringSession(featureId);
            session.featureSession(true);
            sessions.put(player.getUniqueId(), session);
            teleportToWorkspace(player, workspace);
            return session;
        });
    }

    public CompletableFuture<AuthoringSession> createDoorSession(Player player, String doorId) {
        if (doorId.equalsIgnoreCase(com.dungeonarchitect.domain.DoorSlotEntry.EMPTY)) {
            throw new IllegalArgumentException("empty is reserved");
        }
        return prepareWorkspace(player).thenApply(workspace -> {
            placeScaffold(player, editWorld(), workspace.buildOrigin());
            AuthoringSession session = new AuthoringSession(doorId);
            session.doorSession(true);
            sessions.put(player.getUniqueId(), session);
            teleportToWorkspace(player, workspace);
            return session;
        });
    }

    public CompletableFuture<AuthoringSession> editSession(Player player, RoomTemplate template) {
        return prepareWorkspace(player).thenApply(workspace -> {
            try {
                if (!workspace.containsTemplate(template.size())) {
                    throw new IOException("Room " + template.id() + " size " + template.size() + " is too large for the edit workspace.");
                }
                World world = editWorld();
                Structure structure = server.getStructureManager().loadStructure(template.structureFile().toFile());
                var size = structure.getSize();
                IntVector3 nbtSize = new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
                if (!nbtSize.equals(template.size())) {
                    throw new IOException("room.nbt size " + nbtSize + " does not match room.yml size " + template.size() + ". Re-save this room from the original build area first.");
                }
                SelectionBounds footprint = templateBounds(workspace.buildOrigin(), template.size());
                placeSupportPlatform(player, world, footprint);
                pasteStructure(player, world, structure, workspace.buildOrigin(), template.size());
                AuthoringSession session = new AuthoringSession(template.id());
                session.loadTemplateForEdit(template, world, workspace.buildOrigin());
                sessions.put(player.getUniqueId(), session);
                teleportToWorkspace(player, workspace);
                return session;
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    public CompletableFuture<AuthoringSession> editFeatureSession(Player player, FeatureTemplate template) {
        return prepareWorkspace(player).thenApply(workspace -> {
            try {
                if (!workspace.containsTemplate(template.size())) {
                    throw new IOException("Feature " + template.id() + " size " + template.size() + " is too large for the edit workspace.");
                }
                World world = editWorld();
                Structure structure = server.getStructureManager().loadStructure(template.structureFile().toFile());
                var size = structure.getSize();
                IntVector3 nbtSize = new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
                if (!nbtSize.equals(template.size())) {
                    throw new IOException("feature.nbt size " + nbtSize + " does not match feature.yml size " + template.size() + ". Re-save this feature first.");
                }
                SelectionBounds footprint = templateBounds(workspace.buildOrigin(), template.size());
                placeSupportPlatform(player, world, footprint);
                pasteStructure(player, world, structure, workspace.buildOrigin(), template.size());
                AuthoringSession session = new AuthoringSession(template.id());
                session.loadFeatureForEdit(template, world, workspace.buildOrigin());
                sessions.put(player.getUniqueId(), session);
                teleportToWorkspace(player, workspace);
                return session;
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    public CompletableFuture<AuthoringSession> editDoorSession(Player player, DoorTemplate template) {
        return prepareWorkspace(player).thenApply(workspace -> {
            try {
                if (!workspace.containsTemplate(template.size())) {
                    throw new IOException("Door " + template.id() + " size " + template.size() + " is too large for the edit workspace.");
                }
                World world = editWorld();
                Structure structure = server.getStructureManager().loadStructure(template.structureFile().toFile());
                var size = structure.getSize();
                IntVector3 nbtSize = new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
                if (!nbtSize.equals(template.size())) {
                    throw new IOException("door.nbt size " + nbtSize + " does not match door.yml size " + template.size() + ". Re-save this door first.");
                }
                SelectionBounds footprint = templateBounds(workspace.buildOrigin(), template.size());
                placeSupportPlatform(player, world, footprint);
                pasteStructure(player, world, structure, workspace.buildOrigin(), template.size());
                AuthoringSession session = new AuthoringSession(template.id());
                session.loadDoorForEdit(template, world, workspace.buildOrigin());
                sessions.put(player.getUniqueId(), session);
                teleportToWorkspace(player, workspace);
                return session;
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        });
    }

    public void setSelection(Player player, int index, Location location) {
        if (!isEditWorld(location.getWorld())) {
            throw new IllegalStateException("The wand only works in da_edit");
        }
        session(player).setPosition(index, location);
    }

    public SelectionBounds saveCurrentSelectionAsRoomBounds(Player player) {
        AuthoringSession session = session(player);
        session.saveCurrentSelectionAsRoomBounds();
        SelectionBounds bounds = session.roomBounds().orElseThrow();
        session.world().ifPresent(world -> placeSupportPlatform(player, world, bounds));
        return bounds;
    }

    public Optional<SelectionBounds> currentSelection(Player player) {
        return session(player).currentSelection();
    }

    public Optional<SelectionBounds> roomBounds(Player player) {
        return session(player).roomBounds();
    }

    public Optional<String> activeRoomId(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return session == null || session.featureSession() ? Optional.empty() : Optional.of(session.roomId());
    }

    public Optional<String> activeFeatureId(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return session == null || !session.featureSession() ? Optional.empty() : Optional.of(session.roomId());
    }

    public Optional<String> activeDoorId(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return session == null || !session.doorSession() ? Optional.empty() : Optional.of(session.roomId());
    }

    public void renameActiveRoomId(Player player, String oldId, String newId) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (session != null && !session.featureSession() && session.roomId().equalsIgnoreCase(oldId)) {
            session.roomId(newId);
        }
    }

    public void renameActiveFeatureId(Player player, String oldId, String newId) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (session != null && session.featureSession() && session.roomId().equalsIgnoreCase(oldId)) {
            session.roomId(newId);
        }
    }

    public boolean isEditingRoom(Player player, String roomId) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return session != null && session.editingExistingRoom() && session.roomId().equalsIgnoreCase(roomId);
    }

    public Optional<AuthoringSession> editingSession(Player player, String roomId) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.editingExistingRoom() || !session.roomId().equalsIgnoreCase(roomId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<AuthoringSession> editingDoorSession(Player player, String doorId) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.editingExistingDoor() || !session.roomId().equalsIgnoreCase(doorId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public boolean hasEditableRoomSession(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return isRoomComponentSession(session);
    }

    public boolean hasEditableComponentSession(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        return isComponentSession(session);
    }

    public List<String> componentIds(Player player, String type) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (!isComponentSession(session)) {
            return List.of();
        }
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.doors().stream().map(com.dungeonarchitect.domain.DoorSocket::id).toList();
            case "gateway" -> session.gateway() == null ? List.of() : List.of("gateway");
            case "marker" -> session.markers().stream().map(com.dungeonarchitect.domain.RoomMarker::name).toList();
            case "feature" -> session.featureSlots().stream().map(RoomFeatureSlot::id).toList();
            default -> List.of();
        };
    }

    public List<ComponentSelection> componentSelections(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (!isComponentSession(session)) {
            return List.of();
        }
        return componentSelections(session);
    }

    public Optional<AuthoringSession.SelectedComponent> selectedComponent(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (!isComponentSession(session)) {
            return Optional.empty();
        }
        return selectedComponentSelection(player)
            .map(selection -> new AuthoringSession.SelectedComponent(selection.type(), selection.id()));
    }

    public Optional<ComponentSelection> selectedComponentSelection(Player player) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (!isComponentSession(session)) {
            return Optional.empty();
        }
        Optional<AuthoringSession.SelectedComponent> selected = session.selectedComponent();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        AuthoringSession.SelectedComponent component = selected.get();
        Optional<ComponentSelection> selection = componentSelections(session).stream()
            .filter(candidate -> candidate.type().equals(component.type()) && candidate.id().equalsIgnoreCase(component.id()))
            .findFirst();
        if (selection.isEmpty()) {
            session.clearSelectedComponent();
        }
        return selection;
    }

    public Optional<ComponentSelection> raycastComponent(Player player, double maxDistance) {
        return raycastComponentHit(player, maxDistance).map(SelectionRaycaster.Hit::value);
    }

    public Optional<SelectionRaycaster.Hit<ComponentSelection>> raycastComponentHit(Player player, double maxDistance) {
        List<ComponentSelection> components = componentSelections(player);
        if (components.isEmpty()) {
            return Optional.empty();
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        return SelectionRaycaster.firstHit(components, ComponentSelection::worldBounds, eye.toVector(), direction, maxDistance);
    }

    public Optional<IntVector3> targetedLocalPosition(Player player) {
        AuthoringSession session = session(player);
        return session.roomBounds().flatMap(bounds -> {
            var block = player.getTargetBlockExact(8);
            if (block == null) {
                return Optional.empty();
            }
            IntVector3 worldPosition = BukkitVectors.blockVector(block.getLocation());
            if (!bounds.contains(worldPosition)) {
                return Optional.empty();
            }
            return Optional.of(bounds.toLocal(worldPosition));
        });
    }

    public DoorCreation createDoorFromSelection(Player player, String requestedId, SocketType socketType, Direction3 facing) {
        AuthoringSession session = session(player);
        SelectionBounds roomBounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save room bounds first with /da room bounds"));
        SelectionBounds current = session.currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select the door region with the wand first"));
        SelectionBounds local = current.toLocal(roomBounds);
        String id = requestedId == null || requestedId.isBlank() ? "door_" + session.nextDoorNumber() : requestedId;
        Direction3 inferredFacing = BoundaryFacing.infer(local, SelectionBounds.between(IntVector3.ZERO, roomBounds.size().subtract(new IntVector3(1, 1, 1))), "Door slot");
        IntVector3 size = local.size();
        int width = switch (inferredFacing) {
            case NORTH, SOUTH -> size.x();
            case EAST, WEST -> size.z();
            default -> throw new IllegalArgumentException("Door slot facing must be horizontal");
        };
        int height = size.y();
        session.addDoorSlot(id, local.min(), size, inferredFacing);
        selectComponent(player, "door", id);
        return new DoorCreation(id, local, width, height);
    }

    public DoorGateway saveDoorGateway(Player player) {
        AuthoringSession session = session(player);
        if (!session.doorSession()) {
            throw new IllegalStateException("Start a door session with /da door create <id>");
        }
        SelectionBounds doorBounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save door bounds first with /da door bounds"));
        SelectionBounds current = session.currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select the gateway region with the wand first"));
        SelectionBounds local = current.toLocal(doorBounds);
        Direction3 facing = BoundaryFacing.infer(local, SelectionBounds.between(IntVector3.ZERO, doorBounds.size().subtract(new IntVector3(1, 1, 1))), "Gateway");
        DoorGateway gateway = new DoorGateway(local.min(), local.size(), facing);
        session.gateway(gateway);
        selectComponent(player, "gateway", "gateway");
        return gateway;
    }

    public RoomFeatureSlot createFeatureSlotFromSelection(Player player, String requestedId) {
        AuthoringSession session = session(player);
        SelectionBounds roomBounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save room bounds first with /da room bounds"));
        SelectionBounds current = session.currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select the feature slot region with the wand first"));
        SelectionBounds local = current.toLocal(roomBounds);
        String id = requestedId == null || requestedId.isBlank() ? "feature_" + session.nextFeatureNumber() : requestedId;
        Direction3 facing = BukkitVectors.direction(player.getFacing());
        RoomFeatureSlot slot = new RoomFeatureSlot(id, local.min(), local.size(), facing);
        session.addFeatureSlot(slot);
        selectComponent(player, "feature", slot.id());
        return slot;
    }

    public RoomFeatureSlot createDoorFeatureSlotFromSelection(Player player, String requestedId) {
        AuthoringSession session = session(player);
        if (!session.doorSession()) {
            throw new IllegalStateException("Start a door session with /da door create <id>");
        }
        SelectionBounds doorBounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save door bounds first with /da door bounds"));
        SelectionBounds current = session.currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select the feature slot region with the wand first"));
        SelectionBounds local = current.toLocal(doorBounds);
        String id = requestedId == null || requestedId.isBlank() ? "feature_" + session.nextFeatureNumber() : requestedId;
        Direction3 facing = BukkitVectors.direction(player.getFacing());
        RoomFeatureSlot slot = new RoomFeatureSlot(id, local.min(), local.size(), facing);
        session.addFeatureSlot(slot);
        selectComponent(player, "feature", slot.id());
        return slot;
    }

    public void addMarker(Player player, String name, String type, IntVector3 localPosition) {
        AuthoringSession session = session(player);
        session.addMarker(name, type, localPosition);
        selectComponent(player, "marker", name);
    }

    public void addDoorMarker(Player player, String name, String type, IntVector3 localPosition) {
        AuthoringSession session = session(player);
        if (!session.doorSession()) {
            throw new IllegalStateException("Start a door session with /da door create <id>");
        }
        session.addMarker(name, type, localPosition);
        selectComponent(player, "marker", name);
    }

    public TemplateValidationResult save(Player player, String requestedId) throws IOException {
        AuthoringSession session = session(player);
        if (requestedId != null && !requestedId.isBlank()) {
            if (session.editingExistingRoom() && !requestedId.equalsIgnoreCase(session.roomId())) {
                throw new IllegalStateException("This edit session can only overwrite " + session.roomId());
            }
            session.roomId(requestedId);
        }
        SelectionBounds bounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save room bounds first with /da room bounds"));
        World world = session.world()
            .orElseThrow(() -> new IllegalStateException("The selected world is no longer loaded"));

        Path roomDirectory = roomsDirectory.resolve(session.roomId());
        Files.createDirectories(roomDirectory);
        Structure structure = server.getStructureManager().createStructure();
        Location corner1 = new Location(world, bounds.min().x(), bounds.min().y(), bounds.min().z());
        BlockVector size = bounds.blockVectorSize();
        structure.fill(corner1, size, true);
        IntVector3 capturedSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!capturedSize.equals(bounds.size())) {
            throw new IllegalStateException("Captured structure size " + capturedSize + " did not match selected bounds size " + bounds.size());
        }
        server.getStructureManager().saveStructure(roomDirectory.resolve("room.nbt").toFile(), structure);

        RoomTemplate template = new RoomTemplate(
            session.roomId(),
            session.category(),
            session.weight(),
            session.tags(),
            bounds.size(),
            session.spawn(),
            session.doors(),
            session.markers(),
            session.featureSlots(),
            roomDirectory.resolve("room.nbt")
        );
        RoomTemplateIO.save(template, roomDirectory);
        return validator.validate(template);
    }

    public TemplateValidationResult saveFeature(Player player, String requestedId) throws IOException {
        AuthoringSession session = session(player);
        if (!session.featureSession()) {
            throw new IllegalStateException("Start a feature session with /da feature create <id>");
        }
        if (requestedId != null && !requestedId.isBlank()) {
            if (requestedId.equalsIgnoreCase(com.dungeonarchitect.domain.FeatureSlotEntry.EMPTY)) {
                throw new IllegalStateException("empty is reserved");
            }
            if (session.editingExistingFeature() && !requestedId.equalsIgnoreCase(session.roomId())) {
                throw new IllegalStateException("This edit session can only overwrite " + session.roomId());
            }
            session.roomId(requestedId);
        }
        SelectionBounds bounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save feature bounds first with /da feature bounds"));
        World world = session.world()
            .orElseThrow(() -> new IllegalStateException("The selected world is no longer loaded"));
        Path featureDirectory = featuresDirectory.resolve(session.roomId());
        Files.createDirectories(featureDirectory);
        Structure structure = server.getStructureManager().createStructure();
        Location corner1 = new Location(world, bounds.min().x(), bounds.min().y(), bounds.min().z());
        structure.fill(corner1, bounds.blockVectorSize(), true);
        IntVector3 capturedSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!capturedSize.equals(bounds.size())) {
            throw new IllegalStateException("Captured feature size " + capturedSize + " did not match selected bounds size " + bounds.size());
        }
        server.getStructureManager().saveStructure(featureDirectory.resolve("feature.nbt").toFile(), structure);
        FeatureTemplate template = new FeatureTemplate(session.roomId(), bounds.size(), session.tags(), featureDirectory.resolve("feature.nbt"));
        FeatureTemplateIO.save(template, featureDirectory);
        return featureValidator.validate(template);
    }

    public TemplateValidationResult saveDoor(Player player, String requestedId) throws IOException {
        AuthoringSession session = session(player);
        if (!session.doorSession()) {
            throw new IllegalStateException("Start a door session with /da door create <id>");
        }
        if (requestedId != null && !requestedId.isBlank()) {
            if (requestedId.equalsIgnoreCase(com.dungeonarchitect.domain.DoorSlotEntry.EMPTY)) {
                throw new IllegalStateException("empty is reserved");
            }
            if (session.editingExistingDoor() && !requestedId.equalsIgnoreCase(session.roomId())) {
                throw new IllegalStateException("This edit session can only overwrite " + session.roomId());
            }
            session.roomId(requestedId);
        }
        SelectionBounds bounds = session.roomBounds()
            .orElseThrow(() -> new IllegalStateException("Save door bounds first with /da door bounds"));
        World world = session.world()
            .orElseThrow(() -> new IllegalStateException("The selected world is no longer loaded"));
        Path doorDirectory = doorsDirectory.resolve(session.roomId());
        Files.createDirectories(doorDirectory);
        Structure structure = server.getStructureManager().createStructure();
        Location corner1 = new Location(world, bounds.min().x(), bounds.min().y(), bounds.min().z());
        structure.fill(corner1, bounds.blockVectorSize(), true);
        IntVector3 capturedSize = new IntVector3(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ());
        if (!capturedSize.equals(bounds.size())) {
            throw new IllegalStateException("Captured door size " + capturedSize + " did not match selected bounds size " + bounds.size());
        }
        server.getStructureManager().saveStructure(doorDirectory.resolve("door.nbt").toFile(), structure);
        DoorTemplate template = new DoorTemplate(session.roomId(), bounds.size(), session.tags(), session.markers(), session.featureSlots(), session.gateway(), doorDirectory.resolve("door.nbt"));
        DoorTemplateIO.save(template, doorDirectory);
        return doorValidator.validate(template);
    }

    public void highlightComponent(Player player, String type, String id) {
        selectComponent(player, type, id);
    }

    public ComponentSelection selectComponent(Player player, String type, String id) {
        AuthoringSession session = session(player);
        if (!isComponentSession(session)) {
            throw new IllegalStateException("Save bounds first");
        }
        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);
        ComponentSelection selection = componentSelections(session).stream()
            .filter(component -> component.type().equals(normalizedType) && component.id().equalsIgnoreCase(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown " + normalizedType + " " + id));
        session.selectComponent(selection.type(), selection.id());
        return selection;
    }

    public void highlightInvalid(Player player, TemplateValidationResult result) {
        for (TemplateValidationResult.ValidationIssue issue : result.issues()) {
            highlightLocal(player, issue.localPosition(), Particle.DUST);
        }
    }

    public void highlightInvalidIfEditing(Player player, String roomId, TemplateValidationResult result) {
        if (isEditingRoom(player, roomId)) {
            highlightInvalid(player, result);
        }
    }

    public boolean removeComponent(Player player, String type, String id) {
        AuthoringSession session = session(player);
        if (!isRoomComponentSession(session)) {
            throw new IllegalStateException("Save room bounds first with /da room bounds");
        }
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.removeDoor(id);
            case "marker" -> session.removeMarker(id);
            case "feature" -> session.removeFeature(id);
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
    }

    public boolean renameComponent(Player player, String type, String oldId, String newId) {
        AuthoringSession session = session(player);
        if (!isRoomComponentSession(session)) {
            throw new IllegalStateException("Save room bounds first with /da room bounds");
        }
        boolean renamed = switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.renameDoor(oldId, newId);
            case "marker" -> session.renameMarker(oldId, newId);
            case "feature" -> session.renameFeatureSlot(oldId, newId);
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (renamed) {
            selectComponent(player, type, newId);
        }
        return renamed;
    }

    public ComponentSelection updateComponentBounds(Player player, String type, String id) {
        AuthoringSession session = session(player);
        if (!isRoomComponentSession(session)) {
            throw new IllegalStateException("Save room bounds first with /da room bounds");
        }
        SelectionBounds roomBounds = session.roomBounds().orElseThrow();
        SelectionBounds current = session.currentSelection()
            .orElseThrow(() -> new IllegalStateException("Select the new component bounds with the wand first"));
        SelectionBounds local = current.toLocal(roomBounds);
        boolean updated = switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.updateDoorBounds(
                id,
                local,
                BoundaryFacing.infer(local, SelectionBounds.between(IntVector3.ZERO, roomBounds.size().subtract(new IntVector3(1, 1, 1))), "Door slot")
            );
            case "marker" -> session.updateMarkerPosition(id, local.min());
            case "feature" -> session.updateFeatureSlotBounds(id, local);
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (!updated) {
            throw new IllegalArgumentException("No matching " + type + " named " + id + ".");
        }
        return selectComponent(player, type, id);
    }

    public void cancelEdit(Player player) {
        clearExistingEditCopy(player);
        sessions.remove(player.getUniqueId());
    }

    private List<ComponentSelection> componentSelections(AuthoringSession session) {
        SelectionBounds roomBounds = session.roomBounds().orElse(null);
        if (roomBounds == null) {
            return List.of();
        }

        List<ComponentSelection> selections = new ArrayList<>();
        session.doors().forEach(door -> selections.add(componentSelection("door", door.id(), doorBounds(door), roomBounds, door.facing())));
        if (session.doorSession() && session.gateway() != null) {
            DoorGateway gateway = session.gateway();
            SelectionBounds gatewayBounds = SelectionBounds.between(gateway.position(), gateway.position().add(gateway.size()).subtract(new IntVector3(1, 1, 1)));
            selections.add(componentSelection("gateway", "gateway", gatewayBounds, roomBounds, gateway.facing()));
        }
        session.markers().forEach(marker -> selections.add(componentSelection("marker", marker.name(), SelectionBounds.between(marker.position(), marker.position()), roomBounds, null)));
        session.featureSlots().forEach(slot -> {
            SelectionBounds localBounds = SelectionBounds.between(slot.position(), slot.position().add(slot.size()).subtract(new IntVector3(1, 1, 1)));
            selections.add(componentSelection("feature", slot.id(), localBounds, roomBounds, slot.facing()));
        });
        return List.copyOf(selections);
    }

    private boolean isRoomComponentSession(AuthoringSession session) {
        return session != null && !session.featureSession() && !session.doorSession() && session.roomBounds().isPresent();
    }

    private boolean isComponentSession(AuthoringSession session) {
        return session != null && !session.featureSession() && session.roomBounds().isPresent();
    }

    private ComponentSelection componentSelection(String type, String id, SelectionBounds localBounds, SelectionBounds roomBounds, Direction3 facing) {
        SelectionBounds worldBounds = new SelectionBounds(roomBounds.min().add(localBounds.min()), roomBounds.min().add(localBounds.max()));
        return new ComponentSelection(type, id, localBounds, worldBounds, facing);
    }

    private void highlightLocal(Player player, IntVector3 local, Particle particle) {
        AuthoringSession session = session(player);
        SelectionBounds bounds = session.roomBounds().orElseThrow();
        World world = session.world().orElseThrow();
        IntVector3 position = bounds.min().add(local);
        for (int i = 0; i < 3; i++) {
            if (particle == Particle.DUST) {
                world.spawnParticle(Particle.DUST, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5, 16, 0.35, 0.35, 0.35, 0, new Particle.DustOptions(Color.RED, 1.4f));
            } else {
                world.spawnParticle(particle, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5, 16, 0.35, 0.35, 0.35, 0);
            }
        }
    }

    private World editWorld() {
        World existing = server.getWorld(EDIT_WORLD_NAME);
        if (existing != null) {
            configureEditWorld(existing);
            return existing;
        }
        World world = WorldCreator.name(EDIT_WORLD_NAME)
            .generator(new VoidChunkGenerator())
            .generateStructures(false)
            .createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create edit world " + EDIT_WORLD_NAME);
        }
        configureEditWorld(world);
        return world;
    }

    public void prepareEditWorld() {
        editWorld();
    }

    public EditWorkspace workspace(Player player) {
        return workspaceStore.workspace(player.getUniqueId());
    }

    public void markWorkspaceDirty(Player player, Location location) {
        if (!isEditWorld(location.getWorld())) {
            return;
        }
        IntVector3 position = BukkitVectors.blockVector(location);
        EditWorkspace workspace = workspace(player);
        if (!workspace.clearBounds().contains(position)) {
            return;
        }
        workspaceStore.markDirty(player.getUniqueId(), new BoundingBox3i(position, position));
    }

    private void markDirty(Player player, SelectionBounds bounds) {
        workspaceStore.markDirty(player.getUniqueId(), bounds.toBoundingBox());
    }

    public boolean isInEditWorld(Player player) {
        return isEditWorld(player.getWorld());
    }

    public boolean isEditWorld(World world) {
        return world != null && world.getName().equals(EDIT_WORLD_NAME);
    }

    public void exitEditWorld(Player player) {
        if (server.getWorlds().isEmpty()) {
            throw new IllegalStateException("No default world is loaded");
        }
        player.teleport(server.getWorlds().getFirst().getSpawnLocation());
    }

    private void configureEditWorld(World world) {
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(13000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
    }

    private CompletableFuture<EditWorkspace> prepareWorkspace(Player player) {
        UUID playerId = player.getUniqueId();
        if (preparingWorkspaces.containsKey(playerId)) {
            CompletableFuture<EditWorkspace> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Your edit workspace is still preparing"));
            return failed;
        }
        EditWorkspace workspace = workspace(player);
        World world = editWorld();
        Optional<BoundingBox3i> dirtyBounds = workspaceStore.dirtyBounds(playerId);
        BoundingBox3i boundsToClear = dirtyBounds.orElseGet(() -> workspaceStore.needsLegacyClear(playerId) ? workspace.clearBounds() : null);
        if (boundsToClear == null) {
            return CompletableFuture.completedFuture(workspace);
        }
        preparingWorkspaces.put(playerId, true);
        CompletableFuture<EditWorkspace> future = new CompletableFuture<>();
        WorkspaceClearTask clearTask = new WorkspaceClearTask(
            world,
            boundsToClear,
            () -> {
                workspaceStore.markClean(playerId);
                preparingWorkspaces.remove(playerId);
                future.complete(workspace);
            },
            error -> {
                preparingWorkspaces.remove(playerId);
                future.completeExceptionally(error);
            }
        );
        clearTask.start();
        return future;
    }

    private void teleportToWorkspace(Player player, EditWorkspace workspace) {
        World world = editWorld();
        IntVector3 origin = workspace.buildOrigin();
        player.teleport(new Location(world, origin.x() + 0.5, origin.y() + 2, origin.z() + 0.5));
    }

    private void placeScaffold(Player player, World world, IntVector3 buildOrigin) {
        for (IntVector3 block : AuthoringScaffold.floorBlocks(buildOrigin)) {
            world.getBlockAt(block.x(), block.y(), block.z()).setType(Material.GLASS, false);
        }
        markDirty(player, AuthoringScaffold.floorBounds(buildOrigin));
    }

    private void placeSupportPlatform(Player player, World world, SelectionBounds footprint) {
        for (IntVector3 block : AuthoringScaffold.supportPlatformBlocks(footprint)) {
            world.getBlockAt(block.x(), block.y(), block.z()).setType(Material.GLASS, false);
        }
        markDirty(player, AuthoringScaffold.supportPlatformBounds(footprint));
    }

    private SelectionBounds templateBounds(IntVector3 origin, IntVector3 size) {
        return SelectionBounds.between(origin, origin.add(size).subtract(new IntVector3(1, 1, 1)));
    }

    public void spawnSelectorRay(Player player, double distance) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        for (Vector point : SelectionRaycaster.rayPoints(eye.toVector(), eye.getDirection(), distance, 0.75)) {
            world.spawnParticle(Particle.WAX_OFF, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void pasteStructure(Player player, World world, Structure structure, IntVector3 origin, IntVector3 size) {
        structure.place(
            new Location(world, origin.x(), origin.y(), origin.z()),
            true,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            RoomStructurePlacer.STRUCTURE_INTEGRITY,
            new java.util.Random(0L)
        );
        markDirty(player, templateBounds(origin, size));
    }

    private void clearExistingEditCopy(Player player) {
        AuthoringSession existing = sessions.get(player.getUniqueId());
        if (existing == null) {
            return;
        }
        Optional<World> world = existing.world();
        Optional<SelectionBounds> bounds = existing.roomBounds();
        if (world.isEmpty() || bounds.isEmpty() || !isEditWorld(world.get())) {
            return;
        }
        removeNonPlayerEntities(world.get(), bounds.get().toBoundingBox());
        for (int x = bounds.get().min().x(); x <= bounds.get().max().x(); x++) {
            for (int y = bounds.get().min().y(); y <= bounds.get().max().y(); y++) {
                for (int z = bounds.get().min().z(); z <= bounds.get().max().z(); z++) {
                    world.get().getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void removeNonPlayerEntities(World world, BoundingBox3i bounds) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            Location location = entity.getLocation();
            IntVector3 position = new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (bounds.contains(position)) {
                entity.remove();
            }
        }
    }

    public record DoorCreation(String id, SelectionBounds localBounds, int width, int height) {
    }

    public record ComponentSelection(String type, String id, SelectionBounds localBounds, SelectionBounds worldBounds, Direction3 facing) {
        public ComponentSelection(String type, String id, SelectionBounds localBounds, SelectionBounds worldBounds) {
            this(type, id, localBounds, worldBounds, null);
        }
    }

    private SelectionBounds doorBounds(DoorSocket door) {
        return SelectionBounds.between(door.position(), door.position().add(door.size()).subtract(new IntVector3(1, 1, 1)));
    }

    private final class WorkspaceClearTask implements Runnable {
        private final World world;
        private final com.dungeonarchitect.domain.BoundingBox3i bounds;
        private final Runnable onComplete;
        private final java.util.function.Consumer<Throwable> onError;
        private int x;
        private int y;
        private int z;
        private BukkitTask task;

        private WorkspaceClearTask(World world, com.dungeonarchitect.domain.BoundingBox3i bounds, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
            this.world = world;
            this.bounds = bounds;
            this.onComplete = onComplete;
            this.onError = onError;
            this.x = bounds.min().x();
            this.y = bounds.min().y();
            this.z = bounds.min().z();
        }

        private void start() {
            removeNonPlayerEntities(world, bounds);
            task = server.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        }

        @Override
        public void run() {
            try {
                int cleared = 0;
                while (cleared++ < CLEAR_BLOCKS_PER_TICK) {
                    var block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                    if (!advance()) {
                        task.cancel();
                        onComplete.run();
                        return;
                    }
                }
            } catch (Throwable throwable) {
                task.cancel();
                onError.accept(throwable);
            }
        }

        private boolean advance() {
            z++;
            if (z <= bounds.max().z()) {
                return true;
            }
            z = bounds.min().z();
            y++;
            if (y <= bounds.max().y()) {
                return true;
            }
            y = bounds.min().y();
            x++;
            return x <= bounds.max().x();
        }
    }
}
