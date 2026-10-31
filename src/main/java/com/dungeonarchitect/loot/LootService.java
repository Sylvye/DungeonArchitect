package com.dungeonarchitect.loot;

import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTransform;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import java.util.logging.Logger;

/** Applies a template's explicit marker bindings after its blocks are pasted. */
public final class LootService {
    private final LootTableRegistry registry;
    private final Logger logger;

    public LootService(LootTableRegistry registry, Logger logger) { this.registry = registry; this.logger = logger; }

    private final ThreadLocal<Batch> collecting = new ThreadLocal<>();
    public final class Batch {
        private final List<Request> requests = new ArrayList<>();
        private boolean finished;
        /** Validate every target before filling any inventory. Batches belong to one dungeon. */
        public void finish() {
            if (finished) throw new IllegalStateException("Loot batch already finished");
            finished = true;
            Map<String, Target> targets = new java.util.TreeMap<>();
            for (Request request : requests) {
                for (RoomMarker marker : request.markers) {
                    LootBinding binding = request.bindings.get(marker.name());
                    if (binding == null) continue;
                    if (marker.purpose() != RoomMarker.Purpose.LOOT) throw new IllegalArgumentException("Loot binding '" + request.owner + "/" + marker.name() + "' requires a Loot marker");
                    LootTable table = registry.get(binding.tableId()).orElseThrow(() -> new IllegalArgumentException("Unknown loot table '" + binding.tableId() + "' for " + request.owner + "/" + marker.name()));
                    Set<String> found = new HashSet<>();
                    IntVector3 min = marker.position(), max = marker.bounds().max();
                    for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) {
                        IntVector3 position = request.transform.transformLocal(new IntVector3(x, y, z));
                        BlockState state = request.world.getBlockAt(position.x(), position.y(), position.z()).getState();
                        if (!(state instanceof InventoryHolder holder)) continue;
                        Inventory inventory = holder.getInventory();
                        String key = inventoryKey(inventory, request.world, position);
                        if (!found.add(key)) continue;
                        String label = request.placement + "/" + request.owner + "/" + marker.name();
                        Target previous = targets.get(key);
                        if (previous != null) throw new IllegalArgumentException("Loot markers '" + previous.label + "' and '" + label + "' target the same inventory at " + position);
                        long randomSeed = seed(request.seed, request.owner, marker.name(), request.placement, Integer.toString(x), Integer.toString(y), Integer.toString(z));
                        targets.put(key, new Target(label, inventory, table, binding, randomSeed));
                    }
                    if (found.isEmpty()) throw new IllegalArgumentException("Loot marker '" + request.owner + "/" + marker.name() + "' contains no containers");
                }
            }
            for (Target target : targets.values()) fill(target.inventory, target.table, target.binding, registry, new Random(target.seed));
            requests.clear();
        }
    }
    private record Request(World world, String owner, List<RoomMarker> markers, Map<String, LootBinding> bindings, RoomTransform transform, long seed, String placement) { }
    private record Target(String label, Inventory inventory, LootTable table, LootBinding binding, long seed) { }
    public interface Scope extends AutoCloseable { @Override void close(); }
    public Batch batch() { return new Batch(); }
    public Scope collect(Batch batch) {
        Batch previous = collecting.get(); collecting.set(batch);
        return () -> { if (previous == null) collecting.remove(); else collecting.set(previous); };
    }
    public void placeLoot(World world, String ownerId, List<RoomMarker> markers, Map<String, LootBinding> bindings, RoomTransform transform, long dungeonSeed, String placementId) {
        Batch batch = collecting.get(); boolean immediate = batch == null;
        if (immediate) batch = batch();
        batch.requests.add(new Request(world, ownerId, List.copyOf(markers), Map.copyOf(bindings), transform, dungeonSeed, placementId));
        if (immediate) batch.finish();
    }
    private static String inventoryKey(Inventory inventory, World world, IntVector3 fallback) {
        if (inventory.getHolder() instanceof org.bukkit.block.DoubleChest chest) {
            String left = locationKey(chest.getLeftSide().getInventory().getLocation());
            String right = locationKey(chest.getRightSide().getInventory().getLocation());
            return left.compareTo(right) <= 0 ? left + ":" + right : right + ":" + left;
        }
        org.bukkit.Location location = inventory.getLocation();
        return location == null ? world.getUID() + ":" + fallback.x() + "," + fallback.y() + "," + fallback.z() : locationKey(location);
    }
    private static String locationKey(org.bukkit.Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    static void fill(Inventory inventory, LootTable table, LootBinding binding, LootTableRegistry registry, Random random) {
        int rolls = binding.minimumRolls() + random.nextInt(binding.maximumRolls() - binding.minimumRolls() + 1);
        Map<EntryKey, Integer> selectedCounts = new HashMap<>();
        for (int draw = 0; draw < rolls; draw++) {
            List<Integer> empty = emptySlots(inventory);
            if (empty.isEmpty()) return;
            LootEntry entry = resolve(table, registry, selectedCounts, random);
            if (entry == null) return;
            ItemStack item = entry.item();
            item.setAmount(entry.minimumAmount() + random.nextInt(entry.maximumAmount() - entry.minimumAmount() + 1));
            inventory.setItem(empty.get(random.nextInt(empty.size())), item);
        }
    }

    private static LootEntry resolve(LootTable start, LootTableRegistry registry, Map<EntryKey, Integer> counts, Random random) {
        LootTable table = start;
        Set<String> path = new HashSet<>();
        while (path.add(table.id())) {
            List<Integer> available = availableIndices(table, registry, counts, path);
            if (available.isEmpty()) return null;
            int total = 0;
            for (int index : available) total += table.entries().get(index).weight();
            int roll = random.nextInt(total);
            int selected = available.getLast();
            for (int index : available) {
                roll -= table.entries().get(index).weight();
                if (roll < 0) { selected = index; break; }
            }
            LootPoolEntry entry = table.entries().get(selected);
            EntryKey key = new EntryKey(table.id(), selected);
            counts.merge(key, 1, Integer::sum);
            if (entry instanceof LootEntry item) return item;
            LootTableEntry nested = (LootTableEntry) entry;
            table = registry.get(nested.tableId()).orElse(null);
            if (table == null) return null;
        }
        return null;
    }

    private static List<Integer> availableIndices(LootTable table, LootTableRegistry registry, Map<EntryKey, Integer> counts, Set<String> path) {
        List<Integer> available = new ArrayList<>();
        for (int index = 0; index < table.entries().size(); index++) {
            LootPoolEntry entry = table.entries().get(index);
            int used = counts.getOrDefault(new EntryKey(table.id(), index), 0);
            if (entry.maximumPerContainer() != 0 && used >= entry.maximumPerContainer()) continue;
            if (entry instanceof LootEntry) {
                available.add(index);
                continue;
            }
            LootTable child = registry.get(((LootTableEntry) entry).tableId()).orElse(null);
            if (child == null || path.contains(child.id())) continue;
            Set<String> childPath = new HashSet<>(path);
            childPath.add(child.id());
            if (!availableIndices(child, registry, counts, childPath).isEmpty()) available.add(index);
        }
        return available;
    }

    static LootEntry select(List<LootEntry> entries, Random random) {
        if (entries.isEmpty()) throw new IllegalArgumentException("Loot table has no entries");
        int total = entries.stream().mapToInt(LootEntry::weight).sum();
        int roll = random.nextInt(total);
        for (LootEntry entry : entries) { roll -= entry.weight(); if (roll < 0) return entry; }
        return entries.getLast();
    }

    static int selectAvailableIndex(List<LootEntry> entries, int[] selectedCounts, Random random) {
        int total = 0;
        for (int index = 0; index < entries.size(); index++) {
            LootEntry entry = entries.get(index);
            if (entry.maximumPerContainer() == 0 || selectedCounts[index] < entry.maximumPerContainer()) total += entry.weight();
        }
        if (total == 0) return -1;
        int roll = random.nextInt(total);
        for (int index = 0; index < entries.size(); index++) {
            LootEntry entry = entries.get(index);
            if (entry.maximumPerContainer() != 0 && selectedCounts[index] >= entry.maximumPerContainer()) continue;
            roll -= entry.weight();
            if (roll < 0) return index;
        }
        throw new IllegalStateException("Available loot entry selection failed");
    }

    private static List<Integer> emptySlots(Inventory inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) if (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir()) slots.add(i);
        return slots;
    }
    private record EntryKey(String tableId, int entryIndex) { }
    private static long seed(long seed, String... values) { for (String value : values) seed = 31 * seed + value.hashCode(); return seed; }
}
