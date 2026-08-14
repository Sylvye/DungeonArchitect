package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TemplateDiagnosticsTest {
    @Test
    void warnsForRoomSlotsWithNoAssignedTemplates() {
        RoomTemplate room = new RoomTemplate(
            "empty_slots",
            RoomCategory.GENERIC,
            10,
            Set.of(),
            new IntVector3(5, 5, 5),
            null,
            List.of(
                new DoorSocket("empty_door", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 2, 3),
                new DoorSocket("virtual_door", new IntVector3(1, 1, 4), Direction3.SOUTH, SocketType.STANDARD, 2, 3)
                    .withEntries(List.of(new DoorSlotEntry(DoorSlotEntry.EMPTY, 1)))
            ),
            List.of(),
            List.of(new RoomFeatureSlot(
                "virtual_feature",
                new IntVector3(1, 1, 1),
                new IntVector3(2, 2, 2),
                Direction3.NORTH,
                List.of(new FeatureSlotEntry(FeatureSlotEntry.EMPTY, 1))
            )),
            Path.of("room.nbt")
        );

        TemplateValidationResult result = TemplateDiagnostics.analyzeRoom(room, null, null, null);

        assertEquals(3, result.warnings().size(), result.warnings().toString());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("door slot empty_door has no door template assigned")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("door slot virtual_door has no door template assigned")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("feature slot virtual_feature only selects empty")));
    }

    @Test
    void warnsForDoorFeatureSlotsWithNoAssignedFeatures() {
        DoorTemplate door = new DoorTemplate(
            "arch",
            new IntVector3(5, 5, 2),
            Set.of(),
            List.of(),
            List.of(new RoomFeatureSlot(
                "trim",
                new IntVector3(1, 1, 1),
                new IntVector3(2, 2, 1),
                Direction3.NORTH,
                List.of(new FeatureSlotEntry(FeatureSlotEntry.EMPTY, 1))
            )),
            new DoorGateway(new IntVector3(1, 1, 0), new IntVector3(2, 3, 1), Direction3.NORTH),
            Path.of("door.nbt")
        );

        TemplateValidationResult result = TemplateDiagnostics.analyzeDoor(door, null);

        assertEquals(1, result.warnings().size(), result.warnings().toString());
        assertTrue(result.warnings().getFirst().contains("feature slot trim only selects empty"));
    }

    @Test
    void warnsWhenANestedFeatureSlotOnlySelectsEmpty() {
        FeatureTemplate feature = new FeatureTemplate(
            "random_chest",
            new IntVector3(1, 1, 1),
            Set.of(),
            List.of(),
            List.of(new RoomFeatureSlot(
                "chest",
                IntVector3.ZERO,
                new IntVector3(1, 1, 1),
                Direction3.NORTH,
                List.of(new FeatureSlotEntry(FeatureSlotEntry.EMPTY, 1))
            )),
            java.util.Map.of(),
            Path.of("feature.nbt")
        );

        TemplateValidationResult result = TemplateDiagnostics.analyzeFeature(feature);

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().contains("random_chest: feature slot chest only selects empty"));
        assertTrue(result.warnings().getFirst().contains("chest feature with a loot binding"));
    }
}
