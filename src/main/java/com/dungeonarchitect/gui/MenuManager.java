package com.dungeonarchitect.gui;

import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.AuthoringSession;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorConnectionRules;
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
import com.dungeonarchitect.domain.TagDomain;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.template.DiagnosticText;
import com.dungeonarchitect.template.TemplateDiagnostic;
import com.dungeonarchitect.template.TemplateDiagnostics;
import com.dungeonarchitect.template.TemplateLoadStatus;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomTemplateValidator;
import com.dungeonarchitect.template.AssetRenameCoordinator;
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
import java.util.Collection;
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
    private final AssetRenameCoordinator assetRenameCoordinator;
    private final TagCatalog tagCatalog;
    private final Map<UUID, PlayerMenuActions> actions = new HashMap<>();
    private final Map<UUID, MultiEditSession> multiEdits = new HashMap<>();
    private final Map<UUID, TagSelection> tagSelections = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, Runnable reloadAll) {
        this(plugin, authoringManager, templateRegistry, featureRegistry, doorRegistry, dungeonManager, prompts,
            new AssetRenameCoordinator(templateRegistry, featureRegistry, doorRegistry), new TagCatalog(plugin.getDataFolder().toPath().resolve("tags.yml")), reloadAll);
    }

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, AssetRenameCoordinator assetRenameCoordinator, Runnable reloadAll) {
        this(plugin, authoringManager, templateRegistry, featureRegistry, doorRegistry, dungeonManager, prompts, assetRenameCoordinator, new TagCatalog(plugin.getDataFolder().toPath().resolve("tags.yml")), reloadAll);
    }

    public MenuManager(Plugin plugin, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, FeatureTemplateRegistry featureRegistry, DoorTemplateRegistry doorRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, AssetRenameCoordinator assetRenameCoordinator, TagCatalog tagCatalog, Runnable reloadAll) {
        this.plugin = plugin;
        this.authoringManager = authoringManager;
        this.templateRegistry = templateRegistry;
        this.featureRegistry = featureRegistry;
        this.doorRegistry = doorRegistry;
        this.dungeonManager = dungeonManager;
        this.prompts = prompts;
        this.assetRenameCoordinator = assetRenameCoordinator;
        this.tagCatalog = tagCatalog;
        this.reloadAll = reloadAll;
    }

    public void openMain(Player player) {
        Menu menu = menu("da:main", 27, "DungeonArchitect");
        button(menu, 10, Material.BOOKSHELF, "Rooms", List.of("Edit room metadata and validate templates."), this::openRooms);
        button(menu, 12, Material.OAK_DOOR, "Doors", List.of("Edit reusable door templates."), this::openDoors);
        button(menu, 14, Material.STRUCTURE_BLOCK, "Features", List.of("Edit reusable feature templates."), this::openFeatures);
        button(menu, 16, Material.ENDER_PEARL, "Dungeons", List.of("Manage active dungeon instances."), this::openDungeons);
        button(menu, 18, Material.SPYGLASS, "Diagnostics", diagnosticSummaryLore(), this::openDiagnostics);
        button(menu, 22, Material.COMPARATOR, "Config", List.of("Edit config.yml."), this::openConfig);
        open(player, menu);
    }

    public void openDiagnostics(Player player) {
        openDiagnostics(player, 0);
    }

    private void openDiagnostics(Player player, int page) {
        TemplateValidationResult result = TemplateDiagnostics.analyze(templateRegistry, featureRegistry, doorRegistry);
        List<TemplateDiagnostic> diagnostics = result.diagnostics();
        Menu menu = menu("da:diagnostics:" + page, 54, "Diagnostics");
        button(menu, 4, result.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, result.valid() ? "No Blocking Issues" : "Issues Found", List.of("Errors: " + result.errors().size(), "Warnings: " + result.warnings().size(), "Repairs: " + result.repairs().size()), p -> openDiagnostics(p, page));
        int start = page * 36;
        int slot = 9;
        for (int i = start; i < diagnostics.size() && slot < 45; i++) {
            TemplateDiagnostic diagnostic = diagnostics.get(i);
            Material material = diagnostic.severity() == com.dungeonarchitect.template.DiagnosticSeverity.ERROR ? Material.RED_CONCRETE : Material.YELLOW_CONCRETE;
            button(menu, slot++, material, diagnostic.severity() + " " + targetName(diagnostic), diagnosticLore(diagnostic), p -> {
                p.sendMessage(Component.text(diagnostic.display()));
                if (diagnostic.suggestion() != null) {
                    p.sendMessage(Component.text("Fix: " + diagnostic.suggestion()));
                }
            });
        }
        if (page > 0) {
            button(menu, 45, Material.ARROW, "Previous", List.of(), p -> openDiagnostics(p, page - 1));
        }
        if (diagnostics.size() > start + 36) {
            button(menu, 53, Material.ARROW, "Next", List.of(), p -> openDiagnostics(p, page + 1));
        }
        button(menu, 49, Material.BARRIER, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void prompt(Player player, String message, Consumer<String> handler) {
        prompts.prompt(player, message, handler);
    }

    public void openRooms(Player player) {
        Menu menu = menu("da:rooms", 54, "DungeonArchitect Rooms");
        int slot = 0;
        for (RoomTemplate template : templateRegistry.visible()) {
            int target = slot++;
            TemplateLoadStatus<RoomTemplate> status = templateRegistry.status(template.id()).orElse(null);
            button(menu, target, statusValid(status) ? Material.PAPER : Material.RED_CONCRETE, template.id(), loadStatusLore(status, "Category: " + template.category(), "Door Slots: " + template.doors().size(), "Click to edit."), p -> openRoom(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openRoom(Player player, String roomId) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        Menu menu = menu("da:room:" + roomId, 54, "Room: " + roomId);
        TemplateLoadStatus<RoomTemplate> status = templateRegistry.status(template.id()).orElse(null);
        var validation = validator.validate(template);
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        var doors = activeEdit == null ? template.doors() : activeEdit.doors();
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        button(menu, 4, statusValid(status) && validation.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, statusValid(status) && validation.valid() ? "Validation OK" : "Validation Errors", status == null ? validation.errors() : loadStatusLore(status), p -> openRoom(p, roomId));
        button(menu, 10, Material.NAME_TAG, "Category: " + template.category(), enumNames(RoomCategory.class), p -> {
            RoomCategory next = nextCategory(template.category());
            if (activeEdit != null) {
                activeEdit.category(next);
            }
            saveRoom(new RoomTemplate(template.id(), next, template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room category changed to " + next + "."));
            openRoom(p, roomId);
        });
        button(menu, 12, Material.GOLD_NUGGET, "Weight: " + template.weight(), List.of("Click to edit generation weight."), p -> prompts.prompt(p, "Enter positive integer weight", value -> {
            saveRoom(new RoomTemplate(template.id(), template.category(), Integer.parseInt(value), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room weight updated."));
            openRoom(p, roomId);
        }));
        button(menu, 14, Material.COMPASS, "Spawn: " + (template.spawn() == null ? "unset" : template.spawn()), List.of("Format: x,y,z or empty to clear."), p -> prompts.prompt(p, "Enter spawn as x,y,z or empty", value -> {
            IntVector3 spawn = value.isBlank() ? null : parseVector(value);
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), spawn, template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room spawn updated."));
            openRoom(p, roomId);
        }));
        Set<String> currentRoomTags = activeEdit == null ? template.tags() : activeEdit.tags();
        button(menu, 16, Material.OAK_SIGN, "Tags: " + String.join(",", currentRoomTags), List.of("Click to select room tags."), p -> openRoomTags(p, template));
        button(menu, 18, Material.IRON_BARS, "Minimum Connections: " + template.minimumConnections(), List.of("Connections required for every generated copy.", "0 allows this room to end a branch."), p -> prompts.prompt(p, "Enter a non-negative minimum connection count", value -> {
            int minimumConnections = Integer.parseInt(value);
            if (minimumConnections < 0) {
                throw new IllegalArgumentException("Minimum connections cannot be negative");
            }
            if (minimumConnections > template.doors().size()) {
                throw new IllegalArgumentException("Minimum connections cannot exceed this room's " + template.doors().size() + " door slots");
            }
            if (activeEdit != null) {
                activeEdit.minimumConnections(minimumConnections);
            }
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), minimumConnections, template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Minimum connections updated."));
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
        RoomTemplate template = templateRegistry.getVisible(roomId)
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
        if (type.equals("door") || type.equals("feature")) {
            Material material = type.equals("door") ? Material.OAK_DOOR : Material.HOPPER;
            button(menu, 45, material, "Multi-edit", List.of("Configure multiple " + type + " slots at once."), p -> beginMultiEdit(p, MultiEditOwner.ROOM, roomId, type.equals("door") ? MultiEditSlotType.DOOR : MultiEditSlotType.FEATURE));
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openRoom(p, roomId));
        open(player, menu);
    }

    private void openFeatureSlot(Player player, String roomId, String slotId) {
        RoomTemplate template = templateRegistry.getVisible(roomId)
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
        RoomTemplate template = templateRegistry.getVisible(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + roomId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, roomId).orElse(null);
        List<DoorSocket> doorSlots = activeEdit == null ? template.doors() : activeEdit.doors();
        DoorSocket doorSlot = doorSlots.stream()
            .filter(slot -> slot.id().equalsIgnoreCase(slotId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown door slot " + slotId));
        Menu menu = menu("da:door-slot:" + roomId + ":" + slotId, 54, "Door Slot: " + slotId);
        button(menu, 0, Material.NAME_TAG, "Tags: " + String.join(",", doorSlot.tags()), List.of("Click to select door tags."), p -> promptDoorTags(p, template, doorSlot));
        button(menu, 1, doorSlot.connectionRules().mustConnect() ? Material.TRIPWIRE_HOOK : Material.GRAY_CONCRETE, "Must Connect: " + doorSlot.connectionRules().mustConnect(), List.of("A generated dungeon is invalid until this slot connects."), p -> toggleDoorMustConnect(p, template, doorSlot));
        button(menu, 2, Material.LIME_DYE, "Accept Tags: " + String.join(",", doorSlot.connectionRules().allowedTags()), List.of("Select opposite-door tags.", "Empty accepts any tag unless rejected."), p -> promptDoorAllowedTags(p, template, doorSlot));
        button(menu, 3, Material.RED_DYE, "Reject Tags: " + String.join(",", doorSlot.connectionRules().deniedTags()), List.of("Select opposite-door tags."), p -> promptDoorDeniedTags(p, template, doorSlot));
        button(menu, 4, Material.IRON_DOOR, "Slot " + slotId, List.of("Size: " + doorSlot.size(), "Position: " + doorSlot.position(), "Facing: " + doorSlot.facing(), "Tags: " + String.join(",", doorSlot.tags())), p -> openDoorSlot(p, roomId, slotId));
        button(menu, 5, Material.COMPASS, "Connection Preview", connectionPreviewLore(template, doorSlot), p -> openDoorSlot(p, roomId, slotId));
        button(menu, 6, Material.LIME_BANNER, "Accept Room Tags: " + String.join(",", doorSlot.connectionRules().allowedRoomTags()), List.of("Select opposite-room tags.", "Empty accepts any room unless rejected."), p -> promptDoorAllowedRoomTags(p, template, doorSlot));
        button(menu, 7, Material.RED_BANNER, "Reject Room Tags: " + String.join(",", doorSlot.connectionRules().deniedRoomTags()), List.of("Select opposite-room tags."), p -> promptDoorDeniedRoomTags(p, template, doorSlot));
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
            button(menu, slot++, Material.GRAY_CONCRETE, feature.id(), List.of("Unavailable: " + match.reason(), "Size: " + DiagnosticText.size(feature.size())), p -> p.sendMessage(Component.text("Feature " + feature.id() + " unavailable: " + match.reason())));
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
            button(menu, slot++, Material.GRAY_CONCRETE, door.id(), List.of("Unavailable: " + match.reason(), "Bounds: " + DiagnosticText.size(door.size()), "Gateway: " + (door.gateway() == null ? "unset" : DiagnosticText.size(door.gateway().size()) + " " + door.gateway().facing())), p -> p.sendMessage(Component.text("Door " + door.id() + " unavailable: " + match.reason())));
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

    private void promptDoorTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Door Slot Tags", slot.tags(), tags -> {
            saveDoorSlot(player, template, slot.withTags(tags));
            player.sendMessage(Component.text("Door slot tags updated."));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void toggleDoorMustConnect(Player player, RoomTemplate template, DoorSocket slot) {
        DoorConnectionRules rules = slot.connectionRules();
        saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), !rules.mustConnect())));
    }

    private void promptDoorAllowedTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Accept Door Tags", slot.connectionRules().allowedTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(tags, rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorDeniedTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.DOOR, "Reject Door Tags", slot.connectionRules().deniedTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), tags, rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorAllowedRoomTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.ROOM, "Accept Room Tags", slot.connectionRules().allowedRoomTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), tags, rules.deniedRoomTags(), rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private void promptDoorDeniedRoomTags(Player player, RoomTemplate template, DoorSocket slot) {
        openTagSelector(player, TagDomain.ROOM, "Reject Room Tags", slot.connectionRules().deniedRoomTags(), tags -> {
            DoorConnectionRules rules = slot.connectionRules();
            saveDoorSlot(player, template, slot.withConnectionRules(new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), tags, rules.mustConnect())));
        }, p -> openDoorSlot(p, template.id(), slot.id()));
    }

    private Set<String> parseTags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : value.split(",")) {
            if (!tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private void openRoomTags(Player player, RoomTemplate template) {
        Set<String> current = authoringManager.editingSession(player, template.id()).map(AuthoringSession::tags).orElse(template.tags());
        openTagSelector(player, TagDomain.ROOM, "Room Tags", current, tags -> {
            RoomTemplate latest = templateRegistry.getVisible(template.id()).orElse(template);
            authoringManager.editingSession(player, template.id()).ifPresent(session -> session.tags(tags));
            saveRoom(new RoomTemplate(latest.id(), latest.category(), latest.weight(), latest.minimumConnections(), tags, latest.size(), latest.spawn(), latest.doors(), latest.markers(), latest.featureSlots(), latest.structureFile()));
            player.sendMessage(Component.text("Room tags updated."));
            openRoom(player, template.id());
        }, p -> openRoom(p, template.id()));
    }

    private void openDoorTemplateTags(Player player, DoorTemplate template) {
        Set<String> current = authoringManager.editingDoorSession(player, template.id()).map(AuthoringSession::tags).orElse(template.tags());
        openTagSelector(player, TagDomain.DOOR, "Door Template Tags", current, tags -> {
            DoorTemplate latest = doorRegistry.getVisible(template.id()).orElse(template);
            authoringManager.editingDoorSession(player, template.id()).ifPresent(session -> session.tags(tags));
            saveDoorTemplate(new DoorTemplate(latest.id(), latest.size(), tags, latest.markers(), latest.featureSlots(), latest.gateway(), latest.structureFile()));
            player.sendMessage(Component.text("Door template tags updated."));
            openDoor(player, template.id());
        }, p -> openDoor(p, template.id()));
    }

    private void openTagSelector(Player player, TagDomain domain, String title, Set<String> selected, Consumer<Set<String>> onSave, Consumer<Player> onCancel) {
        tagSelections.put(player.getUniqueId(), new TagSelection(domain, title, selected, onSave, onCancel));
        openTagSelector(player);
    }

    private void openTagSelector(Player player) {
        TagSelection selection = requireTagSelection(player);
        List<String> filtered = tagCatalog.tags(selection.domain, selection.filter);
        int pageCount = Math.max(1, (filtered.size() + 44) / 45);
        selection.page = Math.min(selection.page, pageCount - 1);
        Menu menu = menu("da:tags:" + selection.domain.name().toLowerCase(Locale.ROOT), 54, selection.title);
        int start = selection.page * 45;
        for (int index = start; index < filtered.size() && index < start + 45; index++) {
            String tag = filtered.get(index);
            int slot = index - start;
            boolean selected = containsTag(selection.selected, tag);
            button(menu, slot, selected ? Material.LIME_DYE : Material.NAME_TAG, tag,
                List.of(selected ? "Selected. Click to remove from this field." : "Click to add to this field.", "Shift-right-click to delete globally."),
                p -> {
                    toggleTag(selection.selected, tag);
                    openTagSelector(p);
                }, null, null, p -> openTagDeleteConfirm(p, tag));
        }
        if (selection.page > 0) {
            button(menu, 45, Material.ARROW, "Previous", List.of(), p -> {
                selection.page--;
                openTagSelector(p);
            });
        }
        button(menu, 47, Material.ANVIL, "Add New Tag", List.of("Create one " + selection.domain.label().toLowerCase(Locale.ROOT) + " entry."), this::promptNewTag);
        button(menu, 49, Material.BARRIER, "Back", List.of("Discard tag changes."), this::cancelTagSelection);
        button(menu, 51, Material.SPYGLASS, "Search: " + (selection.filter.isBlank() ? "all tags" : selection.filter), List.of("Click to filter tags."), this::promptTagSearch);
        if (start + 45 < filtered.size()) {
            button(menu, 52, Material.ARROW, "Next", List.of(), p -> {
                selection.page++;
                openTagSelector(p);
            });
        }
        button(menu, 53, Material.EMERALD_BLOCK, "Done", List.of("Save " + selection.selected.size() + " selected tag(s)."), this::saveTagSelection);
        open(player, menu);
    }

    private void promptTagSearch(Player player) {
        TagSelection selection = requireTagSelection(player);
        prompts.prompt(player, "Search " + selection.domain.label() + " (empty shows all)", value -> {
            selection.filter = value.trim();
            selection.page = 0;
            openTagSelector(player);
        });
    }

    private void promptNewTag(Player player) {
        TagSelection selection = requireTagSelection(player);
        prompts.prompt(player, "Enter one new " + selection.domain.label().toLowerCase(Locale.ROOT), value -> {
            String tag = tagCatalog.add(selection.domain, value);
            addTag(selection.selected, tag);
            selection.filter = "";
            selection.page = 0;
            openTagSelector(player);
        });
    }

    private void saveTagSelection(Player player) {
        TagSelection selection = requireTagSelection(player);
        tagSelections.remove(player.getUniqueId());
        selection.onSave.accept(Set.copyOf(selection.selected));
    }

    private void cancelTagSelection(Player player) {
        TagSelection selection = tagSelections.remove(player.getUniqueId());
        if (selection != null) {
            selection.onCancel.accept(player);
        }
    }

    private void openTagDeleteConfirm(Player player, String tag) {
        TagSelection selection = requireTagSelection(player);
        TagCleanupService.Result result = TagCleanupService.remove(selection.domain, tag, templateRegistry.visible(), doorRegistry.visible());
        Menu menu = menu("da:tag-delete:" + selection.domain.name().toLowerCase(Locale.ROOT), 27, "Delete Tag: " + tag);
        button(menu, 11, Material.RED_CONCRETE, "Delete Globally", List.of("Remove from " + result.affectedFields() + " tag field(s).", "This cannot be undone."), p -> deleteTagGlobally(p, tag));
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), this::openTagSelector);
        open(player, menu);
    }

    private void deleteTagGlobally(Player player, String tag) {
        TagSelection selection = requireTagSelection(player);
        TagCleanupService.Result result = TagCleanupService.remove(selection.domain, tag, templateRegistry.visible(), doorRegistry.visible());
        try {
            for (RoomTemplate room : result.rooms()) {
                RoomTemplateIO.save(room, room.structureFile().getParent());
            }
            for (DoorTemplate door : result.doors()) {
                DoorTemplateIO.save(door, door.structureFile().getParent());
            }
            tagCatalog.remove(selection.domain, tag);
            authoringManager.removeTag(selection.domain, tag);
            reloadAll.run();
            player.sendMessage(Component.text("Deleted " + tag + " from " + result.affectedFields() + " tag field(s)."));
            cancelTagSelection(player);
        } catch (IOException ex) {
            player.sendMessage(Component.text("Tag deletion failed: " + ex.getMessage()));
            openTagSelector(player);
        }
    }

    private TagSelection requireTagSelection(Player player) {
        TagSelection selection = tagSelections.get(player.getUniqueId());
        if (selection == null) {
            throw new IllegalStateException("No active tag selection");
        }
        return selection;
    }

    private static boolean containsTag(Collection<String> tags, String tag) {
        return tags.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(tag));
    }

    private static void addTag(Set<String> tags, String tag) {
        tags.removeIf(candidate -> candidate.equalsIgnoreCase(tag));
        tags.add(tag);
    }

    private static void toggleTag(Set<String> tags, String tag) {
        if (!tags.removeIf(candidate -> candidate.equalsIgnoreCase(tag))) {
            tags.add(tag);
        }
    }

    private List<String> connectionPreviewLore(RoomTemplate owner, DoorSocket slot) {
        List<String> lore = new ArrayList<>();
        int matches = 0;
        for (RoomTemplate candidateRoom : templateRegistry.all()) {
            for (DoorSocket candidate : candidateRoom.doors()) {
                if (candidateRoom.id().equals(owner.id()) && candidate.id().equals(slot.id())) {
                    continue;
                }
                if (candidate.facing() != slot.facing().opposite()) {
                    continue;
                }
                DoorSocket.ConnectionMatch match = slot.connectionMatch(candidate, owner.tags(), candidateRoom.tags());
                if (match.compatible()) {
                    matches++;
                    if (lore.size() < 5) {
                        lore.add("Compatible: " + candidateRoom.id() + ":" + candidate.id());
                    }
                } else if (lore.size() < 5) {
                    lore.add("Unavailable: " + candidateRoom.id() + ":" + candidate.id() + " - " + match.reason());
                }
            }
        }
        lore.add(0, "Opposite-facing rule matches: " + matches);
        if (lore.size() == 1) {
            lore.add("No opposite-facing room slots found.");
        }
        return lore;
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
            List<DoorSocket> persistedSlots = new ArrayList<>(template.doors());
            persistedSlots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
            saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), persistedSlots, template.markers(), template.featureSlots(), template.structureFile()));
            player.sendMessage(Component.text("Door slot updated in the active edit session."));
            openDoorSlot(player, template.id(), updatedSlot.id());
            return;
        }
        List<DoorSocket> slots = new ArrayList<>(template.doors());
        slots.replaceAll(slot -> slot.id().equalsIgnoreCase(updatedSlot.id()) ? updatedSlot : slot);
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), slots, template.markers(), template.featureSlots(), template.structureFile()));
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
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.structureFile()));
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
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), doors, markers, features, template.structureFile());
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
                RoomTemplate template = templateRegistry.getVisible(roomId)
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
                    RoomTemplate template = templateRegistry.getVisible(roomId)
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
                        doors.set(i, new DoorSocket(newId, door.position(), door.facing(), door.socketType(), door.width(), door.height(), door.size(), door.tags(), door.entries(), door.connectionRules()));
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
        return new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), doors, markers, features, template.structureFile());
    }

    public void openDoors(Player player) {
        Menu menu = menu("da:doors", 54, "DungeonArchitect Doors");
        int slot = 0;
        for (DoorTemplate template : doorRegistry.visible()) {
            TemplateLoadStatus<DoorTemplate> status = doorRegistry.status(template.id()).orElse(null);
            button(menu, slot++, statusValid(status) ? Material.OAK_DOOR : Material.RED_CONCRETE, template.id(), loadStatusLore(status, "Size: " + template.size(), "Tags: " + String.join(",", template.tags()), "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size()), "Click to edit."), p -> openDoor(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openDoor(Player player, String doorId) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + doorId));
        TemplateLoadStatus<DoorTemplate> status = doorRegistry.status(template.id()).orElse(null);
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, doorId).orElse(null);
        var markers = activeEdit == null ? template.markers() : activeEdit.markers();
        var features = activeEdit == null ? template.featureSlots() : activeEdit.featureSlots();
        Menu menu = menu("da:door-template:" + doorId, 54, "Door: " + doorId);
        button(menu, 4, statusValid(status) ? Material.OAK_DOOR : Material.RED_CONCRETE, "Size: " + template.size(), loadStatusLore(status, "Captured door footprint."), p -> openDoor(p, doorId));
        button(menu, 10, template.gateway() == null ? Material.RED_CONCRETE : Material.LIME_CONCRETE, "Gateway: " + (template.gateway() == null ? "unset" : template.gateway().size() + " " + template.gateway().facing()), List.of("Set with /da door gateway.", "Right click to select in edit world."), p -> openDoor(p, doorId), p -> selectDoorComponent(p, doorId, "gateway", "gateway"));
        Set<String> currentDoorTags = activeEdit == null ? template.tags() : activeEdit.tags();
        button(menu, 12, Material.OAK_SIGN, "Tags: " + String.join(",", currentDoorTags), List.of("Click to select door tags."), p -> openDoorTemplateTags(p, template));
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
        button(menu, 36, Material.NAME_TAG, "Rename Door", List.of("Move this door template to a new id."), p -> promptRenameDoor(p, template.id()));
        button(menu, 38, Material.MAP, "Duplicate Door", List.of("Copy this door template to a new id."), p -> promptDuplicateDoor(p, template.id()));
        button(menu, 40, Material.RED_CONCRETE, "Delete Door", List.of("Permanently delete this door template."), p -> openDeleteDoorConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openDoors);
        open(player, menu);
    }

    private void openDoorComponents(Player player, String doorId, String type) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
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
            button(menu, 45, Material.HOPPER, "Multi-edit", List.of("Configure multiple feature slots at once."), p -> beginMultiEdit(p, MultiEditOwner.DOOR_TEMPLATE, doorId, MultiEditSlotType.FEATURE));
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), p -> openDoor(p, doorId));
        open(player, menu);
    }

    private void openDoorFeatureSlot(Player player, String doorId, String slotId) {
        DoorTemplate template = doorRegistry.getVisible(doorId)
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

    private void beginMultiEdit(Player player, MultiEditOwner owner, String ownerId, MultiEditSlotType slotType) {
        MultiEditSession session = new MultiEditSession(owner, ownerId, slotType);
        multiEdits.put(player.getUniqueId(), session);
        openMultiEditSlotSelection(player);
    }

    private void openMultiEditSlotSelection(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        Menu menu = menu("da:multi-select:" + session.ownerId + ":" + session.slotType, 54, "Multi-edit " + session.slotType.label());
        int slotIndex = 0;
        if (session.slotType == MultiEditSlotType.DOOR) {
            for (DoorSocket slot : currentDoorSlots(player, session)) {
                boolean selected = session.selectedSlotIds.contains(slot.id().toLowerCase(Locale.ROOT));
                button(menu, slotIndex++, selected ? Material.EMERALD_BLOCK : Material.IRON_DOOR, slot.id(), multiDoorSlotLore(player, slot, selected, session), p -> {
                    toggleMultiEditSlot(p, slot.id());
                    openMultiEditSlotSelection(p);
                });
                if (slotIndex >= 45) {
                    break;
                }
            }
        } else {
            for (RoomFeatureSlot slot : currentFeatureSlots(player, session)) {
                boolean selected = session.selectedSlotIds.contains(slot.id().toLowerCase(Locale.ROOT));
                button(menu, slotIndex++, selected ? Material.EMERALD_BLOCK : Material.CHEST, slot.id(), multiFeatureSlotLore(player, slot, selected, session), p -> {
                    toggleMultiEditSlot(p, slot.id());
                    openMultiEditSlotSelection(p);
                });
                if (slotIndex >= 45) {
                    break;
                }
            }
        }
        if (session.selectedSlotIds.isEmpty()) {
            button(menu, 53, Material.GRAY_CONCRETE, "Continue to config", List.of("Select at least one slot first."), p -> openMultiEditSlotSelection(p));
        } else {
            button(menu, 53, Material.EMERALD_BLOCK, "Continue to config", List.of("Selected slots: " + session.selectedSlotIds.size()), this::openMultiEditConfig);
        }
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        open(player, menu);
    }

    private void openMultiEditConfig(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        if (session.selectedSlotIds.isEmpty()) {
            openMultiEditSlotSelection(player);
            return;
        }
        if (session.slotType == MultiEditSlotType.DOOR) {
            openMultiEditDoorConfig(player, session);
        } else {
            openMultiEditFeatureConfig(player, session);
        }
    }

    private void openMultiEditDoorConfig(Player player, MultiEditSession session) {
        List<DoorSocket> selectedSlots = selectedDoorSlots(player, session);
        initializeMultiDoorRuleDraft(session, selectedSlots);
        Menu menu = menu("da:multi-config:" + session.ownerId + ":door", 54, "Multi-edit Doors");
        button(menu, 0, Material.NAME_TAG, "Tags: " + String.join(",", session.doorTagsDraft), List.of("Select door tags.", "Applies to every selected door."), p -> promptMultiDoorTags(p));
        button(menu, 1, session.doorConnectionRulesDraft.mustConnect() ? Material.TRIPWIRE_HOOK : Material.GRAY_CONCRETE, "Must Connect: " + session.doorConnectionRulesDraft.mustConnect(), List.of("Applies to every selected door."), p -> toggleMultiDoorMustConnect(p));
        button(menu, 2, Material.LIME_DYE, "Accept Tags: " + String.join(",", session.doorConnectionRulesDraft.allowedTags()), List.of("Select opposite-door tags.", "Applies to every selected door."), p -> promptMultiDoorAllowedTags(p));
        button(menu, 3, Material.RED_DYE, "Reject Tags: " + String.join(",", session.doorConnectionRulesDraft.deniedTags()), List.of("Select opposite-door tags.", "Applies to every selected door."), p -> promptMultiDoorDeniedTags(p));
        button(menu, 4, Material.OAK_DOOR, "Draft Door Entries", List.of("Selected slots: " + selectedSlots.size(), "Draft entries: " + session.doorDraft.size(), session.doorEntriesTouched ? "Entry changes apply to every selected slot." : "Entries remain unchanged unless edited."), p -> openMultiEditConfig(p));
        button(menu, 6, Material.LIME_BANNER, "Accept Room Tags: " + String.join(",", session.doorConnectionRulesDraft.allowedRoomTags()), List.of("Select opposite-room tags.", "Applies to every selected door."), p -> promptMultiDoorAllowedRoomTags(p));
        button(menu, 7, Material.RED_BANNER, "Reject Room Tags: " + String.join(",", session.doorConnectionRulesDraft.deniedRoomTags()), List.of("Select opposite-room tags.", "Applies to every selected door."), p -> promptMultiDoorDeniedRoomTags(p));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", multiDoorEntryLore(session, DoorSlotEntry.EMPTY, "Virtual door; leaves room blueprint unchanged."), p -> {
            toggleMultiDoorDraft(p, DoorSlotEntry.EMPTY, 1);
            openMultiEditConfig(p);
        }, null, p -> promptMultiDoorWeight(p, DoorSlotEntry.EMPTY));
        List<UnavailableCandidate> unavailable = new ArrayList<>();
        for (DoorTemplate door : doorRegistry.visible()) {
            TemplateLoadStatus<DoorTemplate> status = doorRegistry.status(door.id()).orElse(null);
            List<String> conflicts = SlotMultiEditMatcher.doorConflicts(selectedSlots, door, status);
            if (!conflicts.isEmpty()) {
                unavailable.add(new UnavailableCandidate(door.id(), conflicts, "Bounds: " + DiagnosticText.size(door.size())));
                continue;
            }
            button(menu, slot++, Material.OAK_DOOR, door.id(), multiDoorEntryLore(session, door.id(), "Bounds: " + DiagnosticText.size(door.size()), "Matches all selected slots."), p -> {
                toggleMultiDoorDraft(p, door.id(), 1);
                openMultiEditConfig(p);
            }, null, p -> promptMultiDoorWeight(p, door.id()));
            if (slot >= 45) {
                break;
            }
        }
        for (TemplateLoadStatus<DoorTemplate> status : doorRegistry.loadStatuses()) {
            if (!status.loadable()) {
                unavailable.add(new UnavailableCandidate(status.id(), status.errors(), "Template could not be loaded."));
            }
        }
        slot = addUnavailableMultiEntries(menu, slot, unavailable, "doors");
        button(menu, 45, Material.ARROW, "Back to slot selection", List.of("Keep the current draft."), this::openMultiEditSlotSelection);
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        button(menu, 53, Material.EMERALD_BLOCK, "Apply to selected slots", multiDoorApplyLore(session, selectedSlots.size()), this::applyMultiEdit);
        open(player, menu);
    }

    private void openMultiEditFeatureConfig(Player player, MultiEditSession session) {
        List<RoomFeatureSlot> selectedSlots = selectedFeatureSlots(player, session);
        Menu menu = menu("da:multi-config:" + session.ownerId + ":feature", 54, "Multi-edit Features");
        button(menu, 4, Material.HOPPER, "Draft Feature Entries", List.of("Selected slots: " + selectedSlots.size(), "Draft entries: " + session.featureDraft.size(), "Apply replaces every selected slot."), p -> openMultiEditConfig(p));
        int slot = 9;
        button(menu, slot++, Material.BARRIER, "empty", multiFeatureEntryLore(session, FeatureSlotEntry.EMPTY, "Virtual feature; pastes nothing."), p -> {
            toggleMultiFeatureDraft(p, FeatureSlotEntry.EMPTY, 1);
            openMultiEditConfig(p);
        }, null, p -> promptMultiFeatureWeight(p, FeatureSlotEntry.EMPTY));
        List<UnavailableCandidate> unavailable = new ArrayList<>();
        for (FeatureTemplate feature : featureRegistry.visible()) {
            TemplateLoadStatus<FeatureTemplate> status = featureRegistry.status(feature.id()).orElse(null);
            List<String> conflicts = SlotMultiEditMatcher.featureConflicts(selectedSlots, feature, status);
            if (!conflicts.isEmpty()) {
                unavailable.add(new UnavailableCandidate(feature.id(), conflicts, "Size: " + DiagnosticText.size(feature.size())));
                continue;
            }
            button(menu, slot++, Material.STRUCTURE_BLOCK, feature.id(), multiFeatureEntryLore(session, feature.id(), "Size: " + DiagnosticText.size(feature.size()), "Matches all selected slots."), p -> {
                toggleMultiFeatureDraft(p, feature.id(), 1);
                openMultiEditConfig(p);
            }, null, p -> promptMultiFeatureWeight(p, feature.id()));
            if (slot >= 45) {
                break;
            }
        }
        for (TemplateLoadStatus<FeatureTemplate> status : featureRegistry.loadStatuses()) {
            if (!status.loadable()) {
                unavailable.add(new UnavailableCandidate(status.id(), status.errors(), "Template could not be loaded."));
            }
        }
        slot = addUnavailableMultiEntries(menu, slot, unavailable, "features");
        button(menu, 45, Material.ARROW, "Back to slot selection", List.of("Keep the current draft."), this::openMultiEditSlotSelection);
        button(menu, 49, Material.BARRIER, "Cancel", List.of("Discard this multi-edit session."), this::cancelMultiEdit);
        button(menu, 53, Material.EMERALD_BLOCK, "Apply to selected slots", List.of("Replaces entries on " + selectedSlots.size() + " slot(s).", "Draft entries: " + session.featureDraft.size()), this::applyMultiEdit);
        open(player, menu);
    }

    private int addUnavailableMultiEntries(Menu menu, int slot, List<UnavailableCandidate> unavailable, String label) {
        if (slot >= 45 || unavailable.isEmpty()) {
            return slot;
        }
        button(menu, slot++, Material.GRAY_STAINED_GLASS_PANE, "Unavailable", List.of("These " + label + " do not match every selected slot."), p -> {});
        for (UnavailableCandidate candidate : unavailable) {
            if (slot >= 45) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add("Unavailable for selected slots.");
            lore.add(candidate.summary);
            candidate.conflicts.stream().limit(4).forEach(lore::add);
            button(menu, slot++, Material.GRAY_CONCRETE, candidate.id, lore, p -> {
                p.sendMessage(Component.text(candidate.id + " conflicts:"));
                for (String conflict : candidate.conflicts) {
                    p.sendMessage(Component.text("- " + conflict));
                }
            });
        }
        return slot;
    }

    private void applyMultiEdit(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        if (session.selectedSlotIds.isEmpty()) {
            openMultiEditSlotSelection(player);
            return;
        }
        if (session.slotType == MultiEditSlotType.DOOR) {
            applyMultiDoorEdit(player, session);
        } else if (session.owner == MultiEditOwner.ROOM) {
            applyMultiRoomFeatureEdit(player, session);
        } else {
            applyMultiDoorFeatureEdit(player, session);
        }
        multiEdits.remove(player.getUniqueId());
    }

    private void applyMultiDoorEdit(Player player, MultiEditSession session) {
        RoomTemplate template = templateRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + session.ownerId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        if (activeEdit != null) {
            for (DoorSocket slot : activeEdit.doors()) {
                if (selected(session, slot.id())) {
                    activeEdit.removeDoor(slot.id());
                    activeEdit.addDoorSlot(applyMultiDoorDraft(slot, session));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door slots in the active edit session."));
            openComponents(player, template.id(), "door");
            return;
        }
        List<DoorSocket> slots = template.doors().stream()
            .map(slot -> selected(session, slot.id()) ? applyMultiDoorDraft(slot, session) : slot)
            .toList();
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), slots, template.markers(), template.featureSlots(), template.structureFile()));
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door slots."));
        openComponents(player, template.id(), "door");
    }

    private void applyMultiRoomFeatureEdit(Player player, MultiEditSession session) {
        RoomTemplate template = templateRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown room " + session.ownerId));
        AuthoringSession activeEdit = authoringManager.editingSession(player, template.id()).orElse(null);
        List<FeatureSlotEntry> entries = List.copyOf(session.featureDraft);
        if (activeEdit != null) {
            for (RoomFeatureSlot slot : activeEdit.featureSlots()) {
                if (selected(session, slot.id())) {
                    activeEdit.removeFeature(slot.id());
                    activeEdit.addFeatureSlot(slot.withEntries(entries));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " feature slots in the active edit session."));
            openComponents(player, template.id(), "feature");
            return;
        }
        List<RoomFeatureSlot> slots = template.featureSlots().stream()
            .map(slot -> selected(session, slot.id()) ? slot.withEntries(entries) : slot)
            .toList();
        saveRoom(new RoomTemplate(template.id(), template.category(), template.weight(), template.minimumConnections(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), slots, template.structureFile()));
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " feature slots."));
        openComponents(player, template.id(), "feature");
    }

    private void applyMultiDoorFeatureEdit(Player player, MultiEditSession session) {
        DoorTemplate template = doorRegistry.getVisible(session.ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown door " + session.ownerId));
        AuthoringSession activeEdit = authoringManager.editingDoorSession(player, template.id()).orElse(null);
        List<FeatureSlotEntry> entries = List.copyOf(session.featureDraft);
        if (activeEdit != null) {
            for (RoomFeatureSlot slot : activeEdit.featureSlots()) {
                if (selected(session, slot.id())) {
                    activeEdit.removeFeature(slot.id());
                    activeEdit.addFeatureSlot(slot.withEntries(entries));
                }
            }
            player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door feature slots in the active edit session."));
            openDoorComponents(player, template.id(), "feature");
            return;
        }
        List<RoomFeatureSlot> slots = template.featureSlots().stream()
            .map(slot -> selected(session, slot.id()) ? slot.withEntries(entries) : slot)
            .toList();
        saveDoorTemplate(new DoorTemplate(template.id(), template.size(), template.tags(), template.markers(), slots, template.gateway(), template.structureFile()));
        player.sendMessage(Component.text("Updated " + session.selectedSlotIds.size() + " door feature slots."));
        openDoorComponents(player, template.id(), "feature");
    }

    private void cancelMultiEdit(Player player) {
        MultiEditSession session = multiEdits.remove(player.getUniqueId());
        if (session == null) {
            openMain(player);
            return;
        }
        if (session.owner == MultiEditOwner.ROOM) {
            openComponents(player, session.ownerId, session.slotType == MultiEditSlotType.DOOR ? "door" : "feature");
        } else {
            openDoorComponents(player, session.ownerId, "feature");
        }
    }

    private void toggleMultiEditSlot(Player player, String slotId) {
        MultiEditSession session = requireMultiEdit(player);
        String normalized = slotId.toLowerCase(Locale.ROOT);
        if (!session.selectedSlotIds.remove(normalized)) {
            session.selectedSlotIds.add(normalized);
        }
    }

    private void toggleMultiDoorDraft(Player player, String doorId, int defaultWeight) {
        MultiEditSession session = requireMultiEdit(player);
        session.doorEntriesTouched = true;
        boolean removed = session.doorDraft.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
        if (!removed) {
            session.doorDraft.add(new DoorSlotEntry(doorId, defaultWeight));
        }
    }

    private void toggleMultiFeatureDraft(Player player, String featureId, int defaultWeight) {
        MultiEditSession session = requireMultiEdit(player);
        boolean removed = session.featureDraft.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
        if (!removed) {
            session.featureDraft.add(new FeatureSlotEntry(featureId, defaultWeight));
        }
    }

    private void promptMultiDoorWeight(Player player, String doorId) {
        prompts.prompt(player, "Enter weight for " + doorId, value -> {
            MultiEditSession session = requireMultiEdit(player);
            session.doorEntriesTouched = true;
            int weight = Integer.parseInt(value);
            session.doorDraft.removeIf(entry -> entry.doorId().equalsIgnoreCase(doorId));
            session.doorDraft.add(new DoorSlotEntry(doorId, weight));
            openMultiEditConfig(player);
        });
    }

    private void initializeMultiDoorRuleDraft(MultiEditSession session, List<DoorSocket> selectedSlots) {
        if (session.doorRuleDraftInitialized || selectedSlots.isEmpty()) {
            return;
        }
        DoorSocket source = selectedSlots.getFirst();
        session.doorTagsDraft.addAll(source.tags());
        session.doorConnectionRulesDraft = source.connectionRules();
        session.doorRuleDraftInitialized = true;
    }

    private void promptMultiDoorTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Door Slot Tags", session.doorTagsDraft, tags -> {
            session.doorTagsDraft.clear();
            session.doorTagsDraft.addAll(tags);
            session.doorTagsTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void toggleMultiDoorMustConnect(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        DoorConnectionRules rules = session.doorConnectionRulesDraft;
        session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), !rules.mustConnect());
        session.doorConnectionRulesTouched = true;
        openMultiEditConfig(player);
    }

    private void promptMultiDoorAllowedTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Accept Door Tags", session.doorConnectionRulesDraft.allowedTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(tags, rules.deniedTags(), rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorDeniedTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.DOOR, "Reject Door Tags", session.doorConnectionRulesDraft.deniedTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), tags, rules.allowedRoomTags(), rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorAllowedRoomTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.ROOM, "Accept Room Tags", session.doorConnectionRulesDraft.allowedRoomTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), tags, rules.deniedRoomTags(), rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private void promptMultiDoorDeniedRoomTags(Player player) {
        MultiEditSession session = requireMultiEdit(player);
        openTagSelector(player, TagDomain.ROOM, "Reject Room Tags", session.doorConnectionRulesDraft.deniedRoomTags(), tags -> {
            DoorConnectionRules rules = session.doorConnectionRulesDraft;
            session.doorConnectionRulesDraft = new DoorConnectionRules(rules.allowedTags(), rules.deniedTags(), rules.allowedRoomTags(), tags, rules.mustConnect());
            session.doorConnectionRulesTouched = true;
            openMultiEditConfig(player);
        }, this::openMultiEditConfig);
    }

    private DoorSocket applyMultiDoorDraft(DoorSocket slot, MultiEditSession session) {
        DoorSocket updated = slot;
        if (session.doorEntriesTouched) {
            updated = updated.withEntries(session.doorDraft);
        }
        if (session.doorTagsTouched) {
            updated = updated.withTags(session.doorTagsDraft);
        }
        if (session.doorConnectionRulesTouched) {
            updated = updated.withConnectionRules(session.doorConnectionRulesDraft);
        }
        return updated;
    }

    private List<String> multiDoorApplyLore(MultiEditSession session, int selectedCount) {
        List<String> lore = new ArrayList<>();
        lore.add("Selected slots: " + selectedCount);
        lore.add("Entries: " + (session.doorEntriesTouched ? "updated" : "unchanged"));
        lore.add("Tags: " + (session.doorTagsTouched ? "updated" : "unchanged"));
        lore.add("Connection rules: " + (session.doorConnectionRulesTouched ? "updated" : "unchanged"));
        return lore;
    }

    private void promptMultiFeatureWeight(Player player, String featureId) {
        prompts.prompt(player, "Enter weight for " + featureId, value -> {
            MultiEditSession session = requireMultiEdit(player);
            int weight = Integer.parseInt(value);
            session.featureDraft.removeIf(entry -> entry.featureId().equalsIgnoreCase(featureId));
            session.featureDraft.add(new FeatureSlotEntry(featureId, weight));
            openMultiEditConfig(player);
        });
    }

    private List<String> multiDoorEntryLore(MultiEditSession session, String doorId, String... extra) {
        List<String> lore = new ArrayList<>();
        session.doorDraft.stream()
            .filter(entry -> entry.doorId().equalsIgnoreCase(doorId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits shared weight.");
        return lore;
    }

    private List<String> multiFeatureEntryLore(MultiEditSession session, String featureId, String... extra) {
        List<String> lore = new ArrayList<>();
        session.featureDraft.stream()
            .filter(entry -> entry.featureId().equalsIgnoreCase(featureId))
            .findFirst()
            .ifPresentOrElse(entry -> lore.add("Selected weight: " + entry.weight()), () -> lore.add("Not selected"));
        lore.addAll(List.of(extra));
        lore.add("Left click toggles weight 1.");
        lore.add("Shift-left edits shared weight.");
        return lore;
    }

    private List<String> multiDoorSlotLore(Player player, DoorSocket slot, boolean selected, MultiEditSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "Selected" : "Not selected");
        lore.add("Size: " + DiagnosticText.size(slot.size()));
        lore.add("Facing: " + slot.facing());
        lore.add("Current entries: " + slot.entries().size());
        if (mixedDoorEntries(slot, currentDoorSlots(player, session))) {
            lore.add("Config differs from other slots.");
        }
        return lore;
    }

    private List<String> multiFeatureSlotLore(Player player, RoomFeatureSlot slot, boolean selected, MultiEditSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "Selected" : "Not selected");
        lore.add("Size: " + DiagnosticText.size(slot.size()));
        lore.add("Current entries: " + slot.entries().size());
        if (mixedFeatureEntries(slot, currentFeatureSlots(player, session))) {
            lore.add("Config differs from other slots.");
        }
        return lore;
    }

    private boolean mixedDoorEntries(DoorSocket slot, List<DoorSocket> slots) {
        return slots.stream().anyMatch(other -> !other.id().equalsIgnoreCase(slot.id()) && !other.entries().equals(slot.entries()));
    }

    private boolean mixedFeatureEntries(RoomFeatureSlot slot, List<RoomFeatureSlot> slots) {
        return slots.stream().anyMatch(other -> !other.id().equalsIgnoreCase(slot.id()) && !other.entries().equals(slot.entries()));
    }

    private List<DoorSocket> currentDoorSlots(Player player, MultiEditSession session) {
        if (session.owner != MultiEditOwner.ROOM) {
            return List.of();
        }
        if (player != null) {
            AuthoringSession activeEdit = authoringManager.editingSession(player, session.ownerId).orElse(null);
            if (activeEdit != null) {
                return activeEdit.doors();
            }
        }
        return templateRegistry.getVisible(session.ownerId).map(RoomTemplate::doors).orElse(List.of());
    }

    private List<RoomFeatureSlot> currentFeatureSlots(Player player, MultiEditSession session) {
        if (session.owner == MultiEditOwner.ROOM) {
            if (player != null) {
                AuthoringSession activeEdit = authoringManager.editingSession(player, session.ownerId).orElse(null);
                if (activeEdit != null) {
                    return activeEdit.featureSlots();
                }
            }
            return templateRegistry.getVisible(session.ownerId).map(RoomTemplate::featureSlots).orElse(List.of());
        }
        if (player != null) {
            AuthoringSession activeEdit = authoringManager.editingDoorSession(player, session.ownerId).orElse(null);
            if (activeEdit != null) {
                return activeEdit.featureSlots();
            }
        }
        return doorRegistry.getVisible(session.ownerId).map(DoorTemplate::featureSlots).orElse(List.of());
    }

    private List<DoorSocket> selectedDoorSlots(Player player, MultiEditSession session) {
        return currentDoorSlots(player, session).stream().filter(slot -> selected(session, slot.id())).toList();
    }

    private List<RoomFeatureSlot> selectedFeatureSlots(Player player, MultiEditSession session) {
        return currentFeatureSlots(player, session).stream().filter(slot -> selected(session, slot.id())).toList();
    }

    private boolean selected(MultiEditSession session, String slotId) {
        return session.selectedSlotIds.contains(slotId.toLowerCase(Locale.ROOT));
    }

    private MultiEditSession requireMultiEdit(Player player) {
        MultiEditSession session = multiEdits.get(player.getUniqueId());
        if (session == null) {
            throw new IllegalArgumentException("No active multi-edit session");
        }
        return session;
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
                DoorTemplate template = doorRegistry.getVisible(doorId)
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
                    DoorTemplate template = doorRegistry.getVisible(doorId)
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
                var renamed = assetRenameCoordinator.renameRoom(roomId, value.trim());
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
                var renamed = assetRenameCoordinator.renameFeature(featureId, value.trim());
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

    private void promptRenameDoor(Player player, String doorId) {
        prompts.prompt(player, "Enter new door id", value -> {
            try {
                var renamed = assetRenameCoordinator.renameDoor(doorId, value.trim());
                authoringManager.renameActiveDoorId(player, doorId, renamed.id());
                player.sendMessage(Component.text("Renamed door " + doorId + " to " + renamed.id() + "."));
                openDoor(player, renamed.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Rename failed: " + ex.getMessage()));
                openDoor(player, doorId);
            }
        });
    }

    private void promptDuplicateDoor(Player player, String doorId) {
        prompts.prompt(player, "Enter duplicate door id", value -> {
            try {
                var duplicated = doorRegistry.duplicateDoor(doorId, value.trim());
                templateRegistry.reload();
                player.sendMessage(Component.text("Duplicated door " + doorId + " to " + duplicated.id() + "."));
                openDoor(player, duplicated.id());
            } catch (Exception ex) {
                player.sendMessage(Component.text("Duplicate failed: " + ex.getMessage()));
                openDoor(player, doorId);
            }
        });
    }

    private void openDeleteRoomConfirm(Player player, String roomId) {
        Menu menu = menu("da:delete-room:" + roomId, 27, "Delete Room?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + roomId), p -> {
            try {
                templateRegistry.deleteRoom(roomId);
                assetRenameCoordinator.reloadAll();
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
        for (FeatureTemplate template : featureRegistry.visible()) {
            TemplateLoadStatus<FeatureTemplate> status = featureRegistry.status(template.id()).orElse(null);
            button(menu, slot++, statusValid(status) ? Material.STRUCTURE_BLOCK : Material.RED_CONCRETE, template.id(), loadStatusLore(status, "Size: " + template.size(), "Tags: " + String.join(",", template.tags()), "Click to edit."), p -> openFeature(p, template.id()));
            if (slot >= 45) {
                break;
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openFeature(Player player, String featureId) {
        FeatureTemplate template = featureRegistry.getVisible(featureId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown feature " + featureId));
        TemplateLoadStatus<FeatureTemplate> status = featureRegistry.status(template.id()).orElse(null);
        Menu menu = menu("da:feature-template:" + featureId, 54, "Feature: " + featureId);
        button(menu, 4, statusValid(status) ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "Size: " + template.size(), loadStatusLore(status, "Captured feature footprint."), p -> openFeature(p, featureId));
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
                assetRenameCoordinator.reloadAll();
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

    private void openDeleteDoorConfirm(Player player, String doorId) {
        Menu menu = menu("da:delete-door:" + doorId, 27, "Delete Door?");
        button(menu, 11, Material.RED_CONCRETE, "Confirm Delete", List.of("Permanently deletes " + doorId), p -> {
            try {
                doorRegistry.deleteDoor(doorId);
                assetRenameCoordinator.reloadAll();
                p.sendMessage(Component.text("Deleted door " + doorId));
                openDoors(p);
            } catch (IOException ex) {
                p.sendMessage(Component.text("Delete failed: " + ex.getMessage()));
                openDoor(p, doorId);
            }
        });
        button(menu, 15, Material.GRAY_CONCRETE, "Cancel", List.of(), p -> openDoor(p, doorId));
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
        for (String repair : result.repairs()) {
            player.sendMessage(Component.text("Repair: " + repair));
        }
        if (result.valid() && result.warnings().isEmpty()) {
            player.sendMessage(Component.text("Validation OK."));
            return;
        }
        if (!result.errors().isEmpty()) {
            player.sendMessage(Component.text("Validation errors:"));
        }
        for (String error : result.errors()) {
            player.sendMessage(Component.text("- " + error));
        }
        for (String warning : result.warnings()) {
            player.sendMessage(Component.text("Warning: " + warning));
        }
    }

    private boolean statusValid(TemplateLoadStatus<?> status) {
        return status == null || status.valid();
    }

    private List<String> loadStatusLore(TemplateLoadStatus<?> status, String... base) {
        List<String> lore = new ArrayList<>(List.of(base));
        if (status == null) {
            return lore;
        }
        if (!status.repairs().isEmpty()) {
            lore.add("Repairs: " + status.repairs().size());
        }
        if (!status.errors().isEmpty()) {
            lore.add("Invalid: " + status.errors().size() + " issue(s)");
            status.errors().stream().limit(2).forEach(error -> lore.add("- " + error));
            lore.add("Click to inspect. Use /da diagnose for full details.");
        }
        return DiagnosticText.lore(lore);
    }

    private List<String> diagnosticSummaryLore() {
        TemplateValidationResult result = TemplateDiagnostics.analyze(templateRegistry, featureRegistry, doorRegistry);
        if (result.valid() && result.warnings().isEmpty()) {
            return List.of("No known template issues.");
        }
        return List.of("Errors: " + result.errors().size(), "Warnings: " + result.warnings().size(), "Click for full report.");
    }

    private List<String> diagnosticLore(TemplateDiagnostic diagnostic) {
        List<String> lore = new ArrayList<>();
        lore.add(diagnostic.message());
        if (diagnostic.localPosition() != null) {
            lore.add("Position: " + DiagnosticText.position(diagnostic.localPosition()));
        }
        if (diagnostic.suggestion() != null) {
            lore.add("Fix: " + diagnostic.suggestion());
        }
        return DiagnosticText.lore(lore);
    }

    private String targetName(TemplateDiagnostic diagnostic) {
        if (diagnostic.templateId() == null) {
            return "template";
        }
        if (diagnostic.componentId() == null) {
            return diagnostic.templateId();
        }
        return diagnostic.templateId() + "/" + diagnostic.componentId();
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

    private enum MultiEditOwner {
        ROOM,
        DOOR_TEMPLATE
    }

    private enum MultiEditSlotType {
        DOOR("Door Slots"),
        FEATURE("Feature Slots");

        private final String label;

        MultiEditSlotType(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private static final class MultiEditSession {
        private final MultiEditOwner owner;
        private final String ownerId;
        private final MultiEditSlotType slotType;
        private final Set<String> selectedSlotIds = new LinkedHashSet<>();
        private final List<DoorSlotEntry> doorDraft = new ArrayList<>();
        private final Set<String> doorTagsDraft = new LinkedHashSet<>();
        private final List<FeatureSlotEntry> featureDraft = new ArrayList<>();
        private DoorConnectionRules doorConnectionRulesDraft = DoorConnectionRules.DEFAULT;
        private boolean doorRuleDraftInitialized;
        private boolean doorEntriesTouched;
        private boolean doorTagsTouched;
        private boolean doorConnectionRulesTouched;

        private MultiEditSession(MultiEditOwner owner, String ownerId, MultiEditSlotType slotType) {
            this.owner = owner;
            this.ownerId = ownerId;
            this.slotType = slotType;
        }
    }

    private static final class TagSelection {
        private final TagDomain domain;
        private final String title;
        private final Set<String> selected = new LinkedHashSet<>();
        private final Consumer<Set<String>> onSave;
        private final Consumer<Player> onCancel;
        private String filter = "";
        private int page;

        private TagSelection(TagDomain domain, String title, Set<String> selected, Consumer<Set<String>> onSave, Consumer<Player> onCancel) {
            this.domain = domain;
            this.title = title;
            this.selected.addAll(selected);
            this.onSave = onSave;
            this.onCancel = onCancel;
        }
    }

    private record UnavailableCandidate(String id, List<String> conflicts, String summary) {
        private UnavailableCandidate {
            conflicts = List.copyOf(conflicts);
        }
    }
}
