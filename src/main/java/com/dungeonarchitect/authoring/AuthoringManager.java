package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuthoringManager {
    private static final String EDIT_WORLD_NAME = "da_edit";
    private static final IntVector3 EDIT_ORIGIN = new IntVector3(0, 80, 0);
    private final Server server;
    private final Path roomsDirectory;
    private final Path featuresDirectory;
    private final NamespacedKey wandKey;
    private final Material wandMaterial;
    private final RoomCategory defaultCategory;
    private final int defaultWeight;
    private final Map<UUID, AuthoringSession> sessions = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();
    private final FeatureTemplateValidator featureValidator;

    public AuthoringManager(Server server, Path roomsDirectory, Path featuresDirectory, NamespacedKey wandKey, Material wandMaterial, RoomCategory defaultCategory, int defaultWeight) {
        this.server = server;
        this.roomsDirectory = roomsDirectory;
        this.featuresDirectory = featuresDirectory;
        this.wandKey = wandKey;
        this.wandMaterial = wandMaterial;
        this.defaultCategory = defaultCategory;
        this.defaultWeight = defaultWeight;
        this.featureValidator = new FeatureTemplateValidator(new com.dungeonarchitect.template.RoomStructureService(server));
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

    public AuthoringSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new AuthoringSession("unnamed_room"));
    }

    public AuthoringSession createSession(Player player, String roomId) {
        clearExistingEditCopy(player);
        enterEditWorld(player);
        AuthoringSession session = new AuthoringSession(roomId);
        session.featureSession(false);
        session.category(defaultCategory);
        session.weight(defaultWeight);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public AuthoringSession createFeatureSession(Player player, String featureId) {
        if (featureId.equalsIgnoreCase(com.dungeonarchitect.domain.FeatureSlotEntry.EMPTY)) {
            throw new IllegalArgumentException("empty is reserved");
        }
        clearExistingEditCopy(player);
        enterEditWorld(player);
        AuthoringSession session = new AuthoringSession(featureId);
        session.featureSession(true);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public AuthoringSession editSession(Player player, RoomTemplate template) throws IOException {
        clearExistingEditCopy(player);
        World world = editWorld();
        Structure structure = server.getStructureManager().loadStructure(template.structureFile().toFile());
        var size = structure.getSize();
        IntVector3 nbtSize = new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
        if (!nbtSize.equals(template.size())) {
            throw new IOException("room.nbt size " + nbtSize + " does not match room.yml size " + template.size() + ". Re-save this room from the original build area first.");
        }
        structure.place(
            new Location(world, EDIT_ORIGIN.x(), EDIT_ORIGIN.y(), EDIT_ORIGIN.z()),
            true,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            RoomStructurePlacer.STRUCTURE_INTEGRITY,
            new java.util.Random(0L)
        );
        AuthoringSession session = new AuthoringSession(template.id());
        session.loadTemplateForEdit(template, world, EDIT_ORIGIN);
        sessions.put(player.getUniqueId(), session);
        player.teleport(new Location(world, EDIT_ORIGIN.x() + 0.5, EDIT_ORIGIN.y() + 2, EDIT_ORIGIN.z() + 0.5));
        return session;
    }

    public AuthoringSession editFeatureSession(Player player, FeatureTemplate template) throws IOException {
        clearExistingEditCopy(player);
        World world = editWorld();
        Structure structure = server.getStructureManager().loadStructure(template.structureFile().toFile());
        var size = structure.getSize();
        IntVector3 nbtSize = new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
        if (!nbtSize.equals(template.size())) {
            throw new IOException("feature.nbt size " + nbtSize + " does not match feature.yml size " + template.size() + ". Re-save this feature first.");
        }
        structure.place(
            new Location(world, EDIT_ORIGIN.x(), EDIT_ORIGIN.y(), EDIT_ORIGIN.z()),
            true,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            RoomStructurePlacer.STRUCTURE_INTEGRITY,
            new java.util.Random(0L)
        );
        AuthoringSession session = new AuthoringSession(template.id());
        session.loadFeatureForEdit(template, world, EDIT_ORIGIN);
        sessions.put(player.getUniqueId(), session);
        player.teleport(new Location(world, EDIT_ORIGIN.x() + 0.5, EDIT_ORIGIN.y() + 2, EDIT_ORIGIN.z() + 0.5));
        return session;
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
        return session.roomBounds().orElseThrow();
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

    public List<String> componentIds(Player player, String type) {
        AuthoringSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.editingExistingRoom()) {
            return List.of();
        }
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.doors().stream().map(com.dungeonarchitect.domain.DoorSocket::id).toList();
            case "marker" -> session.markers().stream().map(com.dungeonarchitect.domain.RoomMarker::name).toList();
            case "feature" -> session.featureSlots().stream().map(RoomFeatureSlot::id).toList();
            default -> List.of();
        };
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
        IntVector3 size = local.size();
        int width = Math.max(size.x(), size.z());
        int height = size.y();
        session.addDoor(id, local.min(), facing, socketType, width, height);
        return new DoorCreation(id, local, width, height);
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
        return slot;
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

    public void highlightComponent(Player player, String type, String id) {
        selectComponent(player, type, id);
    }

    public ComponentSelection selectComponent(Player player, String type, String id) {
        AuthoringSession session = session(player);
        if (!session.editingExistingRoom()) {
            throw new IllegalStateException("Paste a room for editing first");
        }
        SelectionBounds localBounds = switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.doors().stream()
                .filter(door -> door.id().equalsIgnoreCase(id))
                .findFirst()
                .map(this::doorBounds)
                .orElseThrow(() -> new IllegalArgumentException("Unknown door " + id));
            case "marker" -> session.markers().stream()
                .filter(marker -> marker.name().equalsIgnoreCase(id))
                .findFirst()
                .map(marker -> SelectionBounds.between(marker.position(), marker.position()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown marker " + id));
            case "feature" -> session.featureSlots().stream()
                .filter(slot -> slot.id().equalsIgnoreCase(id))
                .findFirst()
                .map(slot -> SelectionBounds.between(slot.position(), slot.position().add(slot.size()).subtract(new IntVector3(1, 1, 1))))
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature " + id));
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        SelectionBounds roomBounds = session.roomBounds().orElseThrow();
        SelectionBounds worldBounds = new SelectionBounds(roomBounds.min().add(localBounds.min()), roomBounds.min().add(localBounds.max()));
        session.selectCurrentBounds(worldBounds);
        return new ComponentSelection(type.toLowerCase(java.util.Locale.ROOT), id, localBounds, worldBounds);
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
        if (!session.editingExistingRoom()) {
            throw new IllegalStateException("Paste a room for editing first");
        }
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "door" -> session.removeDoor(id);
            case "marker" -> session.removeMarker(id);
            case "feature" -> session.removeFeature(id);
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
    }

    public void cancelEdit(Player player) {
        clearExistingEditCopy(player);
        sessions.remove(player.getUniqueId());
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

    private void enterEditWorld(Player player) {
        World world = editWorld();
        player.teleport(new Location(world, EDIT_ORIGIN.x() + 0.5, EDIT_ORIGIN.y() + 2, EDIT_ORIGIN.z() + 0.5));
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
        for (int x = bounds.get().min().x(); x <= bounds.get().max().x(); x++) {
            for (int y = bounds.get().min().y(); y <= bounds.get().max().y(); y++) {
                for (int z = bounds.get().min().z(); z <= bounds.get().max().z(); z++) {
                    world.get().getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    public record DoorCreation(String id, SelectionBounds localBounds, int width, int height) {
    }

    public record ComponentSelection(String type, String id, SelectionBounds localBounds, SelectionBounds worldBounds) {
    }

    private SelectionBounds doorBounds(DoorSocket door) {
        IntVector3 maxOffset = switch (door.facing()) {
            case NORTH, SOUTH -> new IntVector3(door.width() - 1, door.height() - 1, 0);
            case EAST, WEST -> new IntVector3(0, door.height() - 1, door.width() - 1);
            case UP, DOWN -> new IntVector3(door.width() - 1, 0, door.height() - 1);
        };
        return SelectionBounds.between(door.position(), door.position().add(maxOffset));
    }
}
