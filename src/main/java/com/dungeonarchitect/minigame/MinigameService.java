package com.dungeonarchitect.minigame;

import com.dungeonarchitect.api.event.*;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.generation.*;
import com.dungeonarchitect.runtime.*;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns roster reservations, lifecycle, recovery, and per-run gameplay state. Server-thread API. */
public final class MinigameService implements AutoCloseable {
    private final Plugin plugin;
    private final DungeonManager dungeons;
    private final RoomTemplateRegistry templates;
    private final PresetRepository presets;
    private final RecoveryStore recovery;
    private final Map<UUID, MinigameRun> runs = new LinkedHashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();
    private final BukkitTask timer;
    private Consumer<Player> kitMenu = player -> { };
    private boolean closed;
    private final java.util.function.Predicate<Location> spawnSafety;

    public MinigameService(Plugin plugin, DungeonManager dungeons, RoomTemplateRegistry templates) {
        this(plugin, dungeons, templates, MinigameService::safe);
    }
    MinigameService(Plugin plugin, DungeonManager dungeons, RoomTemplateRegistry templates, java.util.function.Predicate<Location> spawnSafety) {
        this.plugin = plugin; this.dungeons = dungeons; this.templates = templates;
        this.spawnSafety = spawnSafety;
        presets = new PresetRepository(plugin.getDataFolder().toPath().resolve("minigames/presets"));
        recovery = new RecoveryStore(plugin.getDataFolder().toPath().resolve("minigames/recovery"));
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    private static void mainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Use the server thread for minigame operations"); }
    public PresetRepository presets() { return presets; }
    public Collection<MinigameRun> runs() { return List.copyOf(runs.values()); }
    public List<String> availableKits() { return KitProvider.available(); }
    public void kitMenu(Consumer<Player> menu) { kitMenu = menu; }
    public Optional<MinigameRun> getRun(UUID id) { return Optional.ofNullable(runs.get(id)); }
    public Optional<MinigameRun> getRun(Player player) { return Optional.ofNullable(membership.get(player.getUniqueId())).flatMap(this::getRun); }
    public Optional<MinigameRun> at(Location location) { return runs.values().stream().filter(r -> r.state != RunState.ENDED && r.contains(location)).findFirst(); }
    public MinigameRun require(String prefix) {
        List<MinigameRun> matches = runs.values().stream().filter(r -> r.id().toString().startsWith(prefix)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Unknown or ambiguous run id: " + prefix);
        return matches.getFirst();
    }
    public MinigameRun createRun(String preset, long seed) {
        mainThread(); if (closed) throw new IllegalStateException("Minigames are shutting down");
        MinigameRun run = new MinigameRun(presets.require(preset), seed); runs.put(run.id(), run); return run;
    }
    private void draft(MinigameRun run) { if (run.state != RunState.DRAFT) throw new IllegalStateException("Roster and settings are locked after preparation begins"); }
    public void assign(UUID id, UUID player, int team) {
        mainThread(); MinigameRun run = runs.get(id); draft(run);
        if (team == 0) run.teams.remove(player);
        else { if (team < 1 || team > 8) throw new IllegalArgumentException("Team must be 1–8, or 0 to remove a player"); run.teams.put(player, team); }
    }
    public void balance(UUID id, Collection<UUID> players, int teams) {
        mainThread(); MinigameRun run = runs.get(id); draft(run);
        Map<UUID, Integer> balanced = TeamAssignments.balanced(players, teams, run.seed());
        run.teams.clear(); run.teams.putAll(balanced);
    }
    public void allowedKits(UUID id, List<String> kits) { mainThread(); MinigameRun run = runs.get(id); draft(run); run.kits = List.copyOf(new LinkedHashSet<>(kits)); }

    public Optional<String> unavailableReason(Player player, UUID allowedRun) {
        if (!player.isOnline()) return Optional.of("Offline");
        if (player.isDead()) return Optional.of("Player is dead");
        UUID membershipId = membership.get(player.getUniqueId());
        boolean ownRun = membershipId != null && membershipId.equals(allowedRun);
        if (membershipId != null && !membershipId.equals(allowedRun)) return Optional.of("Already in another match");
        if (dungeons.getDungeon(player).isPresent() && !ownRun) return Optional.of("Already in a dungeon");
        if (recovery.pending(player.getUniqueId()) && !ownRun) return Optional.of("Awaiting player-state recovery");
        return Optional.empty();
    }

    public CompletableFuture<MinigameRun> prepare(MatchSetup setup) {
        mainThread();
        if (closed) throw new IllegalStateException("Minigames are shutting down");
        MinigameRun run = new MinigameRun(setup.preset(), setup.seed());
        run.teams.putAll(setup.teams()); run.kits = setup.kits();
        return prepare(run, true);
    }

    public CompletableFuture<MinigameRun> prepare(UUID id) {
        mainThread(); return prepare(runs.get(id), false);
    }

    private CompletableFuture<MinigameRun> prepare(MinigameRun run, boolean temporary) {
        draft(run);
        TeamAssignments.validate(run.teams);
        for (UUID playerId : run.teams.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || player.isDead()) throw new IllegalArgumentException("All rostered players must be online and alive before preparation");
            if (membership.containsKey(playerId) || dungeons.getDungeon(player).isPresent() || recovery.pending(playerId)) throw new IllegalArgumentException(player.getName() + " is already in a dungeon or awaiting recovery");
        }
        RoomTemplate root = template(run.preset().rootId(), RoomCategory.START);
        if (run.preset().ordinaryRooms().isEmpty() || run.preset().spawnRooms().isEmpty()) throw new IllegalArgumentException("Select ordinary rooms and team spawn templates in the preset");
        for (String room : run.preset().ordinaryRooms()) {
            RoomTemplate ordinary = templates.get(room).orElseThrow(() -> new IllegalArgumentException("Missing room: " + room));
            if (ordinary.category() == RoomCategory.START || ordinary.category() == RoomCategory.TEAM_SPAWN) throw new IllegalArgumentException("Ordinary room pool cannot contain START or TEAM_SPAWN rooms");
        }
        int largest = run.teams.values().stream().collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.stream.Collectors.counting())).values().stream().mapToInt(Long::intValue).max().orElseThrow();
        List<RoomTemplate> spawns = run.preset().spawnRooms().stream().map(room -> template(room, RoomCategory.TEAM_SPAWN)).sorted(Comparator.comparing(RoomTemplate::id)).toList();
        for (RoomTemplate spawn : spawns) if (com.dungeonarchitect.runtime.SpawnAreaResolver.areaCount(spawn) == 0 || spawnMarkers(spawn).size() < largest) throw new com.dungeonarchitect.runtime.SpawnAreaException(spawn.id(), spawnMarkers(spawn).size(), largest, false);
        Map<String, Map<String, org.bukkit.inventory.ItemStack>> kits = KitProvider.resolve(run.kits);
        Random random = new Random(run.seed());
        int totalWeight = spawns.stream().mapToInt(RoomTemplate::weight).sum(), roll = random.nextInt(totalWeight);
        RoomTemplate chosen = spawns.getLast();
        for (RoomTemplate candidate : spawns) { roll -= candidate.weight(); if (roll < 0) { chosen = candidate; break; } }
        TeamSpread spread = new TeamSpread(root.id(), Set.copyOf(run.preset().ordinaryRooms()), chosen.id(), new HashSet<>(run.teams.values()).size(), run.preset().minimumDistance(), run.preset().maximumRatio());
        DungeonRequest request = new DungeonRequest(run.preset().roomCount(), run.seed(), Set.copyOf(run.teams.keySet()), spread);
        new DungeonGenerationRequest(request.roomCount(), request.seed(), spread);
        if (temporary) runs.put(run.id(), run);
        run.kitContents.putAll(kits);
        for (UUID player : run.teams.keySet()) { membership.put(player, run.id()); run.participants.put(player, new MinigameRun.Participant(player)); }
        run.state = RunState.GENERATING;
        CompletableFuture<MinigameRun> result = new CompletableFuture<>();
        if (temporary) result.whenComplete((ready, error) -> { if (error != null) runs.remove(run.id(), run); });
        try { dungeons.prepareDungeonAsync(request).whenComplete((dungeon, error) -> {
            if (error != null) { end(run.id(), "Preparation failed: " + error.getMessage()); result.completeExceptionally(error); return; }
            if (run.state == RunState.ENDED || closed) { dungeons.destroyDungeon(dungeon.id()); result.completeExceptionally(new IllegalStateException("Run cancelled")); return; }
            run.dungeon = dungeon;
            try {
                run.spread = SpreadReport.measure(dungeon.graph());
                List<Integer> rooms = new ArrayList<>(run.spread.spawns());
                Collections.shuffle(rooms, new Random(run.seed() ^ 0x73A4B89L));
                List<Integer> teams = run.teams.values().stream().distinct().sorted().toList();
                for (int i = 0; i < teams.size(); i++) run.spawnRooms.put(teams.get(i), rooms.get(i));
                World world = Objects.requireNonNull(Bukkit.getWorld(dungeon.worldName()));
                for (int team : teams) {
                    RoomInstance room = dungeon.rooms().get(run.spawnRooms.get(team));
                    List<UUID> players = run.teams.entrySet().stream().filter(e -> e.getValue() == team).map(Map.Entry::getKey).sorted().toList();
                    List<Location> positions = com.dungeonarchitect.runtime.SpawnAreaResolver.resolve(world, room.template(), room.node().transform(), players.size(), run.seed() ^ team, spawnSafety);
                    for (int i = 0; i < players.size(); i++) {
                        Location location = positions.get(i);
                        var participant = run.participants.get(players.get(i)); participant.spawn = location; participant.frozenAt = location.clone();
                    }
                }
                run.state = RunState.PREPARING;
                Bukkit.getPluginManager().callEvent(new MinigamePreparedEvent(run));
                if (run.autoStart) start(run.id());
                result.complete(run);
            } catch (Exception ex) { end(run.id(), "Preparation failed: " + ex.getMessage()); result.completeExceptionally(ex); }
        }); } catch (RuntimeException ex) { end(run.id(), "Preparation failed: " + ex.getMessage()); result.completeExceptionally(ex); }
        return result;
    }
    private RoomTemplate template(String id, RoomCategory category) {
        RoomTemplate room = templates.get(id).orElseThrow(() -> new IllegalArgumentException("Missing template: " + id));
        if (room.category() != category) throw new IllegalArgumentException(id + " must be a " + category + " template"); return room;
    }
    public static List<IntVector3> spawnMarkers(RoomTemplate template) {
        return com.dungeonarchitect.runtime.SpawnAreaResolver.candidates(template);
    }
    private static boolean safe(Location location) { return com.dungeonarchitect.runtime.SpawnAreaResolver.safe(location); }

    public void start(UUID id) {
        mainThread(); MinigameRun run = runs.get(id);
        if (run.state == RunState.DRAFT) {
            run.autoStart = true;
            try { prepare(id); } catch (RuntimeException ex) { run.autoStart = false; throw ex; }
            return;
        }
        if (run.state == RunState.GENERATING) { run.autoStart = true; return; }
        if (run.state != RunState.PREPARING || run.started) throw new IllegalStateException("Run is not waiting to start");
        run.started = true; run.remaining = 10;
        try {
            // Capture everyone before mutating anyone.
            for (var p : run.participants.values()) {
                Player player = Bukkit.getPlayer(p.id);
                if (player != null && !p.departed) recovery.save(player);
            }
            for (var p : run.participants.values()) {
                if (p.departed) continue;
                Player player = Bukkit.getPlayer(p.id);
                if (player == null) { if (p.disconnectedAt < 0) p.disconnectedAt = run.seconds; continue; }
                enter(run, p, player, false);
                if (run.kits.size() > 1) kitMenu.accept(player);
            }
            advanceSelection(run);
        } catch (Exception ex) { end(id, "Entry failed: " + ex.getMessage()); throw ex; }
    }
    private void enter(MinigameRun run, MinigameRun.Participant p, Player player, boolean reconnect) {
        if (!recovery.pending(p.id)) recovery.save(player);
        p.entered = true;
        if (reconnect && p.matchState != null) {
            if (!p.matchState.apply(player)) throw new IllegalStateException("Cannot re-enter while dead");
        } else {
            if (player.isDead()) throw new IllegalStateException("Cannot enter while dead");
            PlayerSnapshot.clear(player);
            if (!player.teleport(p.spawn)) throw new IllegalStateException("Entry teleport was blocked for " + player.getName());
        }
        dungeons.attachPlayer(run.dungeon, player);
        p.disconnectedAt = -1;
        if (p.eliminated) player.setGameMode(GameMode.SPECTATOR);
        else if (run.kits.size() == 1) p.kit = run.kits.getFirst();
        else if (reconnect && p.kit == null && !run.kits.isEmpty()) p.kit = randomKit(run, p.id);
        if (run.state == RunState.RUNNING || run.state == RunState.COUNTDOWN) equip(run, p, player);
        else player.setCollidable(false);
    }
    private String randomKit(MinigameRun run, UUID player) {
        return run.kits.get(new Random(run.seed() ^ player.getMostSignificantBits() ^ player.getLeastSignificantBits()).nextInt(run.kits.size()));
    }
    public void chooseKit(Player player, String kit) {
        mainThread(); MinigameRun run = getRun(player).orElseThrow(() -> new IllegalArgumentException("You are not in a run"));
        var p = run.participants.get(player.getUniqueId());
        if (!run.started || !p.entered || p.departed || run.state != RunState.PREPARING || run.kits.isEmpty()) throw new IllegalStateException("Kit selection is closed");
        if (!kit.equalsIgnoreCase("random") && !run.kits.contains(kit)) throw new IllegalArgumentException("Kit is not allowed in this run");
        p.kit = kit.equalsIgnoreCase("random") ? randomKit(run, p.id) : kit;
        player.closeInventory(); player.sendMessage(Component.text("Selected kit: " + p.kit)); advanceSelection(run);
    }
    private void equip(MinigameRun run, MinigameRun.Participant p, Player player) {
        if (p.equipped || p.eliminated) return;
        if (p.kit != null) KitProvider.equip(player, run.kitContents.get(p.kit));
        p.equipped = true;
    }
    private void advanceSelection(MinigameRun run) {
        if (!run.started || run.state != RunState.PREPARING) return;
        boolean ready = run.kits.size() < 2 || run.participants.values().stream().filter(p -> !p.departed && Bukkit.getPlayer(p.id) != null).allMatch(p -> p.kit != null);
        if (!ready && run.remaining > 0) return;
        for (var p : run.participants.values()) {
            if (p.departed) continue;
            if (p.kit == null && !run.kits.isEmpty()) p.kit = randomKit(run, p.id);
            Player player = Bukkit.getPlayer(p.id); if (player != null) { player.closeInventory(); equip(run, p, player); p.frozenAt = player.getLocation(); }
        }
        run.state = RunState.COUNTDOWN; run.remaining = 5; countdown(run);
    }
    private void countdown(MinigameRun run) {
        for (var p : run.participants.values()) {
            Player player = Bukkit.getPlayer(p.id); if (player == null || !p.entered || p.departed) continue;
            player.showTitle(Title.title(Component.text(run.remaining > 0 ? Integer.toString(run.remaining) : "GO!"), Component.text(run.remaining > 0 ? "Get ready" : "Explore, loot, survive"), Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1100), java.time.Duration.ofMillis(150))));
            player.playSound(player.getLocation(), run.remaining > 0 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_PLAYER_LEVELUP, 1f, run.remaining > 0 ? 1f + (5 - run.remaining) * .15f : 1.2f);
        }
    }
    private void tick() {
        for (MinigameRun run : List.copyOf(runs.values())) {
            if (run.state == RunState.ENDED || run.state == RunState.DRAFT) continue;
            try {
                run.seconds++;
                for (var p : run.participants.values()) if (!p.departed && p.disconnectedAt >= 0 && run.seconds - p.disconnectedAt >= 120) leave(run, p);
                if (!run.participants.isEmpty() && run.participants.values().stream().allMatch(p -> p.departed)) { end(run.id(), "No participants remain"); continue; }
                if (run.state == RunState.PREPARING && run.started) {
                    run.remaining--; advanceSelection(run);
                    if (run.state == RunState.PREPARING) for (var p : run.participants.values()) {
                        Player player = Bukkit.getPlayer(p.id); if (player != null && !p.departed) player.sendActionBar(Component.text("Choose a kit: " + run.remaining + "s — /dplay kit"));
                    }
                } else if (run.state == RunState.COUNTDOWN) {
                    run.remaining--; countdown(run);
                    if (run.remaining <= 0) {
                        run.state = RunState.RUNNING; run.dungeon.state(DungeonState.ACTIVE);
                        for (var p : run.participants.values()) { Player player = Bukkit.getPlayer(p.id); if (player != null && !p.departed) player.setCollidable(true); }
                        Bukkit.getPluginManager().callEvent(new MinigameStartedEvent(run));
                    }
                }
            } catch (Exception ex) { plugin.getLogger().severe("Minigame tick failed: " + ex); end(run.id(), "Run failed: " + ex.getMessage()); }
        }
    }
    public void eliminate(UUID runId, UUID playerId) {
        mainThread(); MinigameRun run = runs.get(runId); var p = run.participants.get(playerId);
        if (p == null || p.departed || p.eliminated || run.state != RunState.RUNNING) return;
        p.eliminated = true;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !player.isDead()) { player.getInventory().clear(); player.setGameMode(GameMode.SPECTATOR); }
        Bukkit.getPluginManager().callEvent(new MinigameEliminatedEvent(run, playerId));
    }
    public boolean exit(Player player) {
        mainThread(); MinigameRun run = getRun(player).orElse(null);
        if (run == null) return false;
        leave(run, run.participants.get(player.getUniqueId()));
        if (run.participants.values().stream().allMatch(p -> p.departed)) end(run.id(), "No participants remain");
        return true;
    }
    private void leave(MinigameRun run, MinigameRun.Participant p) {
        if (p.departed) return;
        p.departed = true; membership.remove(p.id, run.id()); dungeons.detachPlayer(p.id);
        Player player = Bukkit.getPlayer(p.id);
        if (player != null) restore(player);
    }
    private void restore(Player player) {
        try { recovery.restore(player); }
        catch (Exception ex) { plugin.getLogger().severe("Recovery pending for " + player.getUniqueId() + ": " + ex); player.kick(Component.text("Your original inventory is saved. Please reconnect to finish recovery.")); }
    }
    public void end(UUID id, String reason) {
        mainThread(); MinigameRun run = runs.get(id);
        if (run == null || run.state == RunState.ENDED || run.state == RunState.ENDING) return;
        run.state = RunState.ENDING; run.message = reason;
        for (var p : run.participants.values()) leave(run, p);
        try { if (run.dungeon != null) dungeons.destroyDungeon(run.dungeon.id()); }
        finally {
            run.state = RunState.ENDED;
            run.kitContents.clear();
            run.participants.values().forEach(p -> p.matchState = null);
            Bukkit.getPluginManager().callEvent(new MinigameEndedEvent(run, reason));
        }
    }
    void quit(Player player) {
        MinigameRun run = getRun(player).orElse(null); if (run == null) return;
        var p = run.participants.get(player.getUniqueId()); if (p == null || p.departed) return;
        if (p.entered) p.matchState = PlayerSnapshot.capture(player);
        p.disconnectedAt = run.seconds;
        Bukkit.getScheduler().runTask(plugin, () -> advanceSelection(run));
    }
    public void join(Player player) {
        MinigameRun run = getRun(player).orElse(null);
        if (run == null) { restore(player); return; }
        var p = run.participants.get(player.getUniqueId());
        if (p.disconnectedAt >= 0 && run.seconds - p.disconnectedAt >= 120) { leave(run, p); return; }
        if (!run.started) { p.disconnectedAt = -1; return; }
        try { enter(run, p, player, true); advanceSelection(run); }
        catch (Exception ex) { end(run.id(), "Reconnect failed: " + ex.getMessage()); }
    }
    void respawn(Player player) {
        MinigameRun run = getRun(player).orElse(null);
        if (run == null) { restore(player); return; }
        if (run.eliminated(player.getUniqueId())) { player.getInventory().clear(); player.setGameMode(GameMode.SPECTATOR); }
    }
    boolean entered(Player player) { MinigameRun r = getRun(player).orElse(null); return r != null && r.participants.get(player.getUniqueId()).entered; }
    Location anchor(Player player) { return getRun(player).orElseThrow().participants.get(player.getUniqueId()).frozenAt.clone(); }
    @Override public void close() {
        mainThread(); closed = true; timer.cancel();
        for (MinigameRun run : List.copyOf(runs.values())) end(run.id(), "Server shutdown");
    }
}
