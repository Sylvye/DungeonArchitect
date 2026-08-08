package com.dungeonarchitect.command;

import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.AuthoringSession;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.gui.MenuManager;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.runtime.DungeonRequest;
import com.dungeonarchitect.runtime.RoomInstance;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomTemplateValidator;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.TemplateValidationResult;
import com.dungeonarchitect.util.BukkitVectors;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DungeonArchitectCommand implements CommandExecutor, TabCompleter {
    private final String version;
    private final AuthoringManager authoringManager;
    private final RoomTemplateRegistry templateRegistry;
    private final DungeonManager dungeonManager;
    private final Supplier<List<String>> featureReload;
    private final Supplier<List<String>> featurePoolIds;
    private final MenuManager menuManager;
    private final RoomStructureService structureService;
    private final RoomTemplateValidator validator;

    public DungeonArchitectCommand(String version, AuthoringManager authoringManager, RoomTemplateRegistry templateRegistry, DungeonManager dungeonManager, Supplier<List<String>> featureReload, Supplier<List<String>> featurePoolIds, MenuManager menuManager, RoomStructureService structureService) {
        this.version = version;
        this.authoringManager = authoringManager;
        this.templateRegistry = templateRegistry;
        this.dungeonManager = dungeonManager;
        this.featureReload = featureReload;
        this.featurePoolIds = featurePoolIds;
        this.menuManager = menuManager;
        this.structureService = structureService;
        this.validator = new RoomTemplateValidator(structureService);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            if (args.length == 0) {
                menuManager.openMain(requirePlayer(sender));
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> help(sender);
                case "version" -> sender.sendMessage(Component.text("DungeonArchitect " + version));
                case "reload" -> reload(sender);
                case "wand" -> wand(sender);
                case "gui" -> menuManager.openMain(requirePlayer(sender));
                case "rooms" -> menuManager.openRooms(requirePlayer(sender));
                case "config" -> menuManager.openConfig(requirePlayer(sender));
                case "dungeons" -> menuManager.openDungeons(requirePlayer(sender));
                case "exit" -> exit(sender);
                case "room" -> room(sender, args);
                case "generate" -> generate(sender, args);
                case "list" -> list(sender);
                case "debug" -> debug(sender, args);
                case "teleport" -> teleport(sender, args);
                case "destroy" -> destroy(sender, args);
                default -> help(sender);
            }
        } catch (RuntimeException ex) {
            sender.sendMessage(Component.text("DungeonArchitect: " + ex.getMessage()));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("/da | /da help"));
        sender.sendMessage(Component.text("/da wand"));
        sender.sendMessage(Component.text("/da gui | rooms | config | dungeons"));
        sender.sendMessage(Component.text("/da room create <id> | bounds | door [id] [socket] [facing]"));
        sender.sendMessage(Component.text("/da room marker add <name> [type] | feature <name>"));
        sender.sendMessage(Component.text("/da room save [id] | inspect <id> | validate <id|all> | delete <id>"));
        sender.sendMessage(Component.text("/da reload | generate <roomCount> [seed] | list | debug instance [id|#n] | debug room"));
        sender.sendMessage(Component.text("/da teleport [instance|#n] <roomIndex> | destroy [instance|#n] | exit"));
    }

    private void reload(CommandSender sender) {
        TemplateValidationResult result = templateRegistry.reload();
        List<String> featureErrors = featureReload.get();
        sender.sendMessage(Component.text("Loaded " + templateRegistry.all().size() + " room templates."));
        sendValidation(sender, result);
        for (String error : featureErrors) {
            sender.sendMessage(Component.text("Feature pool error: " + error));
        }
    }

    private void wand(CommandSender sender) {
        Player player = requirePlayer(sender);
        player.getInventory().addItem(authoringManager.createWand());
        player.sendMessage(Component.text("DungeonArchitect wand added. Left click pos1, right click pos2."));
    }

    private void room(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /da room <create|bounds|pos1|pos2|door|marker|feature|save|validate>");
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                requireArgs(args, 3, "/da room create <id>");
                authoringManager.createSession(player, args[2]);
                player.sendMessage(Component.text("Authoring room " + args[2] + ". Use /da wand to select bounds."));
            }
            case "pos1" -> {
                authoringManager.setSelection(player, 1, player.getLocation());
                player.sendMessage(Component.text("pos1 set to your current block."));
            }
            case "pos2" -> {
                authoringManager.setSelection(player, 2, player.getLocation());
                player.sendMessage(Component.text("pos2 set to your current block."));
            }
            case "bounds" -> bounds(player);
            case "door" -> door(player, args);
            case "marker" -> marker(player, args);
            case "feature" -> feature(player, args);
            case "save" -> save(player, args);
            case "inspect" -> inspect(sender, args);
            case "validate" -> validate(sender, args);
            case "delete" -> deleteRoom(sender, args);
            default -> throw new IllegalArgumentException("Unknown room command " + args[1]);
        }
    }

    private void bounds(Player player) {
        AuthoringSession session = authoringManager.session(player);
        var bounds = authoringManager.saveCurrentSelectionAsRoomBounds(player);
        player.sendMessage(Component.text("Room " + session.roomId() + " bounds saved: " + bounds.describe()));
    }

    private void door(Player player, String[] args) {
        int offset = args.length >= 3 && args[2].equalsIgnoreCase("add") ? 3 : 2;
        String id = args.length > offset ? args[offset] : null;
        SocketType socketType = args.length > offset + 1 ? SocketType.valueOf(args[offset + 1].toUpperCase(Locale.ROOT)) : SocketType.STANDARD;
        Direction3 facing = args.length > offset + 2 ? Direction3.valueOf(args[offset + 2].toUpperCase(Locale.ROOT)) : BukkitVectors.direction(player.getFacing());
        var created = authoringManager.createDoorFromSelection(player, id, socketType, facing);
        player.sendMessage(Component.text("Added door " + created.id() + " " + socketType + " " + facing + " bounds=" + created.localBounds().describe() + " width=" + created.width() + " height=" + created.height()));
    }

    private void marker(Player player, String[] args) {
        requireArgs(args, 4, "/da room marker add <name> [type]");
        if (!args[2].equalsIgnoreCase("add")) {
            throw new IllegalArgumentException("Usage: /da room marker add <name> [type]");
        }
        var local = authoringManager.targetedLocalPosition(player)
            .orElseThrow(() -> new IllegalArgumentException("Look at a block inside selected bounds"));
        String type = args.length >= 5 ? args[4] : "generic";
        authoringManager.session(player).addMarker(args[3], type, local);
        player.sendMessage(Component.text("Added marker " + args[3] + " at " + local + "."));
    }

    private void feature(Player player, String[] args) {
        requireArgs(args, 3, "/da room feature <name> | /da room feature advanced <slotId> <poolId>");
        if (args[2].equalsIgnoreCase("advanced")) {
            requireArgs(args, 5, "/da room feature advanced <slotId> <poolId>");
            var local = authoringManager.targetedLocalPosition(player)
                .orElseThrow(() -> new IllegalArgumentException("Look at a block inside selected bounds"));
            Direction3 facing = BukkitVectors.direction(player.getFacing());
            authoringManager.session(player).addFeatureSlot(args[3], args[4], local, facing);
            player.sendMessage(Component.text("Added advanced feature " + args[3] + " using pool " + args[4] + " at " + local + "."));
            return;
        }
        String featureName = args[2].toLowerCase(Locale.ROOT);
        String poolId = featurePoolIds.get().stream()
            .filter(pool -> pool.equalsIgnoreCase(featureName))
            .findFirst()
            .orElse("default");
        var slot = authoringManager.createFeatureFromTarget(player, featureName, poolId);
        player.sendMessage(Component.text("Added feature " + slot.featureName() + " at " + slot.position() + "."));
    }

    private void save(Player player, String[] args) {
        String id = args.length >= 3 ? args[2] : null;
        try {
            TemplateValidationResult result = authoringManager.save(player, id);
            player.sendMessage(Component.text("Room saved."));
            sendValidation(player, result);
            templateRegistry.reload();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to save room: " + ex.getMessage(), ex);
        }
    }

    private void validate(CommandSender sender, String[] args) {
        requireArgs(args, 3, "/da room validate <id|all>");
        if (args[2].equalsIgnoreCase("all")) {
            sendValidation(sender, templateRegistry.reload());
            return;
        }
        RoomTemplate template = templateRegistry.get(args[2])
            .orElseThrow(() -> new IllegalArgumentException("Unknown room template " + args[2]));
        sender.sendMessage(Component.text("Room " + template.id() + " category=" + template.category() + " size=" + template.size() + " doors=" + template.doors().size()));
        sendValidation(sender, validator.validate(template));
    }

    private void inspect(CommandSender sender, String[] args) {
        requireArgs(args, 3, "/da room inspect <id>");
        RoomTemplate template = templateRegistry.get(args[2])
            .orElseThrow(() -> new IllegalArgumentException("Unknown room template " + args[2]));
        sender.sendMessage(Component.text("Room " + template.id()));
        sender.sendMessage(Component.text("category=" + template.category() + " weight=" + template.weight() + " tags=" + template.tags()));
        sender.sendMessage(Component.text("metadata size=" + template.size() + " structure=" + template.structureFile()));
        try {
            sender.sendMessage(Component.text("room.nbt size=" + structureService.loadSize(template.structureFile())));
        } catch (Exception ex) {
            sender.sendMessage(Component.text("room.nbt size=ERROR " + ex.getMessage()));
        }
        sender.sendMessage(Component.text("doors=" + template.doors().size() + " markers=" + template.markers().size() + " features=" + template.featureSlots().size()));
        template.doors().forEach(door -> sender.sendMessage(Component.text("door " + door.id() + " pos=" + door.position() + " facing=" + door.facing() + " socket=" + door.socketType() + " size=" + door.width() + "x" + door.height())));
    }

    private void deleteRoom(CommandSender sender, String[] args) {
        requireArgs(args, 3, "/da room delete <id>");
        try {
            templateRegistry.deleteRoom(args[2]);
            sender.sendMessage(Component.text("Deleted room " + args[2] + "."));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to delete room: " + ex.getMessage(), ex);
        }
    }

    private void generate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "/da generate <roomCount> [seed]");
        int roomCount = Integer.parseInt(args[1]);
        long seed = args.length >= 3 ? Long.parseLong(args[2]) : System.currentTimeMillis();
        DungeonInstance instance = dungeonManager.createDungeon(new DungeonRequest(roomCount, seed, Set.of(player.getUniqueId())));
        player.sendMessage(Component.text("Generated dungeon " + instance.id() + " seed=" + seed + " rooms=" + instance.rooms().size()));
    }

    private void exit(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (dungeonManager.getDungeon(player).isEmpty()) {
            throw new IllegalArgumentException("You are not in a dungeon");
        }
        dungeonManager.exitDungeon(player);
        player.sendMessage(Component.text("Exited dungeon."));
    }

    private void list(CommandSender sender) {
        if (dungeonManager.instances().isEmpty()) {
            sender.sendMessage(Component.text("No active dungeons."));
            return;
        }
        for (DungeonInstance instance : dungeonManager.instances()) {
            sender.sendMessage(Component.text("#" + dungeonManager.alias(instance).orElse(0) + " " + instance.id() + " state=" + instance.state() + " seed=" + instance.seed() + " rooms=" + instance.rooms().size() + " players=" + instance.playerIds().size()));
        }
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("instance")) {
            DungeonInstance instance = args.length >= 3 ? dungeon(args[2]) : currentDungeon(sender);
            sender.sendMessage(Component.text("Dungeon " + instance.id()));
            sender.sendMessage(Component.text("alias=#" + dungeonManager.alias(instance).orElse(0) + " state=" + instance.state() + " seed=" + instance.seed() + " world=" + instance.worldName()));
            sender.sendMessage(Component.text("rooms=" + instance.rooms().size() + " edges=" + instance.graph().edges().size()));
            for (var node : instance.graph().nodes()) {
                RoomTemplate template = templateRegistry.get(node.templateId()).orElse(null);
                String nbtSize = "unknown";
                if (template != null) {
                    try {
                        nbtSize = structureService.loadSize(template.structureFile()).toString();
                    } catch (Exception ex) {
                        nbtSize = "ERROR " + ex.getMessage();
                    }
                }
                sender.sendMessage(Component.text("node " + node.index() + " template=" + node.templateId() + " depth=" + node.depth() + " origin=" + node.transform().origin() + " pasteOrigin=" + com.dungeonarchitect.runtime.RoomStructurePlacer.pasteOrigin(node.transform()) + " rotation=" + node.transform().rotation() + " metadataSize=" + node.transform().templateSize() + " nbtSize=" + nbtSize + " bounds=" + node.transform().transformedBounds()));
            }
            for (var edge : instance.graph().edges()) {
                sender.sendMessage(Component.text("edge " + edge.fromNode() + ":" + edge.fromDoorId() + " -> " + edge.toNode() + ":" + edge.toDoorId()));
            }
            return;
        }
        if (args[1].equalsIgnoreCase("room")) {
            Player player = requirePlayer(sender);
            RoomInstance room = dungeonManager.getRoom(player).orElseThrow(() -> new IllegalArgumentException("You are not inside a dungeon room"));
            sender.sendMessage(Component.text("Room index=" + room.node().index() + " template=" + room.template().id() + " category=" + room.template().category()));
            sender.sendMessage(Component.text("state=" + room.state() + " depth=" + room.node().depth() + " bounds=" + room.bounds()));
            sender.sendMessage(Component.text("doors=" + room.template().doors().size() + " markers=" + room.template().markers().size() + " featureSlots=" + room.template().featureSlots().size()));
            return;
        }
        throw new IllegalArgumentException("Usage: /da debug instance [id] | /da debug room");
    }

    private void teleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "/da teleport [instance|#n] <roomIndex>");
        DungeonInstance instance;
        int index;
        if (args.length == 2) {
            instance = currentDungeon(sender);
            index = Integer.parseInt(args[1]);
        } else {
            instance = dungeon(args[1]);
            index = Integer.parseInt(args[2]);
        }
        if (index < 0 || index >= instance.rooms().size()) {
            throw new IllegalArgumentException("Room index out of range");
        }
        var room = instance.rooms().get(index);
        var origin = room.node().transform().origin();
        var world = org.bukkit.Bukkit.getWorld(instance.worldName());
        if (world == null) {
            throw new IllegalArgumentException("Dungeon world is not loaded");
        }
        player.teleport(new org.bukkit.Location(world, origin.x() + 0.5, origin.y() + 1, origin.z() + 0.5));
    }

    private void destroy(CommandSender sender, String[] args) {
        DungeonInstance instance = args.length >= 2 ? dungeon(args[1]) : currentDungeon(sender);
        dungeonManager.destroyDungeon(instance.id());
        sender.sendMessage(Component.text("Destroyed dungeon " + instance.id()));
    }

    private DungeonInstance currentDungeon(CommandSender sender) {
        return dungeonManager.getDungeon(requirePlayer(sender))
            .orElseThrow(() -> new IllegalArgumentException("You are not in a dungeon. Use an instance alias like #1 or open /da dungeons."));
    }

    private DungeonInstance dungeon(String idPrefix) {
        return dungeonManager.getDungeonByAliasOrId(idPrefix)
            .orElseThrow(() -> new IllegalArgumentException("Expected one matching dungeon for " + idPrefix));
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("This command requires a player");
        }
        return player;
    }

    private void requireArgs(String[] args, int length, String usage) {
        if (args.length < length) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private void sendValidation(CommandSender sender, TemplateValidationResult result) {
        if (result.valid()) {
            sender.sendMessage(Component.text("Validation OK."));
            return;
        }
        sender.sendMessage(Component.text("Validation errors:"));
        for (String error : result.errors()) {
            sender.sendMessage(Component.text("- " + error));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return prefix(args[0], List.of("help", "version", "reload", "wand", "gui", "rooms", "config", "dungeons", "exit", "room", "generate", "list", "debug", "teleport", "destroy"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("room")) {
            return prefix(args[1], List.of("create", "bounds", "pos1", "pos2", "door", "marker", "feature", "save", "inspect", "validate", "delete"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("save")) {
            return prefix(args[2], templateRegistry.all().stream().map(RoomTemplate::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("validate")) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            ids.addAll(templateRegistry.all().stream().map(RoomTemplate::id).toList());
            return prefix(args[2], ids);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("inspect")) {
            return prefix(args[2], templateRegistry.all().stream().map(RoomTemplate::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("delete")) {
            return prefix(args[2], templateRegistry.all().stream().map(RoomTemplate::id).toList());
        }
        if (args[0].equalsIgnoreCase("room") && args.length == 3 && args[1].equalsIgnoreCase("door")) {
            return prefix(args[2], List.of("add", "door_1"));
        }
        if (args[0].equalsIgnoreCase("room") && args.length == 4 && args[1].equalsIgnoreCase("door") && args[2].equalsIgnoreCase("add")) {
            return prefix(args[3], List.of("door_1"));
        }
        if (args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("door")) {
            int socketArg = args.length >= 4 && args[2].equalsIgnoreCase("add") ? 5 : 4;
            int facingArg = args.length >= 4 && args[2].equalsIgnoreCase("add") ? 6 : 5;
            if (args.length == socketArg) {
                return prefix(args[args.length - 1], enumOptions(SocketType.class));
            }
            if (args.length == facingArg) {
                return prefix(args[args.length - 1], enumOptions(Direction3.class));
            }
        }
        if (args[0].equalsIgnoreCase("room") && args.length == 3 && args[1].equalsIgnoreCase("marker")) {
            return prefix(args[2], List.of("add"));
        }
        if (args[0].equalsIgnoreCase("room") && args.length == 3 && args[1].equalsIgnoreCase("feature")) {
            List<String> features = new ArrayList<>(List.of("chest", "trap", "spawner", "empty", "advanced"));
            features.addAll(featurePoolIds.get());
            return prefix(args[2], features);
        }
        if (args[0].equalsIgnoreCase("room") && args.length == 5 && args[1].equalsIgnoreCase("feature") && args[2].equalsIgnoreCase("advanced")) {
            return prefix(args[4], featurePoolIds.get());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            return prefix(args[1], List.of("5", "6", "8", "10"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return prefix(args[1], List.of("instance", "room"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("instance")) {
            return prefix(args[2], instanceOptions());
        }
        if (args[0].equalsIgnoreCase("teleport")) {
            if (args.length == 2) {
                List<String> values = new ArrayList<>(instanceOptions());
                values.addAll(roomIndexes(currentDungeonOrNull(sender)));
                return prefix(args[1], values);
            }
            if (args.length == 3) {
                return prefix(args[2], roomIndexes(dungeonManager.getDungeonByAliasOrId(args[1]).orElse(null)));
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("destroy")) {
            return prefix(args[1], instanceOptions());
        }
        return List.of();
    }

    private DungeonInstance currentDungeonOrNull(CommandSender sender) {
        if (sender instanceof Player player) {
            return dungeonManager.getDungeon(player).orElse(null);
        }
        return null;
    }

    private List<String> instanceOptions() {
        List<String> options = new ArrayList<>(dungeonManager.instanceLabels());
        options.addAll(dungeonManager.instances().stream().map(instance -> instance.id().toString()).toList());
        return options;
    }

    private List<String> roomIndexes(DungeonInstance instance) {
        if (instance == null) {
            return List.of();
        }
        List<String> indexes = new ArrayList<>();
        for (int i = 0; i < instance.rooms().size(); i++) {
            indexes.add(String.valueOf(i));
        }
        return indexes;
    }

    private <E extends Enum<E>> List<String> enumOptions(Class<E> type) {
        List<String> options = new ArrayList<>();
        for (E value : type.getEnumConstants()) {
            options.add(value.name());
        }
        return options;
    }

    private List<String> prefix(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
