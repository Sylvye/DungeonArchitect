package com.dungeonarchitect.minigame;

import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.gui.ChatPromptManager;
import com.dungeonarchitect.gui.MenuManager;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/** GUI edits are disposable until explicitly saved or prepared. */
public final class MinigameMenus implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final MinigameService games;
    private final RoomTemplateRegistry templates;
    private final ChatPromptManager prompts;
    private final Consumer<Player> back;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private MenuManager markerMenus;

    public MinigameMenus(Plugin plugin, MinigameService games, RoomTemplateRegistry templates, ChatPromptManager prompts, Consumer<Player> back) {
        this.plugin = plugin; this.games = games; this.templates = templates; this.prompts = prompts; this.back = back;
        games.kitMenu(this::kits);
    }
    public void markerMenus(MenuManager menus) { markerMenus = menus; }
    private static final class Session {
        MinigamePreset preset, saved;
        MatchSetup match;
        MinigameRun run;
        int teamCount = 2;
        boolean prompting, spawnEditing, loading, naming;
        Session(MinigamePreset preset, boolean saved) { this.preset = preset; this.saved = saved ? preset : null; }
        boolean editable() { return !loading && (run == null || run.state() == RunState.DRAFT); }
        Map<UUID, Integer> teams() { return run == null ? match.teams() : run.teams(); }
        List<String> kits() { return run == null ? match.kits() : run.kits(); }
    }
    static final class View implements InventoryHolder {
        final Inventory inventory;
        final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        final boolean playerMenu;
        Session session;
        View(String title, boolean playerMenu) { this.playerMenu = playerMenu; inventory = Bukkit.createInventory(this, 54, Component.text(title)); }
        @Override public Inventory getInventory() { return inventory; }
    }
    private View view(String title) { return new View(title, false); }
    private void button(View view, int slot, Material material, String title, List<String> lore, Consumer<Player> action) { button(view, slot, material, title, lore, (p, click) -> action.accept(p)); }
    private void button(View view, int slot, Material material, String title, List<String> lore, BiConsumer<Player, ClickType> action) {
        ItemStack item = new ItemStack(material); var meta = item.getItemMeta(); meta.displayName(Component.text(title)); meta.lore(lore.stream().map(Component::text).toList()); item.setItemMeta(meta);
        view.inventory.setItem(slot, item); view.actions.put(slot, action);
    }
    private void show(Player player, View view) {
        view.session = view.playerMenu ? null : sessions.get(player.getUniqueId()); player.openInventory(view.inventory);
    }
    private void discard(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.spawnEditing && markerMenus != null) markerMenus.closeMarkerNavigation(player);
        if (session != null && session.prompting) prompts.discard(player.getUniqueId());
    }
    public void open(Player player) {
        if (!player.hasPermission("dungeonarchitect.admin")) throw new IllegalArgumentException("Admin permission required");
        discard(player); View view = view("Minigames");
        button(view, 20, Material.WRITABLE_BOOK, "Presets", List.of("Reusable settings are saved only with Save Preset."), p -> presets(p, 0));
        button(view, 24, Material.ENDER_PEARL, "Runs", List.of("Prepared and active matches. Unfinished setup is disposable."), p -> runs(p, 0));
        button(view, 49, Material.ARROW, "Back", List.of(), back); show(player, view);
    }
    private record ViewEntry<T>(View view, T value) { }
    private <T> void page(Player player, String title, List<T> entries, int page, BiConsumer<View, Integer> controls, BiConsumer<ViewEntry<T>, Integer> render) {
        int current = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / 45))); View view = view(title + " · " + (current + 1));
        for (int i = current * 45; i < Math.min(entries.size(), (current + 1) * 45); i++) render.accept(new ViewEntry<>(view, entries.get(i)), i % 45);
        controls.accept(view, current); show(player, view);
    }
    private void navigation(View view, int page, int count, BiConsumer<Player, Integer> open) {
        if (page > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> open.accept(p, page - 1));
        if ((page + 1) * 45 < count) button(view, 53, Material.ARROW, "Next", List.of(), p -> open.accept(p, page + 1));
    }
    private void prompt(Player player, Session session, String label, Consumer<String> save, Consumer<Player> resume) {
        session.prompting = true;
        prompts.prompt(player, label, text -> {
            if (sessions.get(player.getUniqueId()) != session) return;
            session.prompting = false;
            try { save.accept(text); } catch (RuntimeException ex) { player.sendMessage(Component.text(ex.getMessage())); }
            resume.accept(player);
        }, () -> { if (sessions.get(player.getUniqueId()) == session) { session.prompting = false; resume.accept(player); } });
    }
    private void presets(Player player, int number) {
        discard(player); List<MinigamePreset> entries = List.copyOf(games.presets().all());
        page(player, "Minigame presets", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), this::presets);
            button(v, 48, Material.LIME_CONCRETE, "New Preset", List.of("Temporary until you click Save Preset."), p -> {
                Session session = new Session(MinigamePreset.empty("untitled"), false); sessions.put(p.getUniqueId(), session);
                session.naming = true;
                prompt(p, session, "New preset id", id -> {
                    if (games.presets().all().stream().anyMatch(existing -> existing.id().equals(id))) throw new IllegalArgumentException("Preset already exists: " + id);
                    session.preset = MinigamePreset.empty(id); session.naming = false;
                }, q -> { if (session.naming) presets(q, number); else edit(q, session); });
            });
            button(v, 49, Material.ARROW, "Back", List.of(), this::open);
        }, (entry, slot) -> button(entry.view(), slot, Material.BOOK, entry.value().id(), List.of("Click to configure. Changes require Save Preset."), p -> {
            discard(p); Session session = new Session(entry.value(), true); sessions.put(p.getUniqueId(), session); edit(p, session);
        }));
    }
    private void edit(Player player, Session session) {
        MinigamePreset preset = session.preset; View view = view("Preset: " + preset.id());
        button(view, 10, Material.STRUCTURE_BLOCK, "Root: " + preset.rootId(), List.of("One START room grows the layout; teams enter their own spawn rooms."), p -> roomPool(p, session, "root", 0));
        button(view, 12, Material.BOOKSHELF, "Ordinary Rooms: " + preset.ordinaryRooms().size(), preset.ordinaryRooms(), p -> roomPool(p, session, "rooms", 0));
        button(view, 14, Material.RESPAWN_ANCHOR, "Spawn Rooms: " + preset.spawnRooms().size(), List.of("Select rooms and configure Player Spawn markers.", "Areas need enough safe floor positions for the team."), p -> roomPool(p, session, "spawns", 0));
        button(view, 16, Material.IRON_SWORD, "Allowed Kits: " + preset.kits().size(), preset.kits(), p -> allowedKits(p, preset.kits(), kits -> session.preset = session.preset.with("kits", String.join(",", kits)), q -> edit(q, session), 0));
        number(view, 20, "Total Rooms", Integer.toString(preset.roomCount()), "count", session);
        number(view, 22, "Minimum Door Distance", Integer.toString(preset.minimumDistance()), "distance", session);
        number(view, 24, "Maximum Distance Ratio", Double.toString(preset.maximumRatio()), "ratio", session);
        button(view, 30, Material.RESPAWN_ANCHOR, "Edit Spawn Areas", List.of("Choose a room to place, remove, or save player positions."), p -> spawnRooms(p, session, 0));
        button(view, 38, Material.PAPER, "Duplicate", List.of("Create a temporary copy; Save Preset keeps it."), p -> prompt(p, session, "New preset id", id -> {
            if (games.presets().all().stream().anyMatch(existing -> existing.id().equals(id))) throw new IllegalArgumentException("Preset already exists: " + id);
            session.preset = session.preset.copyAs(id); session.saved = null;
        }, q -> edit(q, session)));
        button(view, 40, Material.EMERALD_BLOCK, "Save Preset", List.of("Keep these reusable settings.", "Leaving discards changes you have not saved."), p -> {
            if (session.saved == null) games.presets().create(session.preset);
            else {
                if (!games.presets().require(session.saved.id()).equals(session.saved)) throw new IllegalStateException("Another editor changed this preset. Reopen it before saving.");
                games.presets().save(session.preset);
            }
            session.saved = session.preset; p.sendMessage(Component.text("Saved preset '" + session.preset.id() + "'.")); edit(p, session);
        });
        button(view, 42, Material.LIME_CONCRETE, "Set Up Match", List.of("Use these settings without saving a preset.", "Closing setup discards it; Prepare or Start creates a run."), p -> {
            session.match = new MatchSetup(session.preset, new Random().nextLong()); session.run = null; session.teamCount = 2; match(p, session);
        });
        button(view, 49, Material.ARROW, "Back — Discard Unsaved Edits", List.of(), p -> presets(p, 0)); show(player, view);
    }
    private void number(View view, int slot, String label, String value, String field, Session session) {
        button(view, slot, Material.COMPARATOR, label + ": " + value, List.of("Temporary until Save Preset."), p -> prompt(p, session, label, text -> session.preset = session.preset.with(field, text), q -> edit(q, session)));
    }
    private void roomPool(Player player, Session session, String field, int number) {
        List<RoomTemplate> entries = templates.all().stream().filter(t -> switch (field) {
            case "root" -> t.category() == RoomCategory.START;
            case "spawns" -> t.category() == RoomCategory.TEAM_SPAWN;
            default -> t.category() != RoomCategory.START && t.category() != RoomCategory.TEAM_SPAWN;
        }).sorted(Comparator.comparing(RoomTemplate::id)).toList();
        MinigamePreset preset = session.preset;
        List<String> selected = field.equals("root") ? List.of(preset.rootId()) : field.equals("spawns") ? preset.spawnRooms() : preset.ordinaryRooms();
        page(player, field.equals("spawns") ? "Spawn Rooms" : "Select " + field, entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> roomPool(p, session, field, next));
            if (field.equals("spawns")) button(v, 48, Material.RESPAWN_ANCHOR, "Edit Spawn Areas", List.of("Choose a room to edit its Player Spawn markers."), p -> spawnRooms(p, session, 0));
            button(v, 49, Material.ARROW, "Done", List.of("Changes are not saved yet."), p -> edit(p, session));
        }, (entry, slot) -> {
            RoomTemplate room = entry.value(); List<String> lore = new ArrayList<>(List.of("Left click: select / remove."));
            if (field.equals("spawns")) lore.addAll(List.of("Spawn Areas: " + com.dungeonarchitect.runtime.SpawnAreaResolver.areaCount(room), "Select floor areas. Capacity checked during preparation. Right click: edit markers."));
            button(entry.view(), slot, selected.contains(room.id()) ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, room.id(), lore, (p, click) -> {
                if (field.equals("spawns") && click.isRightClick()) { spawnPoints(p, session, room.id(), q -> roomPool(q, session, field, number)); return; }
                List<String> changed = new ArrayList<>(selected); if (!changed.remove(room.id())) changed.add(room.id());
                session.preset = session.preset.with(field, field.equals("root") ? room.id() : String.join(",", changed)); roomPool(p, session, field, number);
            });
        });
    }
    private void spawnRooms(Player player, Session session, int number) {
        List<RoomTemplate> entries = templates.all().stream().filter(t -> t.category() == RoomCategory.TEAM_SPAWN).sorted(Comparator.comparing(RoomTemplate::id)).toList();
        page(player, "Choose Spawn Room", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> spawnRooms(p, session, next));
            button(v, 49, Material.ARROW, "Back", List.of(), p -> { if (session.match == null && session.run == null) edit(p, session); else match(p, session); });
        }, (entry, slot) -> button(entry.view(), slot, Material.RESPAWN_ANCHOR, entry.value().id() + " — Spawn Areas: " + com.dungeonarchitect.runtime.SpawnAreaResolver.areaCount(entry.value()),
            List.of("Click to edit Player Spawn markers.", "Safe floor positions are spread across this room’s areas."), p -> spawnPoints(p, session, entry.value().id(), q -> spawnRooms(q, session, number))));
    }
    private void spawnPoints(Player player, Session session, String roomId, Consumer<Player> done) {
        if (markerMenus == null) throw new IllegalStateException("Marker editor is unavailable");
        session.spawnEditing = true;
        try { markerMenus.openRoomSpawnMarkers(player, roomId, p -> { session.spawnEditing = false; if (sessions.get(p.getUniqueId()) == session) done.accept(p); }); }
        catch (RuntimeException ex) { session.spawnEditing = false; throw ex; }
    }
    public void spawnPoints(Player player, String roomId) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) spawnPoints(player, session, roomId, p -> { if (session.match != null || session.run != null) match(p, session); else edit(p, session); });
        else { if (markerMenus == null) throw new IllegalStateException("Marker editor is unavailable"); markerMenus.openRoomSpawnMarkers(player, roomId, back); }
    }
    private void allowedKits(Player player, List<String> selected, Consumer<List<String>> save, Consumer<Player> done, int number) {
        Set<String> ids = new TreeSet<>(games.availableKits()); ids.addAll(selected); List<String> entries = List.copyOf(ids);
        page(player, "Allowed Kits", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> allowedKits(p, selected, save, done, next));
            button(v, 48, Material.BARRIER, "No Kits", List.of("Players start without gear."), p -> { save.accept(List.of()); done.accept(p); });
            button(v, 49, Material.ARROW, "Done", List.of(), done);
        }, (entry, slot) -> button(entry.view(), slot, selected.contains(entry.value()) ? Material.LIME_CONCRETE : Material.IRON_SWORD, entry.value(), List.of("Click to toggle."), p -> {
            List<String> changed = new ArrayList<>(selected); if (!changed.remove(entry.value())) changed.add(entry.value()); save.accept(changed); allowedKits(p, changed, save, done, number);
        }));
    }
    private void runs(Player player, int number) {
        discard(player); List<MinigameRun> entries = games.runs().stream().filter(r -> r.state() != RunState.ENDED).toList();
        page(player, "Minigame runs", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), this::runs);
            button(v, 48, Material.BOOK, "Set Up Match From Preset", List.of(), p -> presets(p, 0));
            button(v, 49, Material.ARROW, "Back", List.of(), this::open);
        }, (entry, slot) -> button(entry.view(), slot, Material.ENDER_PEARL, shortId(entry.value()) + " · " + entry.value().preset().id(), List.of(entry.value().state().name(), "Players: " + entry.value().teams().size()), p -> run(p, entry.value())));
    }
    public void run(Player player, MinigameRun run) {
        discard(player); Session session = new Session(run.preset(), false); session.run = run;
        session.teamCount = Math.max(2, Math.min(8, new HashSet<>(run.teams().values()).size())); sessions.put(player.getUniqueId(), session); match(player, session);
    }
    private void requireEditable(Session session) { if (!session.editable()) throw new IllegalStateException("Teams and kits are locked after preparation begins"); }
    private void match(Player player, Session session) {
        MinigameRun run = session.run; View view = view(run == null ? "Match Setup" : "Run: " + shortId(run));
        button(view, 4, Material.PAPER, session.loading ? "Generating..." : run == null ? "Temporary Match Setup" : run.state().name(), List.of("Preset settings: " + session.preset.id(), "Closing unfinished setup discards it."), p -> match(p, session));
        button(view, 10, Material.PLAYER_HEAD, "Players and Teams: " + session.teams().size(), List.of("Choose players, team count, and balancing."), p -> roster(p, session, 0));
        button(view, 12, Material.COMPARATOR, "Balance Selected Players", List.of("Teams: " + session.teamCount, "Replaces manual assignments for selected players."), p -> { requireEditable(session); balance(session, session.teams().keySet()); match(p, session); });
        button(view, 14, Material.IRON_SWORD, "Kit Choices: " + session.kits().size(), session.kits(), p -> {
            requireEditable(session); allowedKits(p, session.kits(), kits -> { requireEditable(session); if (session.run == null) session.match.kits(kits); else games.allowedKits(session.run.id(), kits); }, q -> match(q, session), 0);
        });
        button(view, 16, Material.MAP, "Spread Report", run == null ? List.of("Prepare the dungeon first.") : run.spread().map(s -> List.of("Shortest: " + s.shortest() + " doors", "Longest: " + s.longest() + " doors", "Team rooms: " + run.spawnRooms(), "Click for every pair in chat.")).orElse(List.of("Not yet available.")), p -> { if (session.run != null) inspect(p, session.run); });
        button(view, 22, Material.RESPAWN_ANCHOR, "Edit Spawn Areas", List.of("Spawn areas need enough safe floor positions for the team."), p -> { requireEditable(session); spawnRooms(p, session, 0); });
        button(view, 29, Material.STRUCTURE_BLOCK, "Prepare", List.of("Validate and build without moving players."), p -> prepare(p, session, false));
        button(view, 31, Material.LIME_CONCRETE, "Start", List.of("Prepare if needed, then send players in."), p -> prepare(p, session, true));
        button(view, 33, Material.RED_CONCRETE, run == null ? "Discard Setup" : "Cancel / End", List.of("Restore participants if the run has begun."), p -> { if (session.run != null) games.end(session.run.id(), "Ended by " + p.getName()); runs(p, 0); });
        button(view, 49, Material.ARROW, "Back to Runs", List.of("Unprepared setup is discarded."), p -> runs(p, 0)); show(player, view);
    }
    private void prepare(Player player, Session session, boolean start) {
        if (session.loading) throw new IllegalStateException("This match is already preparing");
        if (session.run != null && session.run.state() != RunState.DRAFT) {
            if (!start) throw new IllegalStateException("This dungeon is already prepared");
            player.closeInventory(); games.start(session.run.id()); return;
        }
        session.loading = true;
        try {
            var prepared = session.run == null ? games.prepare(session.match) : games.prepare(session.run.id());
            prepared.whenComplete((ready, error) -> {
                session.loading = false;
                if (error != null) {
                    if (player.isOnline()) explain(player, error);
                    if (sessions.get(player.getUniqueId()) == session) match(player, session);
                    return;
                }
                session.run = ready;
                if (start) {
                    if (sessions.get(player.getUniqueId()) == session) player.closeInventory();
                    try { games.start(ready.id()); } catch (RuntimeException ex) { if (player.isOnline()) explain(player, ex); }
                } else if (sessions.get(player.getUniqueId()) == session && player.isOnline()) match(player, session);
            });
            if (session.loading) match(player, session);
        } catch (RuntimeException ex) { session.loading = false; explain(player, ex); match(player, session); }
    }
    private static void explain(Player player, Throwable error) {
        while (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) error = error.getCause();
        player.sendMessage(error instanceof com.dungeonarchitect.runtime.SpawnAreaException missing ? missing.guidance() : Component.text(error.getMessage() == null ? "Could not complete this action" : error.getMessage()));
    }
    private void balance(Session session, Collection<UUID> players) {
        if (session.run == null) { session.match.teamCount(session.teamCount); session.match.balance(players); }
        else { MatchSetup.balanced(players, session.teamCount, session.run.seed()); games.balance(session.run.id(), players, session.teamCount); }
    }
    private void assign(Session session, UUID player, int team) {
        requireEditable(session); if (session.run == null) session.match.assign(player, team); else games.assign(session.run.id(), player, team);
    }
    private void roster(Player player, Session session, int number) {
        Set<UUID> ids = new LinkedHashSet<>(session.teams().keySet()); Bukkit.getOnlinePlayers().forEach(p -> ids.add(p.getUniqueId()));
        List<UUID> entries = ids.stream().sorted(Comparator.comparing(id -> Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()), String.CASE_INSENSITIVE_ORDER)).toList();
        page(player, "Players and Teams", entries, number, (v, page) -> {
            navigation(v, page, entries.size(), (p, next) -> roster(p, session, next));
            if (session.editable()) {
                button(v, 46, Material.PLAYER_HEAD, "Use All Online & Balance", List.of("Select eligible online players across every page.", "Balance into " + session.teamCount + " teams; replaces manual assignments."), p -> {
                    requireEditable(session); UUID allowedRun = session.run == null ? null : session.run.id();
                    List<UUID> eligible = Bukkit.getOnlinePlayers().stream().filter(candidate -> games.unavailableReason(candidate, allowedRun).isEmpty()).map(Player::getUniqueId).toList();
                    balance(session, eligible); roster(p, session, number);
                });
                button(v, 47, Material.COMPARATOR, "Teams: " + session.teamCount, List.of("Left click: +1. Right click: -1. Range: 2–8.", "Then use a balance action to apply this count."), (p, click) -> { requireEditable(session); session.teamCount = Math.max(2, Math.min(8, session.teamCount + (click.isRightClick() ? -1 : 1))); roster(p, session, number); });
                button(v, 48, Material.HOPPER, "Balance Selected", List.of("Use only selected players. Replaces manual assignments."), p -> { requireEditable(session); balance(session, session.teams().keySet()); roster(p, session, number); });
                button(v, 50, Material.BARRIER, "Clear Selection", List.of(), p -> { for (UUID id : session.teams().keySet()) assign(session, id, 0); roster(p, session, number); });
            }
            button(v, 49, Material.ARROW, "Done", List.of(), p -> match(p, session));
        }, (entry, slot) -> {
            UUID id = entry.value(); int team = session.teams().getOrDefault(id, 0); Player online = Bukkit.getPlayer(id);
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString());
            Optional<String> unavailable = online == null ? Optional.of("Offline") : games.unavailableReason(online, session.run == null ? null : session.run.id());
            List<String> lore = new ArrayList<>(List.of(team == 0 ? "Not selected" : "Selected — Team " + team));
            if (!session.editable()) lore.add("Read only: preparation has begun.");
            else if (unavailable.isPresent()) lore.add("Unavailable: " + unavailable.get());
            else lore.addAll(List.of("Left click: select / remove", "Right click: next team (1–8)"));
            button(entry.view(), slot, Material.PLAYER_HEAD, (team == 0 ? "" : "✓ ") + name, lore, (p, click) -> {
                requireEditable(session);
                if (unavailable.isPresent() && (team == 0 || click.isRightClick())) throw new IllegalArgumentException(name + " is unavailable: " + unavailable.get());
                assign(session, id, click.isRightClick() ? team % 8 + 1 : team == 0 ? 1 : 0); roster(p, session, number);
            });
            ItemStack head = entry.view().inventory.getItem(slot); SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(online == null ? Bukkit.getOfflinePlayer(id).getPlayerProfile() : online.getPlayerProfile());
            meta.setEnchantmentGlintOverride(team > 0);
            meta.displayName(Component.text((team == 0 ? "" : "✓ ") + name, unavailable.isPresent() ? NamedTextColor.GRAY : team > 0 ? NamedTextColor.AQUA : NamedTextColor.WHITE)); head.setItemMeta(meta);
        });
    }
    public void kits(Player player) { kits(player, 0); }
    private void kits(Player player, int number) {
        MinigameRun run = games.getRun(player).orElseThrow(() -> new IllegalArgumentException("You are not in a run"));
        if (run.state() != RunState.PREPARING || !run.started || run.kits().size() < 2) { player.sendMessage(Component.text("Kit selection is not open.")); return; }
        View view = new View("Choose a kit", true); int page = Math.max(0, Math.min(number, (run.kits().size() - 1) / 45));
        for (int i = page * 45; i < Math.min(run.kits().size(), (page + 1) * 45); i++) { String id = run.kits().get(i); button(view, i % 45, Material.IRON_SWORD, id, List.of("Click to select."), p -> games.chooseKit(p, id)); }
        if (page > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> kits(p, page - 1));
        if ((page + 1) * 45 < run.kits().size()) button(view, 53, Material.ARROW, "Next", List.of(), p -> kits(p, page + 1));
        button(view, 49, Material.ENDER_EYE, "Random", List.of("Choose any allowed kit at random."), p -> games.chooseKit(p, "random")); show(player, view);
    }
    public static void inspect(org.bukkit.command.CommandSender sender, MinigameRun run) {
        sender.sendMessage(Component.text(run.id() + " · " + run.state() + " · " + run.preset().id() + " · seed " + run.seed()));
        run.teams().forEach((id, team) -> sender.sendMessage(Component.text(Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()) + ": team " + team)));
        sender.sendMessage(Component.text("Team spawn rooms: " + run.spawnRooms())); run.spread().ifPresent(s -> sender.sendMessage(Component.text(s.summary())));
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
            if (!player.isOnline() || player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() != view.inventory || view.session != null && sessions.get(player.getUniqueId()) != view.session) return;
            try { action.accept(player, event.getClick()); } catch (RuntimeException ex) { explain(player, ex); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true); }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session before = sessions.get(player.getUniqueId()); if (before == null) return;
        boolean own = event.getInventory().getHolder() instanceof View view && view.session == before;
        if (!own && !(before.spawnEditing && markerMenus != null && markerMenus.ownsMarkerNavigation(player, event.getInventory()))) return;
        if (!plugin.isEnabled()) { discard(player); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session session = sessions.get(player.getUniqueId());
            if (session != before || session.prompting) return;
            if (session.spawnEditing && markerMenus != null && markerMenus.retainsMarkerNavigation(player)) return;
            Inventory current = player.getOpenInventory() == null ? null : player.getOpenInventory().getTopInventory();
            if (current != null && (current.getHolder() instanceof View next && next.session == session || session.spawnEditing && markerMenus != null && markerMenus.ownsMarkerNavigation(player, current))) return;
            discard(player);
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId()); if (session == null) return;
        if (event.getInventory().getHolder() instanceof View view && view.session == session) return;
        if (session.spawnEditing && markerMenus != null && markerMenus.ownsMarkerNavigation(player, event.getInventory())) return;
        discard(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { discard(event.getPlayer()); }
    @Override public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            discard(player);
            Inventory current = player.getOpenInventory() == null ? null : player.getOpenInventory().getTopInventory();
            if (current != null && current.getHolder() instanceof View) player.closeInventory();
        }
        sessions.clear();
    }
}
