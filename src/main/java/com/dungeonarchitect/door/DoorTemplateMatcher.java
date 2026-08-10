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
        boolean facingCanMatch = false;
        String lastSizeReason = null;
        for (Rotation rotation : Rotation.values()) {
            if (template.gateway().facing().rotateY(rotation) != slot.facing()) {
                continue;
            }
            facingCanMatch = true;
            IntVector3 rotatedDoorSize = rotation.rotateSize(template.size());
            if (!slot.size().equals(rotatedDoorSize)) {
                lastSizeReason = "door bounds size " + template.size() + " rotates to " + rotatedDoorSize + ", expected slot size " + slot.size();
                continue;
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
        if (!facingCanMatch) {
            return DoorTemplateMatchResult.rejected("gateway facing " + template.gateway().facing() + " cannot rotate around Y to slot facing " + slot.facing());
        }
        return DoorTemplateMatchResult.rejected(lastSizeReason);
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
