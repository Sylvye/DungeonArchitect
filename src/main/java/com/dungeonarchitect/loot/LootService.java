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

    public void placeLoot(World world, String ownerId, List<RoomMarker> markers, Map<String, LootBinding> bindings, RoomTransform transform, long dungeonSeed, String placementId) {
        for (RoomMarker marker : markers) {
            LootBinding binding = bindings.get(marker.name());
            if (binding == null) continue;
            LootTable table = registry.get(binding.tableId()).orElse(null);
            if (table == null) { logger.warning("Skipped loot marker " + ownerId + "/" + marker.name() + ": unknown table " + binding.tableId()); continue; }
            IntVector3 worldPosition = transform.transformLocal(marker.position());
            BlockState state = world.getBlockAt(worldPosition.x(), worldPosition.y(), worldPosition.z()).getState();
            if (!(state instanceof InventoryHolder holder)) {
                logger.warning("Skipped loot marker " + ownerId + "/" + marker.name() + ": target is not an inventory");
                continue;
            }
            fill(holder.getInventory(), table, binding, registry, new Random(seed(dungeonSeed, ownerId, marker.name(), placementId)));
        }
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
