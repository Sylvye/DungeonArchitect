package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.gui.GuiItems;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
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

public final class MenuManager implements Listener {
    private final Plugin plugin;
    private final RoomTemplateRegistry templateRegistry;
    private final DungeonManager dungeonManager;
    private final ChatPromptManager prompts;
    private final Runnable reloadAll;
    private final Map<UUID, Map<Integer, MenuAction>> actions = new HashMap<>();
    private final RoomTemplateValidator validator = new RoomTemplateValidator();

    public MenuManager(Plugin plugin, RoomTemplateRegistry templateRegistry, DungeonManager dungeonManager, ChatPromptManager prompts, Runnable reloadAll) {
        this.plugin = plugin;
        this.templateRegistry = templateRegistry;
        this.dungeonManager = dungeonManager;
        this.prompts = prompts;
        this.reloadAll = reloadAll;
    }

    public void openMain(Player player) {
        Menu menu = menu("da:main", 27, "DungeonArchitect");
        button(menu, 10, Material.BOOKSHELF, "Rooms", List.of("Edit room metadata and validate templates."), this::openRooms);
        button(menu, 13, Material.COMPARATOR, "Config", List.of("Edit config.yml and feature-pools.yml."), this::openConfig);
        button(menu, 16, Material.ENDER_PEARL, "Dungeons", List.of("Manage active dungeon instances."), this::openDungeons);
        open(player, menu);
    }

