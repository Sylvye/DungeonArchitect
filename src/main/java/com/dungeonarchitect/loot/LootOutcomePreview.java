package com.dungeonarchitect.loot;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Computes final-item probabilities for the first uncapped draw. */
public final class LootOutcomePreview {
    private LootOutcomePreview() { }

    public static List<Outcome> flatten(LootTable root, LootTableRegistry registry) {
        List<MutableOutcome> outcomes = new ArrayList<>();
        visit(root, registry, 1D, root.id(), new HashSet<>(), outcomes);
        return outcomes.stream().map(MutableOutcome::freeze).sorted(java.util.Comparator.comparingDouble(Outcome::probability).reversed()).toList();
    }

    private static void visit(LootTable table, LootTableRegistry registry, double parentProbability, String path, Set<String> visiting, List<MutableOutcome> outcomes) {
        if (!visiting.add(table.id()) || table.entries().isEmpty()) return;
        int totalWeight = table.entries().stream().mapToInt(LootPoolEntry::weight).sum();
        for (LootPoolEntry entry : table.entries()) {
            double probability = parentProbability * entry.weight() / totalWeight;
            if (entry instanceof LootEntry item) add(outcomes, item, probability, path);
            else if (entry instanceof LootTableEntry nested) registry.get(nested.tableId()).ifPresent(child -> visit(child, registry, probability, path + " -> " + child.id(), new HashSet<>(visiting), outcomes));
        }
    }

    private static void add(List<MutableOutcome> outcomes, LootEntry entry, double probability, String path) {
        for (MutableOutcome outcome : outcomes) {
            if (outcome.minimumAmount == entry.minimumAmount() && outcome.maximumAmount == entry.maximumAmount() && outcome.item.isSimilar(entry.item())) {
                outcome.probability += probability;
                outcome.paths.add(path);
                return;
            }
        }
        outcomes.add(new MutableOutcome(entry.item(), entry.minimumAmount(), entry.maximumAmount(), probability, new ArrayList<>(List.of(path))));
    }

    public record Outcome(ItemStack item, int minimumAmount, int maximumAmount, double probability, List<String> paths) {
        public Outcome { item = item.clone(); paths = List.copyOf(paths); }
        @Override public ItemStack item() { return item.clone(); }
        public String percent() { return String.format(Locale.ROOT, "%.2f%%", probability * 100D); }
    }

    private static final class MutableOutcome {
        private final ItemStack item;
        private final int minimumAmount;
        private final int maximumAmount;
        private double probability;
        private final List<String> paths;
        private MutableOutcome(ItemStack item, int minimumAmount, int maximumAmount, double probability, List<String> paths) {
            this.item = item; this.minimumAmount = minimumAmount; this.maximumAmount = maximumAmount; this.probability = probability; this.paths = paths;
        }
        private Outcome freeze() { return new Outcome(item, minimumAmount, maximumAmount, probability, paths); }
    }
}
