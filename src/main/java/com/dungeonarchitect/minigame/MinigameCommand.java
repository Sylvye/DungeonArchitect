package com.dungeonarchitect.minigame;

import java.util.*;
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
        if (!sender.hasPermission("dungeonarchitect.admin")) throw new IllegalArgumentException("Admin permission required");
        if (args.length == 0) { if (sender instanceof Player player) menus.open(player); else help(sender); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
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
                if (!Set.of("root", "rooms", "spawns", "count", "distance", "ratio", "kits").contains(args[3])) throw new IllegalArgumentException("Unknown preset field");
                games.presets().save(games.presets().require(args[2]).with(args[3], String.join(" ", Arrays.copyOfRange(args, 4, args.length))));
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
    private static void help(CommandSender sender) { sender.sendMessage(Component.text("/da minigame preset|create|list|assign|balance|kits|prepare|start|inspect|cancel|end")); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices;
        if (command.getName().equalsIgnoreCase("dplay")) {
            choices = args.length == 1 ? List.of("kit", "exit") : args.length == 2 && sender instanceof Player p ? games.getRun(p).map(r -> { var list = new ArrayList<>(r.kits()); list.add("random"); return (List<String>) list; }).orElse(List.of()) : List.of();
        } else if (!sender.hasPermission("dungeonarchitect.admin")) return List.of();
        else if (args.length == 1) choices = List.of("preset", "create", "list", "assign", "balance", "kits", "prepare", "start", "inspect", "cancel", "end");
        else if (args.length == 2 && args[0].equals("preset")) choices = List.of("list", "create", "duplicate", "set");
        else if (args.length == 2 && args[0].equals("create") || args.length == 3 && args[0].equals("preset")) choices = games.presets().all().stream().map(MinigamePreset::id).toList();
        else if (args.length == 2) choices = games.runs().stream().filter(r -> r.state() != RunState.ENDED).map(r -> r.id().toString()).toList();
        else choices = List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