    public void openRooms(Player player) {
        Menu menu = menu("da:rooms", 54, "DungeonArchitect Rooms");
        int slot = 0;
        for (RoomTemplate template : templateRegistry.all()) {
            int target = slot++;
            button(menu, target, Material.PAPER, template.id(), List.of("Category: " + template.category(), "Doors: " + template.doors().size(), "Click to edit."), p -> openRoom(p, template.id()));
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
        button(menu, 4, validation.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, validation.valid() ? "Validation OK" : "Validation Errors", validation.errors(), p -> openRoom(p, roomId));
        button(menu, 10, Material.NAME_TAG, "Category: " + template.category(), enumNames(RoomCategory.class), p -> prompts.prompt(p, "Enter room category", value -> {
            saveRoom(new RoomTemplate(template.id(), RoomCategory.valueOf(value.toUpperCase(Locale.ROOT)), template.weight(), template.tags(), template.size(), template.spawn(), template.doors(), template.markers(), template.featureSlots(), template.structureFile()));
            p.sendMessage(Component.text("Room category updated."));
            openRoom(p, roomId);
        }));
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
        button(menu, 28, Material.IRON_DOOR, "Doors: " + template.doors().size(), template.doors().stream().map(door -> door.id() + " " + door.socketType() + " " + door.facing()).toList(), p -> openRoom(p, roomId));
        button(menu, 30, Material.REDSTONE_TORCH, "Markers: " + template.markers().size(), template.markers().stream().map(marker -> marker.name() + " " + marker.type()).toList(), p -> openRoom(p, roomId));
        button(menu, 32, Material.CHEST, "Features: " + template.featureSlots().size(), template.featureSlots().stream().map(slot -> slot.featureName() + " at " + slot.position()).toList(), p -> openRoom(p, roomId));
        button(menu, 40, Material.RED_CONCRETE, "Delete Room", List.of("Permanently delete this room template."), p -> openDeleteRoomConfirm(p, template.id()));
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openRooms);
        open(player, menu);
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

    public void openConfig(Player player) {
        Menu menu = menu("da:config", 54, "DungeonArchitect Config");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        int slot = fillConfig(menu, config, new File(plugin.getDataFolder(), "config.yml"), "", 0);
        button(menu, Math.min(slot, 45), Material.CHEST, "Feature Pools", List.of("Edit feature-pools.yml entries."), this::openFeaturePools);
        button(menu, 49, Material.EMERALD, "Reload", List.of("Reload config, rooms, and feature pools."), p -> {
            reloadAll.run();
            p.sendMessage(Component.text("DungeonArchitect reloaded."));
            openConfig(p);
        });
        button(menu, 53, Material.ARROW, "Back", List.of(), this::openMain);
        open(player, menu);
    }

    public void openFeaturePools(Player player) {
        Menu menu = menu("da:features", 54, "Feature Pools");
        File file = new File(plugin.getDataFolder(), "feature-pools.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pools = yaml.getConfigurationSection("pools");
        int slot = 0;
        if (pools != null) {
            for (String poolId : pools.getKeys(false)) {
                button(menu, slot++, Material.CHEST, poolId, List.of("Click to edit entries."), p -> openFeaturePool(p, poolId));
                if (slot >= 45) {
                    break;
                }
            }
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openConfig);
        open(player, menu);
    }

    public void openFeaturePool(Player player, String poolId) {
        Menu menu = menu("da:feature:" + poolId, 54, "Feature Pool: " + poolId);
        File file = new File(plugin.getDataFolder(), "feature-pools.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<String, Object>> entries = featureEntries(yaml, poolId);
        int slot = 0;
        for (int i = 0; i < entries.size() && slot < 45; i++) {
            int index = i;
            Map<String, Object> entry = entries.get(i);
            button(menu, slot++, Material.PAPER, String.valueOf(entry.getOrDefault("id", "entry_" + i)), List.of("type=" + entry.getOrDefault("type", "EMPTY"), "weight=" + entry.getOrDefault("weight", 1), "material=" + entry.getOrDefault("material", "")), p -> openFeatureEntry(p, poolId, index));
        }
        button(menu, 48, Material.EMERALD, "Add Empty Entry", List.of("Creates a new weighted EMPTY entry."), p -> {
            entries.add(new java.util.LinkedHashMap<>(Map.of("id", "entry_" + (entries.size() + 1), "weight", 1, "type", "EMPTY")));
            yaml.set("pools." + poolId, entries);
            saveYaml(yaml, file);
            reloadAll.run();
            openFeaturePool(p, poolId);
        });
        button(menu, 49, Material.ARROW, "Back", List.of(), this::openFeaturePools);
        open(player, menu);
    }

    public void openFeatureEntry(Player player, String poolId, int index) {
        Menu menu = menu("da:feature-entry:" + poolId + ":" + index, 27, "Feature Entry");
        File file = new File(plugin.getDataFolder(), "feature-pools.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<String, Object>> entries = featureEntries(yaml, poolId);
        if (index < 0 || index >= entries.size()) {
            openFeaturePool(player, poolId);
            return;
        }
        Map<String, Object> entry = entries.get(index);
        button(menu, 10, Material.NAME_TAG, "ID: " + entry.getOrDefault("id", ""), List.of("Click to edit."), p -> editFeatureEntry(p, yaml, file, poolId, entries, index, "id"));
        button(menu, 12, Material.GOLD_NUGGET, "Weight: " + entry.getOrDefault("weight", 1), List.of("Click to edit."), p -> editFeatureEntry(p, yaml, file, poolId, entries, index, "weight"));
        button(menu, 14, Material.COMPARATOR, "Type: " + entry.getOrDefault("type", "EMPTY"), List.of("EMPTY or BLOCK"), p -> editFeatureEntry(p, yaml, file, poolId, entries, index, "type"));
        button(menu, 16, Material.STONE, "Material: " + entry.getOrDefault("material", ""), List.of("Only used for BLOCK entries."), p -> editFeatureEntry(p, yaml, file, poolId, entries, index, "material"));
        button(menu, 22, Material.ARROW, "Back", List.of(), p -> openFeaturePool(p, poolId));
        open(player, menu);
    }

    private void editFeatureEntry(Player player, YamlConfiguration yaml, File file, String poolId, List<Map<String, Object>> entries, int index, String key) {
        prompts.prompt(player, "Enter value for " + key, value -> {
            Object parsed = key.equals("weight") ? Integer.parseInt(value) : value.toUpperCase(Locale.ROOT);
            if (key.equals("id")) {
                parsed = value;
            }
            entries.get(index).put(key, parsed);
            yaml.set("pools." + poolId, entries);
            saveYaml(yaml, file);
            reloadAll.run();
            player.sendMessage(Component.text("Feature entry updated."));
            openFeatureEntry(player, poolId, index);
        });
    }

    private List<Map<String, Object>> featureEntries(YamlConfiguration yaml, String poolId) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object item : yaml.getList("pools." + poolId, List.of())) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                entries.add(copy);
            } else if (item instanceof ConfigurationSection section) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (String key : section.getKeys(false)) {
                    copy.put(key, section.get(key));
                }
                entries.add(copy);
            }
        }
        return entries;
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

    private void saveYaml(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Menu menu(String id, int size, String title) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(id), size, Component.text(title));
        return new Menu(inventory, new HashMap<>());
    }

    private void button(Menu menu, int slot, Material material, String name, List<String> lore, MenuAction action) {
        ItemStack item = GuiItems.item(material, name, lore);
        menu.inventory.setItem(slot, item);
        menu.actions.put(slot, action);
    }

    private void open(Player player, Menu menu) {
        actions.put(player.getUniqueId(), menu.actions);
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        MenuAction action = actions.getOrDefault(player.getUniqueId(), Map.of()).get(event.getRawSlot());
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

    private record Menu(Inventory inventory, Map<Integer, MenuAction> actions) {
    }
}
