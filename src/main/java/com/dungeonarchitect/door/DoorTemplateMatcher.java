package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.Rotation;

public final class DoorTemplateMatcher {
    private DoorTemplateMatcher() {
    }

    public static boolean matches(DoorSocket slot, DoorTemplate template) {
        return match(slot, template).matched();
    }

    public static DoorTemplateMatchResult match(DoorSocket slot, DoorTemplate template) {
        if (template.gateway() == null) {
            return DoorTemplateMatchResult.rejected("missing gateway");
        }
        Rotation rotation = rotationTo(template.gateway().facing(), slot.facing());
        IntVector3 rotatedDoorSize = rotation.rotateSize(template.size());
        if (!slot.size().equals(rotatedDoorSize)) {
            return DoorTemplateMatchResult.rejected("door bounds size " + template.size() + " rotates to " + rotatedDoorSize + ", expected slot size " + slot.size());
        }
        if (slot.tags().isEmpty() || template.tags().isEmpty()) {
            return DoorTemplateMatchResult.matched(rotation, "matched");
        }
        boolean tagsMatch = slot.tags().stream().anyMatch(tag -> template.tags().stream().anyMatch(tag::equalsIgnoreCase));
        if (!tagsMatch) {
            return DoorTemplateMatchResult.rejected("tags do not overlap; slot=" + slot.tags() + " door=" + template.tags());
        }
        return DoorTemplateMatchResult.matched(rotation, "matched");
    }

    private static Rotation rotationTo(com.dungeonarchitect.domain.Direction3 from, com.dungeonarchitect.domain.Direction3 to) {
        for (Rotation rotation : Rotation.values()) {
            if (from.rotateY(rotation) == to) {
                return rotation;
            }
        }
        throw new IllegalArgumentException("Cannot rotate " + from + " to " + to);
    }

    public record DoorTemplateMatchResult(boolean matched, Rotation rotation, String reason) {
        private static DoorTemplateMatchResult matched(Rotation rotation, String reason) {
            return new DoorTemplateMatchResult(true, rotation, reason);
        }

        private static DoorTemplateMatchResult rejected(String reason) {
            return new DoorTemplateMatchResult(false, null, reason);
        }
    }
}
