package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.Rotation;

import java.util.List;

public final class FeatureMatcher {
    private FeatureMatcher() {
    }

    public static boolean matches(RoomFeatureSlot slot, FeatureTemplate feature) {
        return rotationFor(slot.size(), feature.size()) != null;
    }

    public static Rotation rotationFor(IntVector3 slotSize, IntVector3 featureSize) {
        if (fitsWithoutRotation(slotSize, featureSize)) {
            return Rotation.NONE;
        }
        if (fitsWithoutRotation(slotSize, Rotation.CLOCKWISE_90.rotateSize(featureSize))) {
            return Rotation.CLOCKWISE_90;
        }
        return null;
    }

    public static List<Rotation> rotationsFor(IntVector3 slotSize, IntVector3 featureSize) {
        java.util.ArrayList<Rotation> rotations = new java.util.ArrayList<>();
        if (fitsWithoutRotation(slotSize, featureSize)) {
            rotations.add(Rotation.NONE);
        }
        if (fitsWithoutRotation(slotSize, Rotation.CLOCKWISE_90.rotateSize(featureSize))) {
            rotations.add(Rotation.CLOCKWISE_90);
        }
        return List.copyOf(rotations);
    }

    public static IntVector3 placementOffset(IntVector3 slotSize, IntVector3 rotatedFeatureSize) {
        if (!fitsWithoutRotation(slotSize, rotatedFeatureSize)) {
            throw new IllegalArgumentException("Feature size " + rotatedFeatureSize + " does not fit slot " + slotSize);
        }
        return new IntVector3(
            (slotSize.x() - rotatedFeatureSize.x()) / 2,
            (slotSize.y() - rotatedFeatureSize.y()) / 2,
            (slotSize.z() - rotatedFeatureSize.z()) / 2
        );
    }

    private static boolean fitsWithoutRotation(IntVector3 slotSize, IntVector3 featureSize) {
        return featureSize.x() <= slotSize.x()
            && featureSize.y() <= slotSize.y()
            && featureSize.z() <= slotSize.z();
    }
}
