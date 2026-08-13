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
import java.util.Random;
import java.util.logging.Logger;

/** Applies a template's explicit marker bindings after its blocks are pasted. */
public final class LootService {
    private final LootTableRegistry registry;
    private final Logger logger;

    public LootService(LootTableRegistry registry, Logger logger) { this.registry = registry; this.logger = logger; }

    public void placeLoot(World world, String ownerId, List<RoomMarker> markers, Map<String, String> bindings, RoomTransform transform, long dungeonSeed, String placementId) {
        for (RoomMarker marker : markers) {
            String tableId = bindings.get(marker.name());
            if (tableId == null) continue;
            LootTable table = registry.get(tableId).orElse(null);
            if (table == null) { logger.warning("Skipped loot marker " + ownerId + "/" + marker.name() + ": unknown table " + tableId); continue; }
            IntVector3 worldPosition = transform.transformLocal(marker.position());
            BlockState state = world.getBlockAt(worldPosition.x(), worldPosition.y(), worldPosition.z()).getState();
            if (!(state instanceof InventoryHolder holder)) {
                logger.warning("Skipped loot marker " + ownerId + "/" + marker.name() + ": target is not an inventory");
                continue;
            }
            fill(holder.getInventory(), table, new Random(seed(dungeonSeed, ownerId, marker.name(), placementId)));
        }
    }

    static void fill(Inventory inventory, LootTable table, Random random) {
        int rolls = table.minimumRolls() + random.nextInt(table.maximumRolls() - table.minimumRolls() + 1);
        int[] selectedCounts = new int[table.entries().size()];
        for (int draw = 0; draw < rolls; draw++) {
            List<Integer> empty = emptySlots(inventory);
            if (empty.isEmpty()) return;
            int entryIndex = selectAvailableIndex(table.entries(), selectedCounts, random);
            if (entryIndex < 0) return;
            LootEntry entry = table.entries().get(entryIndex);
            ItemStack item = entry.item();
            item.setAmount(entry.minimumAmount() + random.nextInt(entry.maximumAmount() - entry.minimumAmount() + 1));
            inventory.setItem(empty.get(random.nextInt(empty.size())), item);
            selectedCounts[entryIndex]++;
        }
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
    private static long seed(long seed, String... values) { for (String value : values) seed = 31 * seed + value.hashCode(); return seed; }
}
