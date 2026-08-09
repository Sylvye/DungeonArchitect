package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomTemplateValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsDuplicateDoorsAndOutOfBoundsPositions() throws Exception {
        Path nbt = tempDir.resolve("room.nbt");
        Files.writeString(nbt, "fake");
        RoomTemplate template = new RoomTemplate(
            "bad",
            RoomCategory.COMBAT,
            1,
            Set.of(),
            new IntVector3(3, 3, 3),
            null,
            List.of(
                new DoorSocket("door", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("door", new IntVector3(9, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)
            ),
            List.of(),
            List.of(),
            nbt
        );

        var result = new RoomTemplateValidator().validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("duplicate door")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("outside room bounds")));
        assertTrue(result.issues().stream()
            .map(TemplateValidationResult.ValidationIssue::localPosition)
            .anyMatch(new IntVector3(9, 1, 0)::equals));
    }

    @Test
    void validationIssuesIncludeMarkerAndFeaturePositions() throws Exception {
        Path nbt = tempDir.resolve("room.nbt");
        Files.writeString(nbt, "fake");
        IntVector3 markerPosition = new IntVector3(0, 4, 0);
        IntVector3 featurePosition = new IntVector3(-1, 1, 1);
        RoomTemplate template = new RoomTemplate(
            "bad_components",
            RoomCategory.COMBAT,
            1,
            Set.of(),
            new IntVector3(3, 3, 3),
            null,
            List.of(),
            List.of(new RoomMarker("spawn", "player", markerPosition)),
            List.of(new RoomFeatureSlot("feature_1", "chest", "chest", featurePosition, Direction3.SOUTH)),
            nbt
        );

        var result = new RoomTemplateValidator().validate(template);

        assertFalse(result.valid());
        assertEquals(List.of(markerPosition, featurePosition), result.issues().stream().map(TemplateValidationResult.ValidationIssue::localPosition).toList());
    }

    @Test
    void rejectsUnknownAndNonFittingFeatureEntries() throws Exception {
        Path featureRoot = tempDir.resolve("features");
        Path large = featureRoot.resolve("large");
        Files.createDirectories(large);
        Files.writeString(large.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("large", new IntVector3(5, 5, 5), Set.of(), large.resolve("feature.nbt")), large);
        FeatureTemplateRegistry featureRegistry = new FeatureTemplateRegistry(featureRoot, null);
        featureRegistry.reload();

        Path nbt = tempDir.resolve("room.nbt");
        Files.writeString(nbt, "fake");
        RoomTemplate template = new RoomTemplate(
            "bad_features",
            RoomCategory.COMBAT,
            1,
            Set.of(),
            new IntVector3(6, 6, 6),
            null,
            List.of(),
            List.of(),
            List.of(
                new RoomFeatureSlot("unknown", new IntVector3(1, 1, 1), new IntVector3(2, 2, 2), Direction3.NORTH, List.of(new FeatureSlotEntry("missing", 1))),
                new RoomFeatureSlot("mismatch", new IntVector3(1, 1, 1), new IntVector3(2, 2, 2), Direction3.NORTH, List.of(new FeatureSlotEntry("large", 1)))
            ),
            nbt
        );

        var result = new RoomTemplateValidator(null, featureRegistry).validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("unknown feature missing")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("does not match feature large")));
    }
}
