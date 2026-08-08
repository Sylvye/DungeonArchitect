package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.gui.GuiItems;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateValidator;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.util.BukkitVectors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuthoringManager {
    private final Server server;
    private final Path roomsDirectory;
    private final NamespacedKey wandKey;
    private final Material wandMaterial;
    private final RoomCategory defaultCategory;
    private final int defaultWeight;
    private final Map<UUID, AuthoringSession> sessions = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();

    public AuthoringManager(Server server, Path roomsDirectory, NamespacedKey wandKey, Material wandMaterial, RoomCategory defaultCategory, int defaultWeight) {
        this.server = server;
        this.roomsDirectory = roomsDirectory;
        this.wandKey = wandKey;
        this.wandMaterial = wandMaterial;
        this.defaultCategory = defaultCategory;
        this.defaultWeight = defaultWeight;
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(wandMaterial);
        var meta = item.getItemMeta();
        meta.displayName(GuiItems.text("DungeonArchitect Wand", net.kyori.adventure.text.format.NamedTextColor.GOLD));
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
        AuthoringSession session = sessions.computeIfAbsent(player.getUniqueId(), id -> new AuthoringSession(roomId));
        session.roomId(roomId);
        return session;
    }

    public void setSelection(Player player, int index, Location location) {
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

    public RoomFeatureSlot createFeatureFromTarget(Player player, String featureName, String poolId) {
        AuthoringSession session = session(player);
        IntVector3 local = targetedLocalPosition(player)
            .orElseThrow(() -> new IllegalStateException("Look at a block inside selected room bounds"));
        String id = "feature_" + session.nextFeatureNumber();
        Direction3 facing = BukkitVectors.direction(player.getFacing());
        RoomFeatureSlot slot = new RoomFeatureSlot(id, poolId, featureName, local, facing);
        session.addFeatureSlot(slot);
        return slot;
    }

    public TemplateValidationResult save(Player player, String requestedId) throws IOException {
        AuthoringSession session = session(player);
        if (requestedId != null && !requestedId.isBlank()) {
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
            defaultCategory,
            defaultWeight,
            Set.of(),
            bounds.size(),
            null,
            session.doors(),
            session.markers(),
            session.featureSlots(),
            roomDirectory.resolve("room.nbt")
        );
        RoomTemplateIO.save(template, roomDirectory);
        return validator.validate(template);
    }

    public record DoorCreation(String id, SelectionBounds localBounds, int width, int height) {
    }
}
