package com.dungeonarchitect.gui;

import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.AuthoringSession;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomTemplateValidator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class MenuManager implements Listener {
    private final Plugin plugin;
    private final AuthoringManager authoringManager;
    private final RoomTemplateRegistry templateRegistry;
    private final FeatureTemplateRegistry featureRegistry;
    private final DoorTemplateRegistry doorRegistry;
    private final DungeonManager dungeonManager;
    private final ChatPromptManager prompts;
    private final Runnable reloadAll;
    private final Map<UUID, PlayerMenuActions> actions = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, Runnable reloadAll) {
        this.plugin = plugin;
        this.authoringManager = authoringManager;
        this.templateRegistry = templateRegistry;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
        this.dungeonManager = dungeonManager;
        this.prompts = prompts;
        this.reloadAll = reloadAll;
    }

    public void openMain(Player player) {
        Menu menu = menu("da:main", 27, "DungeonArchitect");
        button(menu, 10, Material.BOOKSHELF, "Rooms", List.of("Edit room metadata and validate templates."), this::openRooms);
        button(menu, 12, Material.OAK_DOOR, "Doors", List.of("Edit reusable door templates."), this::openDoors);
        button(menu, 14, Material.STRUCTURE_BLOCK, "Features", List.of("Edit reusable feature templates."), this::openFeatures);
        button(menu, 16, Material.ENDER_PEARL, "Dungeons", List.of("Manage active dungeon instances."), this::openDungeons);
        button(menu, 22, Material.COMPARATOR, "Config", List.of("Edit config.yml."), this::openConfig);
        open(player, menu);
    }

    public void prompt(Player player, String message, Consumer<String> handler) {
        prompts.prompt(player, message, handler);
    }

    public void openRooms(Player player) {
        Menu menu = menu("da:rooms", 54, "DungeonArchitect Rooms");
        int slot = 0;
        for (RoomTemplate template : templateRegistry.all()) {
            int target = slot++;
            button(menu, target, Material.PAPER, template.id(), List.of("Category: " + template.category(), "Door Slots: " + template.doors().size(), "Click to edit."), p -> openRoom(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openRoom(Player player, String roomId) {
        RoomTemplate template = templateRegistry.get(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        Menu menu = menu("da:room:" + roomId, 54, "Room: " + roomId);
        var validation = validator.validate(template);
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        var doors = activeEdit == null ? template.doors() : activeEdit.doors();
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        button(menu, 4, validation.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, validation.valid() ? "Validation OK" : "Validation Errors", validation.errors(), p -> openRoom(p, roomId));
        button(menu, 10, Material.NAME_TAG, "Category: " + template.category(), enumNames(RoomCategory.class), p -> {
            RoomCategory next = nextCategory(template.category());
            if (activeEdit != null) {
                activeEdit.category(next);
            }
            saveRoom(new RoomTemplate(template.id(), next, template.weight(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room category changed to " + next + "."));
            openRoom(p, roomId);
        });
        button(menu, 12, Material.GOLD_NUGGET, "Weight: " + template.weight(), List.of("Click to edit generation weight."), p -> prompts.prompt(p, "Enter positive integer weight", value -> {
            saveRoom(new RoomTemplate(template.id(), template.category(), Integer.parseInt(value), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room weight updated."));
            openRoom(p, roomId);
        }));
        button(menu, 14, Material.COMPASS, "Spawn: " + (template.spawn() == null ? "unset" : template.spawn()), List.of("Format: x,y,z or empty to clear."), p -> prompts.prompt(p, "Enter spawn as x,y,z or empty", value -> {
            IntVector3 spawn = value.isBlank() ? null : parseVector(value);
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), spawn, template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room spawn updated."));
            openRoom(p, roomId);
        }));
        button(menu, 16, Material.OAK_SIGN, "Tags: " + String.join(",", template.tags()), List.of("Comma separated tags."), p -> prompts.prompt(p, "Enter comma-separated tags", value -> {
            Set<String> tags = new LinkedHashSet<>();
            for (String tag : value.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.trim());
                }
            }
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), tags, template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room tags updated."));
            openRoom(p, roomId);
        }));
        button(menu, 22, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this room into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted " + template.id() + " into the edit world."));
            });
        });
        button(menu, 23, authoringManager.isEditingRoom(player, roomId) ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this room from the edit world."), p -> {
            if (!authoringManager.isEditingRoom(p, roomId)) {
                p.sendMessage(Component.text("Paste this room for editing first."));
                openRoom(p, roomId);
                return;
            }
            try {
                TemplateValidationResult result = authoringManager.save(p, roomId);
                sendValidation(p, result);
                if (!result.valid()) {
                    authoringManager.highlightInvalid(p, result);
                }
                templateRegistry.reload();
                openRoom(p, roomId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openRoom(p, roomId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Room edit session cancelled."));
            openRoom(p, roomId);
        });
        button(menu, 28, Material.IRON_DOOR, "Door Slots: " + doors.size(), doors.stream().map(door -> door.id() + " " + door.size() + " " + door.facing()).toList(), p -> openComponents(p, roomId, "door"));
        button(menu, 30, Material.REDSTONE_TORCH, "Markers: " + markers.size(), markers.stream().map(marker -> marker.name() + " " + marker.type()).toList(), p -> openComponents(p, roomId, "marker"));
        button(menu, 32, Material.CHEST, "Feature Slots: " + features.size(), features.stream().map(slot -> slot.id() + " size=" + slot.size()).toList(), p -> openComponents(p, roomId, "feature"));
        button(menu, 36, Material.NAME_TAG, "Rename Room", List.of("Move this room template to a new id."), p -> promptRenameRoom(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Room", List.of("Copy this room template to a new id."), p -> promptDuplicateRoom(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Room", List.of("Permanently delete this room template."), p -> openDeleteRoomConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openRooms);
        open(player, menu);
    }

    private void openComponents(Player player, String roomId, String type) {
        RoomTemplate template = templateRegistry.get(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        Menu menu = menu("da:components:" + roomId + ":" + type, 54, titleCase(type) + "s: " + roomId);
        int slot = 0;
        if (type.equals("door")) {
            var doors = activeEdit == null ? template.doors() : activeEdit.doors();
            for (var door : doors) {
                button(menu, slot++, Material.IRON_DOOR, door.id(), List.of("Position: " + door.position(), "Size: " + door.size(), "Facing: " + door.facing(), "Entries: " + door.entries().size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openDoorSlot(p, roomId, door.id()), p -> selectComponent(p, roomId, type, door.id()), p -> promptRenameComponent(p, roomId, type, door.id()), p -> openDeleteComponentConfirm(p, roomId, type, door.id()));
                if (slot >= 45) {
                    break;
                }
            }
        } else if (type.equals("marker")) {
            var markers = activeEdit == null ? template.markers() : activeEdit.markers();
            for (var marker : markers) {
                button(menu, slot++, Material.REDSTONE_TORCH, marker.name(), List.of("Type: " + marker.type(), "Position: " + marker.position(), "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openComponents(p, roomId, type), p -> selectComponent(p, roomId, type, marker.name()), p -> promptRenameComponent(p, roomId, type, marker.name()), p -> openDeleteComponentConfirm(p, roomId, type, marker.name()));
                if (slot >= 45) {
                    break;
                }
            }
        } else if (type.equals("feature")) {
            var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
            for (var feature : features) {
                button(menu, slot++, Material.CHEST, feature.id(), List.of("Position: " + feature.position(), "Size: " + feature.size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openFeatureSlot(p, roomId, feature.id()), p -> selectComponent(p, roomId, type, feature.id()), p -> promptRenameComponent(p, roomId, type, feature.id()), p -> openDeleteComponentConfirm(p, roomId, type, feature.id()));
                if (slot >= 45) {
                    break;
                }
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openRoom(p, roomId));
        open(player, menu);
    }

    private void openFeatureSlot(Player player, String roomId, String slotId) {
        RoomTemplate template = templateRegistry.get(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        List<RoomFeatureSlot> featureSlots = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        RoomFeatureSlot featureSlot = featureSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature slot " + slotId));
        Menu menu = menu("da:feature-slot:" + roomId + ":" + slotId, 54, "Feature Slot: " + slotId);
        button(menu, 4, Material.HOPPER, "Slot " + slotId, List.of("Size: " + featureSlot.size(), "Position: " + featureSlot.position()), p -> openFeatureSlot(p, roomId, slotId));
        int slot = 9;
        if (featureSlot.entries().isEmpty()) {
            button(menu, 5, Material.YELLOW_CONCRETE, "No Entries Selected", List.of("This slot always pastes nothing."), p -> openFeatureSlot(p, roomId, slotId));
        }
        button(menu, slot++, Material.BARRIER, "empty", entryLore(featureSlot, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> toggleFeatureEntry(p, template, featureSlot, FeatureSlotEntry.EMPTY, 1), null, p -> promptFeatureWeight(p, template, featureSlot, FeatureSlotEntry.EMPTY));
        List<FeatureTemplate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.all()) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            if (!match.matched()) {
                unavailable.add(feature);
                continue;
            }
            Rotation rotation = match.rotation();
            IntVector3 rotatedSize = rotation.rotateSize(feature.size());
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), entryLore(featureSlot, feature.id(), "Size: " + feature.size(), "Paste size: " + rotatedSize, "Offset: " + FeatureMatcher.placementOffset(featureSlot.size(), rotatedSize), "Rotation: " + rotation), p -> toggleFeatureEntry(p, template, featureSlot, feature.id(), 1), null, p -> promptFeatureWeight(p, template, featureSlot, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableFeatureEntries(menu, slot, featureSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openComponents(p, roomId, "feature"));
        open(player, menu);
    }

    private void openDoorSlot(Player player, String roomId, String slotId) {
        RoomTemplate template = templateRegistry.get(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        List<DoorSocket> doorSlots = activeEdit == null ? template.doors() : activeEdit.doors();
        DoorSocket doorSlot = doorSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown door slot " + slotId));
        Menu menu = menu("da:door-slot:" + roomId + ":" + slotId, 54, "Door Slot: " + slotId);
        button(menu, 4, Material.IRON_DOOR, "Slot " + slotId, List.of("Size: " + doorSlot.size(), "Position: " + doorSlot.position(), "Facing: " + doorSlot.facing(), "Tags: " + String.join(",", doorSlot.tags())), p -> openDoorSlot(p, roomId, slotId));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", doorEntryLore(doorSlot, DoorSlotEntry.EMPTY, "Virtual door; leaves room blueprint unchanged."), p -> toggleDoorEntry(p, template, doorSlot, DoorSlotEntry.EMPTY, 1), null, p -> promptDoorWeight(p, template, doorSlot, DoorSlotEntry.EMPTY));
        List<DoorTemplate> unavailable = new ArrayList<>();
        for (DoorTemplate door : doorRegistry.all()) {
            DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(doorSlot, door);
            if (!match.matched()) {
                unavailable.add(door);
                continue;
            }
            button(menu, slot++, Material.OAK_DOOR, door.id(), doorEntryLore(doorSlot, door.id(), "Bounds: " + door.size(), "Gateway: " + door.gateway().size(), "Door rotation: " + match.rotation(), "Tags: " + String.join(",", door.tags())), p -> toggleDoorEntry(p, template, doorSlot, door.id(), 1), null, p -> promptDoorWeight(p, template, doorSlot, door.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableDoorEntries(menu, slot, doorSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openComponents(p, roomId, "door"));
        open(player, menu);
    }

    private int addUnavailableFeatureEntries(Menu menu, int slot, RoomFeatureSlot featureSlot, List<FeatureTemplate> unavailable) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These features do not fit this slot."), p -> {});
        for (FeatureTemplate feature : unavailable) {
            if (slot >= 45) {
                break;
            }
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            button(menu, slot++, Material.GRAY_CONCRETE, feature.id(), List.of("Unavailable: " + match.reason(), "Size: " + feature.size()), p -> p.sendMessage(Component.text("Feature " + feature.id() + " unavailable: " + match.reason())));
        }
        return slot;
    }

    private int addUnavailableDoorEntries(Menu menu, int slot, DoorSocket doorSlot, List<DoorTemplate> unavailable) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These doors do not match this slot."), p -> {});
        for (DoorTemplate door : unavailable) {
            if (slot >= 45) {
                break;
            }
            DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(doorSlot, door);
            button(menu, slot++, Material.GRAY_CONCRETE, door.id(), List.of("Unavailable: " + match.reason(), "Bounds: " + door.size(), "Gateway: " + (door.gateway() == null ? "unset" : door.gateway().size() + " " + door.gateway().facing())), p -> p.sendMessage(Component.text("Door " + door.id() + " unavailable: " + match.reason())));
        }
        return slot;
    }

    private List<String> doorEntryLore(DoorSocket slot, String doorId, String... extra) {
        List<String> lore = new ArrayList<>();
        slot.entries().stream()
            .filter(entry -> entry.doorId().equalsIgnoreCase(doorId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits weight.");
        return lore;
    }

    private void toggleDoorEntry(Player player, RoomTemplate template, DoorSocket slot, String doorId, int defaultWeight) {
        List<DoorSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
        if (!removed) {
            entries.add(new DoorSlotEntry(doorId, defaultWeight));
        }
        saveDoorSlot(player, template, slot.withEntries(entries));
    }

    private void promptDoorWeight(Player player, RoomTemplate template, DoorSocket slot, String doorId) {
        prompts.prompt(player, "Enter weight for " + doorId, value -> {
            int weight = Integer.parseInt(value);
            List<DoorSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
            entries.add(new DoorSlotEntry(doorId, weight));
            saveDoorSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveDoorSlot(Player player, RoomTemplate template, DoorSocket updatedSlot) {
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            activeEdit.removeDoor(updatedSlot.id());
            activeEdit.addDoorSlot(updatedSlot);
            player.sendMessage(Component.text("Door slot updated in the active edit session."));
            openDoorSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<DoorSocket> slots = new ArrayList<>(template.doors());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), slots, template.markers(), template.featureSlots(), template.structureFile()));
        player.sendMessage(Component.text("Door slot updated."));
        openDoorSlot(player, template.id(), updatedSlot.id());
    }

    private List<String> entryLore(RoomFeatureSlot slot, String featureId, String... extra) {
        List<String> lore = new ArrayList<>();
        slot.entries().stream()
            .filter(entry -> entry.featureId().equalsIgnoreCase(featureId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits weight.");
        return lore;
    }

    private void toggleFeatureEntry(Player player, RoomTemplate template, RoomFeatureSlot slot, String featureId, int defaultWeight) {
        List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            entries.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
        saveFeatureSlot(player, template, slot.withEntries(entries));
    }

    private void promptFeatureWeight(Player player, RoomTemplate template, RoomFeatureSlot slot, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            int weight = Integer.parseInt(value);
            List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            entries.add(new FeatureSlotEntry(featureId, weight));
            saveFeatureSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveFeatureSlot(Player player, RoomTemplate template, RoomFeatureSlot updatedSlot) {
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            activeEdit.removeFeature(updatedSlot.id());
            activeEdit.addFeatureSlot(updatedSlot);
            player.sendMessage(Component.text("Feature slot updated in the active edit session."));
            openFeatureSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<RoomFeatureSlot> slots = new ArrayList<>(template.featureSlots());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.structureFile()));
        player.sendMessage(Component.text("Feature slot updated."));
        openFeatureSlot(player, template.id(), updatedSlot.id());
    }

    private RoomTemplate removeComponent(RoomTemplate template, String type, String id) {
        List<DoorSocket> doors = new ArrayList<>(template.doors());
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean removed = switch (type) {
            case "door" -> doors.removeIf(door -> door.id().equalsIgnoreCase(id));
            case "marker" -> markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
            case "feature" -> features.removeIf(slot -> slot.id().equalsIgnoreCase(id));
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (!removed) {
            return template;
        }
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), doors, markers, features, template.structureFile());
    }

    private void selectComponent(Player player, String roomId, String type, String id) {
        if (!authoringManager.isEditingRoom(player, roomId)) {
            player.sendMessage(Component.text("Paste this room for editing before inspecting components."));
            openRoom(player, roomId);
            return;
        }
        var selection = authoringManager.selectComponent(player, type, id);
        player.closeInventory();
        player.sendMessage(Component.text("Selected " + type + " " + id + ": " + selection.worldBounds().describe()));
    }

    private void openDeleteComponentConfirm(Player player, String roomId, String type, String id) {
        Menu menu = menu("da:delete-component:" + roomId + ":" + type + ":" + id, 27, "Delete Component?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Deletes " + type + " " + id), p -> {
            boolean removed;
            if (authoringManager.isEditingRoom(p, roomId)) {
                removed = authoringManager.removeComponent(p, type, id);
            } else {
                RoomTemplate template = templateRegistry.get(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
                RoomTemplate updated = removeComponent(template, type, id);
                removed = updated != template;
                if (removed) {
                    saveRoom(updated);
                }
            }
            p.sendMessage(Component.text(removed ? "Deleted " + type + " " + id + "." : "No matching " + type + " named " + id + "."));
            openComponents(p, roomId, type);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openComponents(p, roomId, type));
        open(player, menu);
    }

    private void promptRenameComponent(Player player, String roomId, String type, String oldId) {
        prompts.prompt(player, "Enter new " + type + " id", value -> {
            try {
                String newId = value.trim();
                boolean renamed;
                if (authoringManager.isEditingRoom(player, roomId)) {
                    renamed = authoringManager.renameComponent(player, type, oldId, newId);
                } else {
                    RoomTemplate template = templateRegistry.get(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
                    RoomTemplate updated = renameComponent(template, type, oldId, newId);
                    renamed = updated != template;
                    if (renamed) {
                        saveRoom(updated);
                    }
                }
                player.sendMessage(Component.text(renamed ? "Renamed " + type + " " + oldId + " to " + newId + "." : "No matching " + type + " named " + oldId + "."));
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
            }
            openComponents(player, roomId, type);
        });
    }

    private RoomTemplate renameComponent(RoomTemplate template, String type, String oldId, String newId) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("New id is required");
        }
        List<DoorSocket> doors = new ArrayList<>(template.doors());
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean renamed = switch (type) {
            case "door" -> {
                if (doors.stream().anyMatch(door -> door.id().equalsIgnoreCase(newId))) {
                    throw new IllegalArgumentException("Door already exists: " + newId);
                }
                boolean found = false;
                for (int i = 0; i < doors.size(); i++) {
                    DoorSocket door = doors.get(i);
                    if (door.id().equalsIgnoreCase(oldId)) {
                        doors.set(i, new DoorSocket(newId, door.position(), door.facing(), door.socketType(), door.width(), door.height(), door.size(), door.tags(), door.entries()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "marker" -> {
                if (markers.stream().anyMatch(marker -> marker.name().equalsIgnoreCase(newId))) {
                    throw new IllegalArgumentException("Marker already exists: " + newId);
                }
                boolean found = false;
                for (int i = 0; i < markers.size(); i++) {
                    RoomMarker marker = markers.get(i);
                    if (marker.name().equalsIgnoreCase(oldId)) {
                        markers.set(i, new RoomMarker(newId, marker.type(), marker.position()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "feature" -> {
                if (features.stream().anyMatch(slot -> slot.id().equalsIgnoreCase(newId))) {
                    throw new IllegalArgumentException("Feature slot already exists: " + newId);
                }
                boolean found = false;
                for (int i = 0; i < features.size(); i++) {
                    RoomFeatureSlot slot = features.get(i);
                    if (slot.id().equalsIgnoreCase(oldId)) {
                        features.set(i, new RoomFeatureSlot(newId, slot.position(), slot.size(), slot.facing(), slot.entries()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            default -> throw new IllegalArgumentException("Unknown component type " + type);
        };
        if (!renamed) {
            return template;
        }
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.tags(), template.size(), template.spawn(), doors, markers, features, template.structureFile());
    }

    public void openDoors(Player player) {
        Menu menu = menu("da:doors", 54, "DungeonArchitect Doors");
        int slot = 0;
        for (DoorTemplate template : doorRegistry.all()) {
            button(menu, slot++, Material.OAK_DOOR, template.id(), List.of("Size: " + template.size(), "Tags: " + String.join(",", template.tags()), "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size()), "Click to edit."), p -> openDoor(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openDoor(Player player, String doorId) {
        DoorTemplate template = doorRegistry.get(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        Menu menu = menu("da:door-template:" + doorId, 54, "Door: " + doorId);
        button(menu, 4, Material.OAK_DOOR, "Size: " + template.size(), List.of("Captured door footprint."), p -> openDoor(p, doorId));
        button(menu, 10, template.gateway() == null ? Material.RED_CONCRETE : Material.LIME_CONCRETE, "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size() + " " + template.gateway().facing()), List.of("Set with /da door gateway.", "Right click to select in edit world."), p -> openDoor(p, doorId), p -> selectDoorComponent(p, doorId, "gateway", "gateway"));
        button(menu, 12, Material.OAK_SIGN, "Tags: " + String.join(",", template.tags()), List.of("Comma separated tags."), p -> prompts.prompt(p, "Enter comma-separated tags", value -> {
            Set<String> tags = new LinkedHashSet<>();
            for (String tag : value.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.trim());
                }
            }
            saveDoorTemplate(new DoorTemplate(template.id(), template.size(), tags, template.markers(), template.featureSlots(), template.gateway(), template.structureFile()));
            p.sendMessage(Component.text("Door tags updated."));
            openDoor(p, doorId);
        }));
        button(menu, 20, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this door into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editDoorSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted door " + template.id() + " into the edit world."));
            });
        });
        button(menu, 22, authoringManager.activeDoorId(player).filter(id -> id.equalsIgnoreCase(doorId)).isPresent() ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this door from the edit world."), p -> {
            try {
                TemplateValidationResult result = authoringManager.saveDoor(p, doorId);
                sendValidation(p, result);
                doorRegistry.reload();
                templateRegistry.reload();
                openDoor(p, doorId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openDoor(p, doorId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Door edit session cancelled."));
            openDoor(p, doorId);
        });
        button(menu, 28, Material.REDSTONE_TORCH, "Markers: " + markers.size(), markers.stream().map(marker -> marker.name() + " " + marker.type()).toList(), p -> openDoorComponents(p, doorId, "marker"));
        button(menu, 30, Material.CHEST, "Feature Slots: " + features.size(), features.stream().map(slot -> slot.id() + " size=" + slot.size()).toList(), p -> openDoorComponents(p, doorId, "feature"));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openDoors);
        open(player, menu);
    }

    private void openDoorComponents(Player player, String doorId, String type) {
        DoorTemplate template = doorRegistry.get(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        Menu menu = menu("da:door-components:" + doorId + ":" + type, 54, "Door " + titleCase(type) + "s: " + doorId);
        int slot = 0;
        if (type.equals("marker")) {
            var markers = activeEdit == null ? template.markers() : activeEdit.markers();
            for (var marker : markers) {
                button(menu, slot++, Material.REDSTONE_TORCH, marker.name(), List.of("Type: " + marker.type(), "Position: " + marker.position(), "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openDoorComponents(p, doorId, type), p -> selectDoorComponent(p, doorId, type, marker.name()), p -> promptRenameDoorComponent(p, doorId, type, marker.name()), p -> openDeleteDoorComponentConfirm(p, doorId, type, marker.name()));
                if (slot >= 45) {
                    break;
                }
            }
        } else if (type.equals("feature")) {
            var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
            for (var feature : features) {
                button(menu, slot++, Material.CHEST, feature.id(), List.of("Position: " + feature.position(), "Size: " + feature.size(), "Left click to edit entries.", "Right click to select in edit world.", "Shift-left to rename.", "Shift-right to delete."), p -> openDoorFeatureSlot(p, doorId, feature.id()), p -> selectDoorComponent(p, doorId, type, feature.id()), p -> promptRenameDoorComponent(p, doorId, type, feature.id()), p -> openDeleteDoorComponentConfirm(p, doorId, type, feature.id()));
                if (slot >= 45) {
                    break;
                }
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openDoor(p, doorId));
        open(player, menu);
    }

    private void openDoorFeatureSlot(Player player, String doorId, String slotId) {
        DoorTemplate template = doorRegistry.get(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        List<RoomFeatureSlot> featureSlots = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        RoomFeatureSlot featureSlot = featureSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature slot " + slotId));
        Menu menu = menu("da:door-feature-slot:" + doorId + ":" + slotId, 54, "Door Feature: " + slotId);
        button(menu, 4, Material.HOPPER, "Slot " + slotId, List.of("Size: " + featureSlot.size(), "Position: " + featureSlot.position()), p -> openDoorFeatureSlot(p, doorId, slotId));
        int slot = 9;
        if (featureSlot.entries().isEmpty()) {
            button(menu, 5, Material.YELLOW_CONCRETE, "No Entries Selected", List.of("This slot always pastes nothing."), p -> openDoorFeatureSlot(p, doorId, slotId));
        }
        button(menu, slot++, Material.BARRIER, "empty", entryLore(featureSlot, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> toggleDoorFeatureEntry(p, template, featureSlot, FeatureSlotEntry.EMPTY, 1), null, p -> promptDoorFeatureWeight(p, template, featureSlot, FeatureSlotEntry.EMPTY));
        List<FeatureTemplate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.all()) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(featureSlot, feature);
            if (!match.matched()) {
                unavailable.add(feature);
                continue;
            }
            Rotation rotation = match.rotation();
            IntVector3 rotatedSize = rotation.rotateSize(feature.size());
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), entryLore(featureSlot, feature.id(), "Size: " + feature.size(), "Paste size: " + rotatedSize, "Offset: " + FeatureMatcher.placementOffset(featureSlot.size(), rotatedSize), "Rotation: " + rotation), p -> toggleDoorFeatureEntry(p, template, featureSlot, feature.id(), 1), null, p -> promptDoorFeatureWeight(p, template, featureSlot, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        slot = addUnavailableFeatureEntries(menu, slot, featureSlot, unavailable);
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openDoorComponents(p, doorId, "feature"));
        open(player, menu);
    }

    private void toggleDoorFeatureEntry(Player player, DoorTemplate template, RoomFeatureSlot slot, String featureId, int defaultWeight) {
        List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
        boolean removed = entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            entries.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
        saveDoorFeatureSlot(player, template, slot.withEntries(entries));
    }

    private void promptDoorFeatureWeight(Player player, DoorTemplate template, RoomFeatureSlot slot, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            int weight = Integer.parseInt(value);
            List<FeatureSlotEntry> entries = new ArrayList<>(slot.entries());
            entries.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            entries.add(new FeatureSlotEntry(featureId, weight));
            saveDoorFeatureSlot(player, template, slot.withEntries(entries));
        });
    }

    private void saveDoorFeatureSlot(Player player, DoorTemplate template, RoomFeatureSlot updatedSlot) {
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            activeEdit.removeFeature(updatedSlot.id());
            activeEdit.addFeatureSlot(updatedSlot);
            player.sendMessage(Component.text("Door feature slot updated in the active edit session."));
            openDoorFeatureSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<RoomFeatureSlot> slots = new ArrayList<>(template.featureSlots());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveDoorTemplate(new DoorTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.gateway(), template.structureFile()));
        player.sendMessage(Component.text("Door feature slot updated."));
        openDoorFeatureSlot(player, template.id(), updatedSlot.id());
    }

    private void selectDoorComponent(Player player, String doorId, String type, String id) {
        if (authoringManager.editingDoorSession(player, doorId).isEmpty()) {
            player.sendMessage(Component.text("Paste this door for editing before inspecting components."));
            openDoor(player, doorId);
            return;
        }
        var selection = authoringManager.selectComponent(player, type, id);
        player.closeInventory();
        player.sendMessage(Component.text("Selected " + type + " " + id + ": " + selection.worldBounds().describe()));
    }

    private void openDeleteDoorComponentConfirm(Player player, String doorId, String type, String id) {
        Menu menu = menu("da:delete-door-component:" + doorId + ":" + type + ":" + id, 27, "Delete Component?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Deletes " + type + " " + id), p -> {
            boolean removed;
            AuthoringSession activeEdit = authoringManager.editingDoorSession(p, doorId).orElse(null);
            if (activeEdit != null) {
                removed = type.equals("marker") ? activeEdit.removeMarker(id) : activeEdit.removeFeature(id);
            } else {
                DoorTemplate template = doorRegistry.get(doorId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
                DoorTemplate updated = removeDoorTemplateComponent(template, type, id);
                removed = updated != template;
                if (removed) {
                    saveDoorTemplate(updated);
                }
            }
            p.sendMessage(Component.text(removed ? "Deleted " + type + " " + id + "." : "No matching " + type + " named " + id + "."));
            openDoorComponents(p, doorId, type);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDoorComponents(p, doorId, type));
        open(player, menu);
    }

    private void promptRenameDoorComponent(Player player, String doorId, String type, String oldId) {
        prompts.prompt(player, "Enter new " + type + " id", value -> {
            try {
                String newId = value.trim();
                boolean renamed;
                AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
                if (activeEdit != null) {
                    renamed = type.equals("marker") ? activeEdit.renameMarker(oldId, newId) : activeEdit.renameFeatureSlot(oldId, newId);
                    if (renamed) {
                        authoringManager.selectComponent(player, type, newId);
                    }
                } else {
                    DoorTemplate template = doorRegistry.get(doorId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
                    DoorTemplate updated = renameDoorTemplateComponent(template, type, oldId, newId);
                    renamed = updated != template;
                    if (renamed) {
                        saveDoorTemplate(updated);
                    }
                }
                player.sendMessage(Component.text(renamed ? "Renamed " + type + " " + oldId + " to " + newId + "." : "No matching " + type + " named " + oldId + "."));
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
            }
            openDoorComponents(player, doorId, type);
        });
    }

    private DoorTemplate removeDoorTemplateComponent(DoorTemplate template, String type, String id) {
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean removed = switch (type) {
            case "marker" -> markers.removeIf(marker -> marker.name().equalsIgnoreCase(id));
            case "feature" -> features.removeIf(slot -> slot.id().equalsIgnoreCase(id));
            default -> throw new IllegalArgumentException("Unknown door component type " + type);
        };
        if (!removed) {
            return template;
        }
        return new DoorTemplate(template.id(), template.size(), template.tags(), markers, features, template.gateway(), template.structureFile());
    }

    private DoorTemplate renameDoorTemplateComponent(DoorTemplate template, String type, String oldId, String newId) {
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("New id is required");
        }
        List<RoomMarker> markers = new ArrayList<>(template.markers());
        List<RoomFeatureSlot> features = new ArrayList<>(template.featureSlots());
        boolean renamed = switch (type) {
            case "marker" -> {
                if (markers.stream().anyMatch(marker -> marker.name().equalsIgnoreCase(newId))) {
                    throw new IllegalArgumentException("Marker already exists: " + newId);
                }
                boolean found = false;
                for (int i = 0; i < markers.size(); i++) {
                    RoomMarker marker = markers.get(i);
                    if (marker.name().equalsIgnoreCase(oldId)) {
                        markers.set(i, new RoomMarker(newId, marker.type(), marker.position()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case "feature" -> {
                if (features.stream().anyMatch(slot -> slot.id().equalsIgnoreCase(newId))) {
                    throw new IllegalArgumentException("Feature slot already exists: " + newId);
                }
                boolean found = false;
                for (int i = 0; i < features.size(); i++) {
                    RoomFeatureSlot slot = features.get(i);
                    if (slot.id().equalsIgnoreCase(oldId)) {
                        features.set(i, new RoomFeatureSlot(newId, slot.position(), slot.size(), slot.facing(), slot.entries()));
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            default -> throw new IllegalArgumentException("Unknown door component type " + type);
        };
        if (!renamed) {
            return template;
        }
        return new DoorTemplate(template.id(), template.size(), template.tags(), markers, features, template.gateway(), template.structureFile());
    }

    private void promptRenameRoom(Player player, String roomId) {
        prompts.prompt(player, "Enter new room id", value -> {
            try {
                var renamed = templateRegistry.renameRoom(roomId, value.trim());
                authoringManager.renameActiveRoomId(player, roomId, renamed.id());
                player.sendMessage(Component.text("Renamed room " + roomId + " to " + renamed.id() + "."));
                openRoom(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openRoom(player, roomId);
            }
        });
    }

    private void promptDuplicateRoom(Player player, String roomId) {
        prompts.prompt(player, "Enter duplicate room id", value -> {
            try {
                var duplicated = templateRegistry.duplicateRoom(roomId, value.trim());
                player.sendMessage(Component.text("Duplicated room " + roomId + " to " + duplicated.id() + "."));
                openRoom(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openRoom(player, roomId);
            }
        });
    }

    private void promptRenameFeature(Player player, String featureId) {
        prompts.prompt(player, "Enter new feature id", value -> {
            try {
                var renamed = featureRegistry.renameFeature(featureId, value.trim());
                templateRegistry.replaceFeatureReferences(featureId, renamed.id());
                authoringManager.renameActiveFeatureId(player, featureId, renamed.id());
                player.sendMessage(Component.text("Renamed feature " + featureId + " to " + renamed.id() + "."));
                openFeature(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openFeature(player, featureId);
            }
        });
    }

    private void promptDuplicateFeature(Player player, String featureId) {
        prompts.prompt(player, "Enter duplicate feature id", value -> {
            try {
                var duplicated = featureRegistry.duplicateFeature(featureId, value.trim());
                templateRegistry.reload();
                player.sendMessage(Component.text("Duplicated feature " + featureId + " to " + duplicated.id() + "."));
                openFeature(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openFeature(player, featureId);
            }
        });
    }

    private void openDeleteRoomConfirm(Player player, String roomId) {
        Menu menu = menu("da:delete-room:" + roomId, 27, "Delete Room?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + roomId), p -> {
            try {
                templateRegistry.deleteRoom(roomId);
                p.sendMessage(Component.text("Deleted room " + roomId));
                openRooms(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openRoom(p, roomId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openRoom(p, roomId));
        open(player, menu);
    }

    public void openFeatures(Player player) {
        Menu menu = menu("da:features", 54, "DungeonArchitect Features");
        int slot = 0;
        for (FeatureTemplate template : featureRegistry.all()) {
            button(menu, slot++, Material.STRUCTURE_BLOCK, template.id(), List.of("Size: " + template.size(), "Tags: " + String.join(",", template.tags()), "Click to edit."), p -> openFeature(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openFeature(Player player, String featureId) {
        FeatureTemplate template = featureRegistry.get(featureId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature " + featureId));
        Menu menu = menu("da:feature-template:" + featureId, 54, "Feature: " + featureId);
        button(menu, 4, Material.LIME_CONCRETE, "Size: " + template.size(), List.of("Captured feature footprint."), p -> openFeature(p, featureId));
        button(menu, 12, Material.OAK_SIGN, "Tags: " + String.join(",", template.tags()), List.of("Comma separated tags."), p -> prompts.prompt(p, "Enter comma-separated tags", value -> {
            Set<String> tags = new LinkedHashSet<>();
            for (String tag : value.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.trim());
                }
            }
            saveFeatureTemplate(new FeatureTemplate(template.id(), template.size(), tags, template.structureFile()));
            p.sendMessage(Component.text("Feature tags updated."));
            openFeature(p, featureId);
        }));
        button(menu, 20, Material.STRUCTURE_BLOCK, "Edit In World", List.of("Paste this feature into the edit world."), p -> {
            p.closeInventory();
            p.sendMessage(Component.text("Preparing isolated edit workspace for " + template.id() + "..."));
            authoringManager.editFeatureSession(p, template).whenComplete((session, error) -> {
                if (error != null) {
                    p.sendMessage(Component.text("Edit paste failed: " + message(error)));
                    return;
                }
                p.sendMessage(Component.text("Pasted feature " + template.id() + " into the edit world."));
            });
        });
        button(menu, 22, authoringManager.activeFeatureId(player).filter(id -> id.equalsIgnoreCase(featureId)).isPresent() ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "Save Edit", List.of("Overwrite this feature from the edit world."), p -> {
            try {
                TemplateValidationResult result = authoringManager.saveFeature(p, featureId);
                sendValidation(p, result);
                featureRegistry.reload();
                templateRegistry.reload();
                openFeature(p, featureId);
            } catch (Exception ex) {
                p.sendMessage(Component.text("Save failed: " + ex.getMessage()));
                openFeature(p, featureId);
            }
        });
        button(menu, 24, Material.BARRIER, "Cancel Edit", List.of("Clear the pasted edit copy."), p -> {
            authoringManager.cancelEdit(p);
            p.sendMessage(Component.text("Feature edit session cancelled."));
            openFeature(p, featureId);
        });
        button(menu, 36, Material.NAME_TAG, "Rename Feature", List.of("Move this feature template to a new id."), p -> promptRenameFeature(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Feature", List.of("Copy this feature template to a new id."), p -> promptDuplicateFeature(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Feature", List.of("Permanently delete this feature template."), p -> openDeleteFeatureConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openFeatures);
        open(player, menu);
    }

    private void openDeleteFeatureConfirm(Player player, String featureId) {
        Menu menu = menu("da:delete-feature:" + featureId, 27, "Delete Feature?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + featureId), p -> {
            try {
                featureRegistry.deleteFeature(featureId);
                templateRegistry.reload();
                p.sendMessage(Component.text("Deleted feature " + featureId));
                openFeatures(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openFeature(p, featureId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openFeature(p, featureId));
        open(player, menu);
    }

    public void openConfig(Player player) {
        Menu menu = menu("da:config", 54, "DungeonArchitect Config");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        int slot = fillConfig(menu, config, new File(plugin.getDataFolder(), "config.yml"), "", 0);
        button(menu, Math.min(slot, 45), Material.CHEST, "Features", List.of("Open captured feature templates."), this::openFeatures);
        button(menu, 49, Material.EMERALD, "Reload", List.of("Reload config, rooms, and features."), p -> {
            reloadAll.run();
            p.sendMessage(Component.text("DungeonArchitect reloaded."));
            openConfig(p);
        });
        button(menu, 53, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openDungeons(Player player) {
        Menu menu = menu("da:dungeons", 54, "Active Dungeons");
        int slot = 0;
        DungeonInstance current = dungeonManager.getDungeon(player).orElse(null);
        if (current != null) {
            addDungeon(menu, slot++, current, true);
        }
        for (DungeonInstance instance : dungeonManager.instances()) {
            if (current != null && current.id().equals(instance.id())) {
                continue;
            }
            addDungeon(menu, slot++, instance, false);
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    private void addDungeon(Menu menu, int slot, DungeonInstance instance, boolean current) {
        String alias = "#" + dungeonManager.alias(instance).orElse(0);
        button(menu, slot, current ? Material.ENDER_EYE : Material.ENDER_PEARL, alias + " " + instance.id(), List.of("State: " + instance.state(), "Rooms: " + instance.rooms().size(), "Seed: " + instance.seed(), "Click to manage."), p -> openDungeon(p, instance));
    }

    public void openDungeon(Player player, DungeonInstance instance) {
        Menu menu = menu("da:dungeon:" + instance.id(), 27, "Dungeon " + dungeonManager.alias(instance).map(i -> "#" + i).orElse(""));
        button(menu, 10, Material.OAK_DOOR, "Exit", List.of("Teleport yourself out of this dungeon."), p -> {
            dungeonManager.exitDungeon(p);
            p.sendMessage(Component.text("Exited dungeon."));
            openDungeons(p);
        });
        button(menu, 12, Material.COMPASS, "Teleport Room", List.of("Enter room index in chat."), p -> prompts.prompt(p, "Enter room index", value -> teleportToRoom(p, instance, Integer.parseInt(value))));
        button(menu, 14, Material.RED_CONCRETE, "Destroy", List.of("Click to confirm destroy."), p -> openDestroyConfirm(p, instance));
        button(menu, 16, Material.PAPER, "Debug", List.of("Send instance summary to chat."), p -> {
            p.sendMessage(Component.text(instance.id() + " state=" + instance.state() + " rooms=" + instance.rooms().size() + " seed=" + instance.seed()));
            openDungeon(p, instance);
        });
        button(menu, 22, Material.ARROW, "Back", List.of(), this::openDungeons);
        open(player, menu);
    }

    private void openDestroyConfirm(Player player, DungeonInstance instance) {
        Menu menu = menu("da:confirm:" + instance.id(), 27, "Destroy Dungeon?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Destroy", List.of(instance.id().toString()), p -> {
            dungeonManager.destroyDungeon(instance.id());
            p.sendMessage(Component.text("Destroyed dungeon."));
            openDungeons(p);
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDungeon(p, instance));
        open(player, menu);
    }

    private int fillConfig(Menu menu, YamlConfiguration yaml, File file, String path, int slot) {
        ConfigurationSection section = path.isBlank() ? yaml : yaml.getConfigurationSection(path);
        if (section == null) {
            return slot;
        }
        for (String key : section.getKeys(false)) {
            String fullPath = path.isBlank() ? key : path + "." + key;
            if (yaml.isConfigurationSection(fullPath)) {
                slot = fillConfig(menu, yaml, file, fullPath, slot);
                continue;
            }
            Object value = yaml.get(fullPath);
            int target = slot++;
            button(menu, target, Material.COMPARATOR, fullPath + ": " + value, List.of("Click to edit."), p -> prompts.prompt(p, "Enter value for " + fullPath, input -> {
                yaml.set(fullPath, parseScalar(input));
                saveYaml(yaml, file);
                reloadAll.run();
                p.sendMessage(Component.text(fullPath + " updated."));
                openConfig(p);
            }));
            if (slot >= 45) {
                break;
            }
        }
        return slot;
    }

    private void teleportToRoom(Player player, DungeonInstance instance, int index) {
        if (index < 0 || index >= instance.rooms().size()) {
            throw new IllegalArgumentException("Room index out of range");
        }
        var room = instance.rooms().get(index);
        var world = Bukkit.getWorld(instance.worldName());
        if (world == null) {
            throw new IllegalArgumentException("Dungeon world is not loaded");
        }
        var origin = room.node().transform().origin();
        player.teleport(new org.bukkit.Location(world, origin.x() + 0.5, origin.y() + 1, origin.z() + 0.5));
    }

    private void saveRoom(RoomTemplate template) {
        Path roomDir = template.structureFile().getParent();
        try {
            RoomTemplateIO.save(template, roomDir);
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void saveFeatureTemplate(FeatureTemplate template) {
        Path featureDir = template.structureFile().getParent();
        try {
            FeatureTemplateIO.save(template, featureDir);
            featureRegistry.reload();
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void saveDoorTemplate(DoorTemplate template) {
        Path doorDir = template.structureFile().getParent();
        try {
            DoorTemplateIO.save(template, doorDir);
            doorRegistry.reload();
            templateRegistry.reload();
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Object parseScalar(String input) {
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(input);
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return input;
        }
    }

    private IntVector3 parseVector(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected x,y,z");
        }
        return new IntVector3(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }

    private <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E value : type.getEnumConstants()) {
            names.add(value.name());
        }
        return names;
    }

    private RoomCategory nextCategory(RoomCategory current) {
        RoomCategory[] values = RoomCategory.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private String titleCase(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private void sendValidation(Player player, TemplateValidationResult result) {
        if (result.valid()) {
            player.sendMessage(Component.text("Validation OK."));
            return;
        }
        player.sendMessage(Component.text("Validation errors:"));
        for (String error : result.errors()) {
            player.sendMessage(Component.text("- " + error));
        }
    }

    private String message(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private void saveYaml(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Menu menu(String id, int size, String title) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(id), size, Component.text(title));
        return new Menu(inventory, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action) {
        button(menu, slot, material, name, lore, action, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction) {
        button(menu, slot, material, name, lore, action, rightClickAction, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction, MenuAction shiftLeftAction) {
        button(menu, slot, material, name, lore, action, rightClickAction, shiftLeftAction, null);
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action, MenuAction rightClickAction, MenuAction shiftLeftAction, MenuAction shiftRightAction) {
        ItemStack item = GuiItems.item(material, name, lore);
        menu.inventory.setItem(slot, item);
        menu.actions.put(slot, action);
        if (rightClickAction != null) {
            menu.rightClickActions.put(slot, rightClickAction);
        }
        if (shiftLeftAction != null) {
            menu.shiftLeftActions.put(slot, shiftLeftAction);
        }
        if (shiftRightAction != null) {
            menu.shiftRightActions.put(slot, shiftRightAction);
        }
    }

    private void open(Player player, Menu menu) {
        actions.put(player.getUniqueId(), new PlayerMenuActions(menu.actions, menu.rightClickActions, menu.shiftLeftActions, menu.shiftRightActions));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        PlayerMenuActions playerActions = actions.getOrDefault(player.getUniqueId(), new PlayerMenuActions(Map.of(), Map.of(), Map.of(), Map.of()));
        MenuAction action = null;
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            action = playerActions.shiftRightActions().get(event.getRawSlot());
        }
        if (action == null && event.getClick() == ClickType.SHIFT_LEFT) {
            action = playerActions.shiftLeftActions().get(event.getRawSlot());
        }
        if (action == null) {
            action = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT
                ? playerActions.rightClickActions().get(event.getRawSlot())
                : playerActions.actions().get(event.getRawSlot());
        }
        if (action == null) {
            action = playerActions.actions().get(event.getRawSlot());
        }
        if (action != null) {
            action.click(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            UUID playerId = event.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)) {
                    actions.remove(playerId);
                }
            });
        }
    }

    private record Menu(Inventory inventory, Map<Integer, MenuAction> actions, Map<Integer, MenuAction> rightClickActions, Map<Integer, MenuAction> shiftLeftActions, Map<Integer, MenuAction> shiftRightActions) {
    }

    private record PlayerMenuActions(Map<Integer, MenuAction> actions, Map<Integer, MenuAction> rightClickActions, Map<Integer, MenuAction> shiftLeftActions, Map<Integer, MenuAction> shiftRightActions) {
    }
}
