package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureSlotEntry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureServiceTest {
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
}
