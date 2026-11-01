package com.dungeonarchitect.minigame;

import java.util.*;
import com.dungeonarchitect.command.CommandSuggestions;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class MinigameCommand implements CommandExecutor, TabCompleter {
    private final MinigameService games;
    private final MinigameMenus menus;
    public MinigameCommand(MinigameService games, MinigameMenus menus) { this.games = games; this.menus = menus; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (command.getName().equalsIgnoreCase("dplay")) { player(sender, args); return true; }
            executeAdmin(sender, args);
        } catch (RuntimeException ex) { sender.sendMessage(Component.text("Minigames: " + ex.getMessage())); }
        return true;
    }
    public void executeAdmin(CommandSender sender, String[] args) {
        try { admin(sender, args); }
        catch (com.dungeonarchitect.runtime.SpawnAreaException ex) { sender.sendMessage(ex.guidance()); }
    }
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dungeonarchitect.admin")) throw new IllegalArgumentException("Admin permission required");
        if (args.length == 0) { if (sender instanceof Player player) menus.open(player); else help(sender); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawns" -> {
                need(args, 2, "spawns <roomId>");
                if (!(sender instanceof Player player)) throw new IllegalArgumentException("Open Spawn Areas in game");
                menus.spawnPoints(player, args[1]);
            }
            case "preset" -> preset(sender, args);
            case "create" -> { need(args, 2, "create <preset> [seed]"); MinigameRun run = games.createRun(args[1], args.length > 2 ? Long.parseLong(args[2]) : new Random().nextLong()); MinigameMenus.inspect(sender, run); }
            case "list" -> games.runs().forEach(run -> sender.sendMessage(Component.text(run.id() + " " + run.state() + " " + run.preset().id())));
            case "assign" -> { need(args, 4, "assign <run> <player> <team|0>"); games.assign(games.require(args[1]).id(), online(args[2]).getUniqueId(), Integer.parseInt(args[3])); sender.sendMessage(Component.text("Team updated.")); }
            case "balance" -> {
                need(args, 3, "balance <run> <teamCount> [player1,player2,...]"); MinigameRun run = games.require(args[1]);
                Collection<UUID> players = args.length > 3 ? MinigamePreset.ids(args[3]).stream().map(MinigameCommand::online).map(Player::getUniqueId).toList() : run.teams().keySet();
                games.balance(run.id(), players, Integer.parseInt(args[2])); MinigameMenus.inspect(sender, run);
            }
            case "kits" -> { need(args, 3, "kits <run> <kit1,kit2|none>"); games.allowedKits(games.require(args[1]).id(), MinigamePreset.ids(String.join(" ", Arrays.copyOfRange(args, 2, args.length)))); sender.sendMessage(Component.text("Run kit choices updated.")); }
            case "prepare" -> {
                need(args, 2, "prepare <run>"); games.prepare(games.require(args[1]).id()).whenComplete((run, error) -> sender.sendMessage(Component.text(error == null ? "Prepared " + run.id() + ". Use start to send players in." : "Preparation failed: " + error.getMessage())));
            }
            case "start" -> {
                need(args, 2, "start <run>"); MinigameRun run = games.require(args[1]);
                if (run.state() == RunState.DRAFT) games.prepare(run.id()).whenComplete((ready, error) -> {
                    if (error != null) sender.sendMessage(Component.text("Launch failed: " + error.getMessage()));
                });
                games.start(run.id()); sender.sendMessage(Component.text("Starting " + run.id()));
            }
            case "cancel", "end" -> { need(args, 2, "end <run>"); games.end(games.require(args[1]).id(), "Ended by " + sender.getName()); sender.sendMessage(Component.text("Run ended.")); }
            case "inspect" -> { need(args, 2, "inspect <run>"); MinigameMenus.inspect(sender, games.require(args[1])); }
            default -> help(sender);
        }
    }
    private void preset(CommandSender sender, String[] args) {
        need(args, 2, "preset <list|create|duplicate|set>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> sender.sendMessage(Component.text("Presets: " + String.join(", ", games.presets().all().stream().map(MinigamePreset::id).toList())));
            case "create" -> { need(args, 3, "preset create <id>"); games.presets().create(MinigamePreset.empty(args[2])); }
            case "duplicate" -> { need(args, 4, "preset duplicate <source> <newId>"); games.presets().create(games.presets().require(args[2]).copyAs(args[3])); }
            case "set" -> {
                need(args, 5, "preset set <id> <root|rooms|spawns|count|distance|ratio|kits> <value|none>");
                String field = args[3].toLowerCase(Locale.ROOT);
                if (!MinigamePreset.FIELDS.contains(field)) throw new IllegalArgumentException("Unknown preset field");
                games.presets().save(games.presets().require(args[2]).with(field, String.join(" ", Arrays.copyOfRange(args, 4, args.length))));
            }
            default -> throw new IllegalArgumentException("Use preset list, create, duplicate, or set");
        }
        sender.sendMessage(Component.text("Preset command complete."));
    }
    private void player(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("Players only");
        if (args.length == 0 || args[0].equalsIgnoreCase("kit")) {
            if (args.length > 1) games.chooseKit(player, args[1]); else menus.kits(player);
        } else if (args[0].equalsIgnoreCase("exit")) {
            if (!games.exit(player)) player.sendMessage(Component.text("You are not in a run."));
        } else player.sendMessage(Component.text("/dplay kit [id|random] | /dplay exit"));
    }
    private static Player online(String name) { Player player = Bukkit.getPlayerExact(name); if (player == null) throw new IllegalArgumentException("Player is not online: " + name); return player; }
    private static void need(String[] args, int count, String usage) { if (args.length < count) throw new IllegalArgumentException("Usage: /da minigame " + usage); }
    private static void help(CommandSender sender) { sender.sendMessage(Component.text("/da minigame preset|spawns|create|list|assign|balance|kits|prepare|start|inspect|cancel|end")); }
    private static final List<String> ACTIONS = List.of("preset", "spawns", "create", "list", "assign", "balance", "kits", "prepare", "start", "inspect", "cancel", "end");
    private static final List<String> PRESET_ACTIONS = List.of("list", "create", "duplicate", "set");
    private static final List<String> SEEDS = List.of("0", "1", "42");
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String current = args.length == 0 ? "" : args[args.length - 1];
        if (command != null && command.getName().equalsIgnoreCase("dplay")) {
            if (!sender.hasPermission("dungeonarchitect.play")) return List.of();
            if (args.length <= 1) return CommandSuggestions.matching(current, List.of("kit", "exit"));
            if (args.length == 2 && args[0].equalsIgnoreCase("kit") && sender instanceof Player player) {
                return games.getRun(player).map(run -> { List<String> kits = new ArrayList<>(run.kits()); if (!kits.isEmpty()) kits.add("random"); return CommandSuggestions.matching(current, kits); }).orElse(List.of());
            }
            return List.of();
        }
        if (!sender.hasPermission("dungeonarchitect.admin")) return List.of();
        if (args.length <= 1) return CommandSuggestions.matching(current, ACTIONS);
        String action = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = switch (action) {
            case "preset" -> presetSuggestions(args);
            case "spawns" -> args.length == 2 ? games.templateOptions().stream().map(RoomTemplate::id).toList() : List.of();
            case "create" -> args.length == 2 ? presetIds() : args.length == 3 ? SEEDS : List.of();
            case "assign" -> args.length == 2 ? runIds(false) : args.length == 3 ? onlineNames() : args.length == 4 ? List.of("0", "1", "2", "3", "4", "5", "6", "7", "8") : List.of();
            case "balance" -> args.length == 2 ? runIds(false) : args.length == 3 ? List.of("2", "3", "4", "5", "6", "7", "8") : args.length == 4 ? CommandSuggestions.list(current, onlineNames(), List.of()) : List.of();
            case "kits" -> args.length == 2 ? runIds(false) : listValues(args, 2, games.availableKits(), true);
            case "prepare", "start", "cancel", "end", "inspect" -> args.length == 2 ? runIds(action.equals("inspect")) : List.of();
            default -> List.of();
        };
        return CommandSuggestions.matching(current, choices);
    }
    private List<String> presetSuggestions(String[] args) {
        if (args.length == 2) return PRESET_ACTIONS;
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) return switch (action) {
            case "create" -> CommandSuggestions.newId("preset_1", presetIds());
            case "duplicate", "set" -> presetIds();
            default -> List.of();
        };
        if (action.equals("duplicate")) return args.length == 4 ? CommandSuggestions.newId("new_id", presetIds()) : List.of();
        if (!action.equals("set")) return List.of();
        if (args.length == 4) return MinigamePreset.FIELDS;
        if (args.length < 5) return List.of();
        String field = args[3].toLowerCase(Locale.ROOT);
        return switch (field) {
            case "root" -> args.length == 5 ? templateIds(RoomCategory.START) : List.of();
            case "spawns" -> listValues(args, 4, templateIds(RoomCategory.TEAM_SPAWN), true);
            case "rooms" -> listValues(args, 4, games.templateOptions().stream().filter(t -> t.category() != RoomCategory.START && t.category() != RoomCategory.TEAM_SPAWN).map(RoomTemplate::id).toList(), true);
            case "kits" -> listValues(args, 4, games.availableKits(), true);
            case "count" -> args.length == 5 ? List.of("16", "32", "48", "64") : List.of();
            case "distance" -> args.length == 5 ? List.of("1", "2", "4", "6", "8") : List.of();
            case "ratio" -> args.length == 5 ? List.of("1.0", "1.25", "1.5", "2.0") : List.of();
            default -> List.of();
        };
    }
    private List<String> templateIds(RoomCategory category) { return games.templateOptions().stream().filter(t -> t.category() == category).map(RoomTemplate::id).toList(); }
    private List<String> presetIds() { return games.presets().all().stream().map(MinigamePreset::id).toList(); }
    private List<String> runIds(boolean includeEnded) { return games.runs().stream().filter(r -> includeEnded || r.state() != RunState.ENDED).map(r -> r.id().toString()).toList(); }
    private static List<String> onlineNames() { return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(); }
    private static List<String> listValues(String[] args, int start, Collection<String> available, boolean allowNone) {
        if (args.length <= start) return List.of();
        String current = args[args.length - 1];
        if (allowNone && Arrays.stream(args, start, args.length - 1).flatMap(value -> Arrays.stream(value.split(","))).anyMatch(value -> value.equalsIgnoreCase("none"))) return List.of();
        int comma = current.lastIndexOf(',');
        if (allowNone && comma >= 0 && Arrays.stream(current.substring(0, comma).split(",")).anyMatch(value -> value.equalsIgnoreCase("none"))) return List.of();
        List<String> earlier = Arrays.stream(args, start, args.length - 1).flatMap(value -> MinigamePreset.ids(value).stream()).toList();
        List<String> choices = new ArrayList<>(available);
        if (allowNone && args.length == start + 1 && !current.contains(",")) choices.add("none");
        return CommandSuggestions.list(current, choices, earlier);
    }
}
