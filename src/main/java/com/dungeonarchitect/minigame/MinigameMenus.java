package com.dungeonarchitect.minigame;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.gui.ChatPromptManager;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;

/** Inventory-based authoring; every list is paginated and edits save immediately. */
public final class MinigameMenus implements Listener {
    private final Plugin plugin;
    private final MinigameService games;
    private final RoomTemplateRegistry templates;
    private final ChatPromptManager prompts;
    private final Consumer<Player> back;
    public MinigameMenus(Plugin plugin, MinigameService games, RoomTemplateRegistry templates, ChatPromptManager prompts, Consumer<Player> back) {
        this.plugin = plugin; this.games = games; this.templates = templates; this.prompts = prompts; this.back = back;
        games.kitMenu(this::kits);
    }
    static final class View implements InventoryHolder {
        final Inventory inventory;
        final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        final boolean playerMenu;
        View(String title, boolean playerMenu) { this.playerMenu = playerMenu; inventory = Bukkit.createInventory(this, 54, Component.text(title)); }
        @Override public Inventory getInventory() { return inventory; }
    }
    private View view(String title) { return new View(title, false); }
    private void button(View view, int slot, Material material, String title, List<String> lore, Consumer<Player> action) { button(view, slot, material, title, lore, (p, click) -> action.accept(p)); }
    private void button(View view, int slot, Material material, String title, List<String> lore, BiConsumer<Player, ClickType> action) {
        ItemStack item = new ItemStack(material); var meta = item.getItemMeta(); meta.displayName(Component.text(title)); meta.lore(lore.stream().map(Component::text).toList()); item.setItemMeta(meta);
        view.inventory.setItem(slot, item); view.actions.put(slot, action);
    }
    private void show(Player player, View view) { player.openInventory(view.inventory); }
    public void open(Player player) {
        if (!player.hasPermission("dungeonarchitect.admin")) throw new IllegalArgumentException("Admin permission required");
        View view = view("Minigames");
        button(view, 20, Material.WRITABLE_BOOK, "Presets", List.of("Create reusable dungeon and kit settings."), p -> presets(p, 0));
        button(view, 24, Material.ENDER_PEARL, "Runs", List.of("Assemble teams and manage launches."), p -> runs(p, 0));
        button(view, 49, Material.ARROW, "Back", List.of(), back); show(player, view);
    }
    private <T> void page(Player player, String title, List<T> entries, int page, BiConsumer<View, Integer> controls,
                          java.util.function.BiConsumer<ViewEntry<T>, Integer> render) {
        // List rendering is centralized below; controls receive the clamped page.
        int current = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / 45)));
        View view = view(title + " · " + (current + 1));
        for (int i = current * 45; i < Math.min(entries.size(), (current + 1) * 45); i++) render.accept(new ViewEntry<>(view, entries.get(i)), i % 45);
        controls.accept(view, current); show(player, view);
    }
    private record ViewEntry<T>(View view, T value) { }
    private void navigation(View view, int page, int count, BiConsumer<Player, Integer> open) {
        if (page > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> open.accept(p, page - 1));
        if ((page + 1) * 45 < count) button(view, 53, Material.ARROW, "Next", List.of(), p -> open.accept(p, page + 1));
    }
    private void presets(Player player, int number) {
        List<MinigamePreset> entries = List.copyOf(games.presets().all());
        page(player, "Minigame presets", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), this::presets);
            button(v, 48, Material.LIME_CONCRETE, "Create preset", List.of(), p -> prompts.prompt(p, "Preset id", id -> { games.presets().create(MinigamePreset.empty(id)); edit(p, id); }));
            button(v, 49, Material.ARROW, "Back", List.of(), this::open);
        }, (entry, slot) -> button(entry.view(), slot, Material.BOOK, entry.value().id(), List.of("Click to configure."), p -> edit(p, entry.value().id())));
    }
    private void edit(Player player, String id) {
        MinigamePreset preset = games.presets().require(id); View view = view("Preset: " + id);
        button(view, 10, Material.STRUCTURE_BLOCK, "Root: " + preset.rootId(), List.of("One START template; players spawn elsewhere."), p -> roomPool(p, id, "root", 0));
        button(view, 12, Material.BOOKSHELF, "Ordinary rooms: " + preset.ordinaryRooms().size(), preset.ordinaryRooms(), p -> roomPool(p, id, "rooms", 0));
        button(view, 14, Material.RESPAWN_ANCHOR, "Spawn pool: " + preset.spawnRooms().size(), List.of("One template is copied for every team.", "TEAM_SPAWN rooms need player_spawn markers."), p -> roomPool(p, id, "spawns", 0));
        button(view, 16, Material.IRON_SWORD, "Allowed kits: " + preset.kits().size(), preset.kits(), p -> allowedKits(p, preset.kits(), kits -> { games.presets().save(games.presets().require(id).with("kits", String.join(",", kits))); }, q -> edit(q, id), 0));
        number(view, 20, "Total rooms", Integer.toString(preset.roomCount()), "count", id);
        number(view, 22, "Minimum door distance", Integer.toString(preset.minimumDistance()), "distance", id);
        number(view, 24, "Maximum distance ratio", Double.toString(preset.maximumRatio()), "ratio", id);
        button(view, 38, Material.PAPER, "Duplicate", List.of(), p -> prompts.prompt(p, "New preset id", value -> { games.presets().create(games.presets().require(id).copyAs(value)); edit(p, value); }));
        button(view, 42, Material.LIME_CONCRETE, "Create run", List.of("Settings are copied into a new draft."), p -> run(p, games.createRun(id, new Random().nextLong())));
        button(view, 49, Material.ARROW, "Back", List.of(), p -> presets(p, 0)); show(player, view);
    }
    private void number(View view, int slot, String label, String value, String field, String id) {
        button(view, slot, Material.COMPARATOR, label + ": " + value, List.of("Click to edit."), p -> prompts.prompt(p, label, text -> { games.presets().save(games.presets().require(id).with(field, text)); edit(p, id); }));
    }
    private void roomPool(Player player, String id, String field, int number) {
        List<RoomTemplate> entries = templates.all().stream().filter(t -> switch (field) {
            case "root" -> t.category() == RoomCategory.START;
            case "spawns" -> t.category() == RoomCategory.TEAM_SPAWN;
            default -> t.category() != RoomCategory.START && t.category() != RoomCategory.TEAM_SPAWN;
        }).sorted(Comparator.comparing(RoomTemplate::id)).toList();
        MinigamePreset preset = games.presets().require(id);
        List<String> selected = field.equals("root") ? List.of(preset.rootId()) : field.equals("spawns") ? preset.spawnRooms() : preset.ordinaryRooms();
        page(player, "Select " + field, entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> roomPool(p, id, field, next));
            button(v, 49, Material.ARROW, "Done", List.of(), p -> edit(p, id));
        }, (entry, slot) -> {
            RoomTemplate t = entry.value();
            button(entry.view(), slot, selected.contains(t.id()) ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, t.id(), List.of("Click to toggle."), p -> {
                List<String> changed = new ArrayList<>(selected);
                if (!changed.remove(t.id())) changed.add(t.id());
                games.presets().save(games.presets().require(id).with(field, field.equals("root") ? t.id() : String.join(",", changed)));
                roomPool(p, id, field, number);
            });
        });
    }
    private void allowedKits(Player player, List<String> selected, Consumer<List<String>> save, Consumer<Player> done, int number) {
        Set<String> ids = new TreeSet<>(games.availableKits()); ids.addAll(selected);
        List<String> entries = List.copyOf(ids);
        page(player, "Allowed kits", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> allowedKits(p, selected, save, done, next));
            button(v, 48, Material.BARRIER, "No kits", List.of("Players start without gear."), p -> { save.accept(List.of()); done.accept(p); });
            button(v, 49, Material.ARROW, "Done", List.of(), done);
        }, (entry, slot) -> button(entry.view(), slot, selected.contains(entry.value()) ? Material.LIME_CONCRETE : Material.IRON_SWORD, entry.value(), List.of("Click to toggle."), p -> {
            List<String> changed = new ArrayList<>(selected); if (!changed.remove(entry.value())) changed.add(entry.value());
            save.accept(changed); allowedKits(p, changed, save, done, number);
        }));
    }
    private void runs(Player player, int number) {
        List<MinigameRun> entries = games.runs().stream().filter(r -> r.state() != RunState.ENDED).toList();
        page(player, "Minigame runs", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), this::runs);
            button(v, 48, Material.BOOK, "New run from preset", List.of(), p -> presets(p, 0));
            button(v, 49, Material.ARROW, "Back", List.of(), this::open);
        }, (entry, slot) -> button(entry.view(), slot, Material.ENDER_PEARL, shortId(entry.value()) + " · " + entry.value().preset().id(), List.of(entry.value().state().name(), "Players: " + entry.value().teams().size()), p -> run(p, entry.value())));
    }
    public void run(Player player, MinigameRun run) {
        View view = view("Run: " + shortId(run));
        button(view, 4, Material.PAPER, run.state().name(), List.of("Preset: " + run.preset().id(), "Seed: " + run.seed(), run.message()), p -> run(p, run));
        button(view, 10, Material.PLAYER_HEAD, "Players and teams: " + run.teams().size(), List.of("Select players and assign teams."), p -> roster(p, run, 0));
        button(view, 12, Material.COMPARATOR, "Randomly balance teams", List.of("Uses the selected roster."), p -> prompts.prompt(p, "Team count (2–8)", text -> { games.balance(run.id(), run.teams().keySet(), Integer.parseInt(text)); run(p, run); }));
        button(view, 14, Material.IRON_SWORD, "Run kit choices: " + run.kits().size(), run.kits(), p -> allowedKits(p, run.kits(), kits -> games.allowedKits(run.id(), kits), q -> run(q, run), 0));
        button(view, 16, Material.MAP, "Spread report", run.spread().map(s -> List.of("Shortest route: " + s.shortest() + " doors", "Longest route: " + s.longest() + " doors", "Team rooms: " + run.spawnRooms(), "Click for every pairwise distance in chat.")).orElse(List.of("Prepare the dungeon first.")), p -> { inspect(p, run); run(p, run); });
        button(view, 29, Material.STRUCTURE_BLOCK, "Prepare", List.of("Validate and build without moving players."), p -> {
            games.prepare(run.id()).whenComplete((ready, error) -> { p.sendMessage(Component.text(error == null ? "Dungeon prepared. Press Start when ready." : error.getMessage())); if (p.isOnline()) run(p, run); }); run(p, run);
        });
        button(view, 31, Material.LIME_CONCRETE, "Start", List.of("Prepare if needed, then send players in."), p -> {
            if (run.state() == RunState.DRAFT) games.prepare(run.id()).whenComplete((ready, error) -> {
                if (error != null) p.sendMessage(Component.text("Launch failed: " + error.getMessage()));
            });
            p.closeInventory(); games.start(run.id());
        });
        button(view, 33, Material.RED_CONCRETE, "Cancel / End", List.of("Restore players and clean up this dungeon."), p -> { games.end(run.id(), "Ended by " + p.getName()); run(p, run); });
        button(view, 49, Material.ARROW, "Back", List.of(), p -> runs(p, 0)); show(player, view);
    }
    private void roster(Player player, MinigameRun run, int number) {
        Set<UUID> ids = new LinkedHashSet<>(run.teams().keySet()); Bukkit.getOnlinePlayers().forEach(p -> ids.add(p.getUniqueId()));
        List<UUID> entries = List.copyOf(ids);
        page(player, "Players and teams", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> roster(p, run, next));
            button(v, 49, Material.ARROW, "Done", List.of(), p -> run(p, run));
        }, (entry, slot) -> {
            UUID id = entry.value(); int team = run.teams().getOrDefault(id, 0);
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString());
            button(entry.view(), slot, team == 0 ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, name, List.of(team == 0 ? "Not selected" : "Team " + team, "Left click: select / remove", "Right click: next team (1–8)"), (p, click) -> {
                games.assign(run.id(), id, click.isRightClick() ? team % 8 + 1 : team == 0 ? 1 : 0); roster(p, run, number);
            });
        });
    }
    public void kits(Player player) { kits(player, 0); }
    private void kits(Player player, int number) {
        MinigameRun run = games.getRun(player).orElseThrow(() -> new IllegalArgumentException("You are not in a run"));
        if (run.state() != RunState.PREPARING || !run.started || run.kits().size() < 2) { player.sendMessage(Component.text("Kit selection is not open.")); return; }
        View view = new View("Choose a kit", true);
        int page = Math.max(0, Math.min(number, (run.kits().size() - 1) / 45));
        for (int i = page * 45; i < Math.min(run.kits().size(), (page + 1) * 45); i++) {
            String id = run.kits().get(i); button(view, i % 45, Material.IRON_SWORD, id, List.of("Click to select."), p -> games.chooseKit(p, id));
        }
        if (page > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> kits(p, page - 1));
        if ((page + 1) * 45 < run.kits().size()) button(view, 53, Material.ARROW, "Next", List.of(), p -> kits(p, page + 1));
        button(view, 49, Material.ENDER_EYE, "Random", List.of("Choose any allowed kit at random."), p -> games.chooseKit(p, "random")); show(player, view);
    }
    public static void inspect(org.bukkit.command.CommandSender sender, MinigameRun run) {
        sender.sendMessage(Component.text(run.id() + " · " + run.state() + " · " + run.preset().id() + " · seed " + run.seed()));
        run.teams().forEach((id, team) -> sender.sendMessage(Component.text(Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()) + ": team " + team)));
        sender.sendMessage(Component.text("Team spawn rooms: " + run.spawnRooms()));
        run.spread().ifPresent(s -> sender.sendMessage(Component.text(s.summary())));
        if (!run.message().isBlank()) sender.sendMessage(Component.text(run.message()));
    }
    private static String shortId(MinigameRun run) { return run.id().toString().substring(0, 8); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !view.playerMenu && !player.hasPermission("dungeonarchitect.admin")) return;
        var action = view.actions.get(event.getRawSlot());
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            try { action.accept(player, event.getClick()); } catch (RuntimeException ex) { player.sendMessage(Component.text(ex.getMessage())); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true); }
}
