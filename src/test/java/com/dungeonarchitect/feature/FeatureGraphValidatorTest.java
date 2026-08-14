package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureGraphValidatorTest {
    private static final IntVector3 ONE = new IntVector3(1, 1, 1);
    @Test
    void rejectsSelfAndIndirectCyclesWithPaths() {
        var validator = new FeatureGraphValidator(new FeatureNestingPolicy(4, 256));
        Map<String, FeatureTemplate> self = Map.of("a", feature("a", List.of(slot("self", "a"))));
        assertTrue(validator.analyze(self, Map.of()).errors("a").stream().anyMatch(error -> error.contains("a -> a")));

        Map<String, FeatureTemplate> cycle = new LinkedHashMap<>();
        cycle.put("a", feature("a", List.of(slot("to_b", "b"))));
        cycle.put("b", feature("b", List.of(slot("to_c", "c"))));
        cycle.put("c", feature("c", List.of(slot("to_a", "a"))));
        var analysis = validator.analyze(cycle, Map.of());
        assertFalse(analysis.errors("a").isEmpty());
        assertFalse(analysis.errors("b").isEmpty());
        assertFalse(analysis.errors("c").isEmpty());

        Map<String, FeatureTemplate> longCycle = chain(6);
        longCycle.put("n6", feature("n6", List.of(slot("back", "n1"))));
        assertTrue(validator.analyze(longCycle, Map.of()).errors("n1").stream().anyMatch(error -> error.contains("cycle")));
    }

    @Test
    void acceptsDepthFourAndRejectsDepthFive() {
        var validator = new FeatureGraphValidator(new FeatureNestingPolicy(4, 256));
        Map<String, FeatureTemplate> graph = chain(5);
        var analysis = validator.analyze(graph, Map.of());

        assertEquals(4, analysis.metrics("n2").depth());
        assertTrue(analysis.errors("n1").stream().anyMatch(error -> error.contains("depth 5")));
    }

    @Test
    void expansionUsesMaximumCandidatePerSlotAndSumsSlots() {
        var validator = new FeatureGraphValidator(new FeatureNestingPolicy(8, 256));
        FeatureTemplate leaf = feature("leaf", List.of());
        FeatureTemplate two = feature("two", List.of(slot("child", "leaf")));
        FeatureTemplate three = feature("three", List.of(slot("first", "leaf"), slot("second", "leaf")));
        RoomFeatureSlot alternatives = new RoomFeatureSlot("choice", IntVector3.ZERO, ONE, Direction3.NORTH,
            List.of(new FeatureSlotEntry("two", 1), new FeatureSlotEntry("three", 1)));
        FeatureTemplate root = feature("root", List.of(alternatives, slot("extra", "leaf")));
        Map<String, FeatureTemplate> graph = Map.of("leaf", leaf, "two", two, "three", three, "root", root);

        assertEquals(5, validator.analyze(graph, Map.of()).metrics("root").expandedPlacements());
    }

    @Test
    void accepts256PlacementsAndRejects257() {
        var validator = new FeatureGraphValidator(new FeatureNestingPolicy(4, 256));
        FeatureTemplate leaf = feature("leaf", List.of());
        Map<String, FeatureTemplate> graph = new LinkedHashMap<>();
        graph.put("leaf", leaf);
        graph.put("ok", feature("ok", repeatedSlots(255)));
        graph.put("too_many", feature("too_many", repeatedSlots(256)));

        var analysis = validator.analyze(graph, Map.of());
        assertEquals(256, analysis.metrics("ok").expandedPlacements());
        assertTrue(analysis.errors("too_many").stream().anyMatch(error -> error.contains("257")));
    }

    @Test
    void missingAndLocallyInvalidDependenciesQuarantineParents() {
        var validator = new FeatureGraphValidator(new FeatureNestingPolicy());
        Map<String, FeatureTemplate> graph = Map.of(
            "bad", feature("bad", List.of()),
            "parent", feature("parent", List.of(slot("bad_child", "bad"))),
            "missing_parent", feature("missing_parent", List.of(slot("missing_child", "absent")))
        );
        var analysis = validator.analyze(graph, Map.of("bad", List.of("bad: missing feature.nbt")));

        assertTrue(analysis.errors("parent").stream().anyMatch(error -> error.contains("depends on invalid feature bad")));
        assertTrue(analysis.errors("missing_parent").stream().anyMatch(error -> error.contains("missing feature absent")));
    }

    private static Map<String, FeatureTemplate> chain(int length) {
        Map<String, FeatureTemplate> graph = new LinkedHashMap<>();
        for (int index = length; index >= 1; index--) {
            String id = "n" + index;
            graph.put(id, feature(id, index == length ? List.of() : List.of(slot("next", "n" + (index + 1)))));
        }
        return graph;
    }

    private static List<RoomFeatureSlot> repeatedSlots(int count) {
        List<RoomFeatureSlot> slots = new ArrayList<>();
        for (int index = 0; index < count; index++) slots.add(slot("slot_" + index, "leaf"));
        return slots;
    }

    private static RoomFeatureSlot slot(String id, String child) {
        return new RoomFeatureSlot(id, IntVector3.ZERO, ONE, Direction3.NORTH, List.of(new FeatureSlotEntry(child, 1)));
    }

    private static FeatureTemplate feature(String id, List<RoomFeatureSlot> slots) {
        return new FeatureTemplate(id, ONE, Set.of(), List.of(), slots, Map.of(), Path.of(id, "feature.nbt"));
    }
}
