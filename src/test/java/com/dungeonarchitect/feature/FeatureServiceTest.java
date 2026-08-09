package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.HashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void weightedSelectionIsDeterministicForSameSeed() {
        List<FeatureSlotEntry> entries = List.of(
            new FeatureSlotEntry("empty", 1),
            new FeatureSlotEntry("chest", 3),
            new FeatureSlotEntry("trap", 2)
        );

        assertEquals(
            FeatureService.select(entries, new Random(1234L)),
            FeatureService.select(entries, new Random(1234L))
        );
    }

    @Test
    void weightedSelectionIncludesEmptyAtEqualWeight() {
        List<FeatureSlotEntry> entries = List.of(
            new FeatureSlotEntry("empty", 1),
            new FeatureSlotEntry("chest", 1)
        );

        Set<String> selected = new HashSet<>();
        Random random = new Random(12345L);
        for (int roll = 0; roll < 100; roll++) {
            selected.add(FeatureService.select(entries, random).featureId());
        }

        assertTrue(selected.contains("empty"));
        assertTrue(selected.contains("chest"));
    }

    @Test
    void rollReportsNoEntriesAndEmptySelection() {
        FeatureService service = new FeatureService(new FeatureTemplateRegistry(tempDir, null), null);

        var noEntries = service.roll(new RoomFeatureSlot("slot", new IntVector3(0, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of()), new Random(0));
        assertEquals(FeatureService.FeatureRollStatus.NO_ENTRIES, noEntries.status());

        var empty = service.roll(new RoomFeatureSlot("slot", new IntVector3(0, 0, 0), new IntVector3(1, 1, 1), Direction3.NORTH, List.of(new FeatureSlotEntry("empty", 1))), new Random(0));
        assertEquals(FeatureService.FeatureRollStatus.EMPTY, empty.status());
    }

    @Test
    void rollReportsUnknownAndSizeMismatchAndSelected() throws Exception {
        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);
        saveFeature("chest", new IntVector3(2, 2, 2));
        saveFeature("large", new IntVector3(5, 5, 5));
        registry.reload();
        FeatureService service = new FeatureService(registry, null);

        var unknown = service.roll(slotWith("missing"), new Random(0));
        assertEquals(FeatureService.FeatureRollStatus.UNKNOWN_FEATURE, unknown.status());

        var mismatch = service.roll(slotWith("large"), new Random(0));
        assertEquals(FeatureService.FeatureRollStatus.SIZE_MISMATCH, mismatch.status());

        var selected = service.roll(slotWith("chest"), new Random(0));
        assertEquals(FeatureService.FeatureRollStatus.SELECTED, selected.status());
        assertEquals(Rotation.NONE, selected.rotation());
    }

    private RoomFeatureSlot slotWith(String featureId) {
        return new RoomFeatureSlot("slot", new IntVector3(0, 0, 0), new IntVector3(3, 3, 3), Direction3.NORTH, List.of(new FeatureSlotEntry(featureId, 1)));
    }

    private void saveFeature(String id, IntVector3 size) throws Exception {
        Path directory = tempDir.resolve(id);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate(id, size, Set.of(), directory.resolve("feature.nbt")), directory);
    }
}
