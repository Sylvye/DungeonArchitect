package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.template.TemplateLoadStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class SlotMultiEditMatcherTest {
    @Test
    void doorCandidateMustMatchEverySelectedSlot() {
        DoorSocket ceiling = new DoorSocket("ceiling", new IntVector3(1, 4, 1), new IntVector3(3, 1, 3), Direction3.UP, Set.of(), List.of());
        DoorSocket floor = new DoorSocket("floor", new IntVector3(1, 0, 1), new IntVector3(3, 1, 3), Direction3.DOWN, Set.of(), List.of());
        DoorTemplate upDoor = new DoorTemplate("hatch", new IntVector3(3, 1, 3), Set.of(), List.of(), List.of(), new DoorGateway(new IntVector3(0, 0, 0), new IntVector3(3, 1, 3), Direction3.UP), Path.of("door.nbt"));

        List<String> conflicts = SlotMultiEditMatcher.doorConflicts(List.of(ceiling, floor), upDoor, validStatus(upDoor));

        assertFalse(conflicts.isEmpty());
        assertTrue(conflicts.stream().anyMatch(reason -> reason.startsWith("floor:")), conflicts.toString());
    }

    @Test
    void featureCandidateMustFitEverySelectedSlot() {
        RoomFeatureSlot small = new RoomFeatureSlot("small", new IntVector3(0, 0, 0), new IntVector3(2, 2, 2), Direction3.NORTH, List.of());
        RoomFeatureSlot large = new RoomFeatureSlot("large", new IntVector3(0, 0, 0), new IntVector3(5, 5, 5), Direction3.NORTH, List.of());
        FeatureTemplate feature = new FeatureTemplate("statue", new IntVector3(4, 4, 4), Set.of(), Path.of("feature.nbt"));

        List<String> conflicts = SlotMultiEditMatcher.featureConflicts(List.of(small, large), feature, validStatus(feature));

        assertFalse(conflicts.isEmpty());
        assertTrue(conflicts.stream().anyMatch(reason -> reason.startsWith("small:")), conflicts.toString());
        assertFalse(conflicts.stream().anyMatch(reason -> reason.startsWith("large:")), conflicts.toString());
    }

    @Test
    void invalidVisibleTemplateReportsLoadStatusInsteadOfMatching() {
        FeatureTemplate feature = new FeatureTemplate("broken", new IntVector3(1, 1, 1), Set.of(), Path.of("feature.nbt"));
        RoomFeatureSlot slot = new RoomFeatureSlot("slot", new IntVector3(0, 0, 0), new IntVector3(5, 5, 5), Direction3.NORTH, List.of());
        TemplateLoadStatus<FeatureTemplate> status = new TemplateLoadStatus<>(feature, "broken", Path.of("broken"), false, List.of("missing feature.nbt"), List.of());

        List<String> conflicts = SlotMultiEditMatcher.featureConflicts(List.of(slot), feature, status);

        assertTrue(conflicts.contains("missing feature.nbt"));
    }

    private static <T> TemplateLoadStatus<T> validStatus(T template) {
        return new TemplateLoadStatus<>(template, "id", Path.of("id"), true, List.of(), List.of());
    }
}
