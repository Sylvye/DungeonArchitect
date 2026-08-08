package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureMatcherTest {
    @Test
    void matchesExactAndYawSwappedSizes() {
        RoomFeatureSlot slot = new RoomFeatureSlot("slot", new IntVector3(0, 0, 0), new IntVector3(2, 3, 4), Direction3.NORTH);

        assertEquals(Rotation.NONE, FeatureMatcher.rotationFor(slot.size(), new IntVector3(2, 3, 4)));
        assertEquals(Rotation.CLOCKWISE_90, FeatureMatcher.rotationFor(slot.size(), new IntVector3(4, 3, 2)));
        assertNull(FeatureMatcher.rotationFor(slot.size(), new IntVector3(2, 4, 4)));
        assertEquals(Rotation.NONE, FeatureMatcher.rotationFor(slot.size(), new IntVector3(1, 3, 4)));
        assertTrue(FeatureMatcher.matches(slot, new FeatureTemplate("match", new IntVector3(4, 3, 2), Set.of(), Path.of("feature.nbt"))));
    }

    @Test
    void fitsUndersizedFeaturesAndCentersTowardMinimum() {
        IntVector3 slotSize = new IntVector3(5, 4, 6);
        IntVector3 featureSize = new IntVector3(2, 2, 3);

        assertEquals(List.of(Rotation.NONE, Rotation.CLOCKWISE_90), FeatureMatcher.rotationsFor(slotSize, featureSize));
        assertEquals(new IntVector3(1, 1, 1), FeatureMatcher.placementOffset(slotSize, featureSize));
        assertEquals(new IntVector3(1, 1, 2), FeatureMatcher.placementOffset(slotSize, Rotation.CLOCKWISE_90.rotateSize(featureSize)));
    }

    @Test
    void rejectsOversizedFeatures() {
        IntVector3 slotSize = new IntVector3(2, 3, 4);

        assertNull(FeatureMatcher.rotationFor(slotSize, new IntVector3(5, 3, 2)));
        assertNull(FeatureMatcher.rotationFor(slotSize, new IntVector3(2, 4, 2)));
    }
}
