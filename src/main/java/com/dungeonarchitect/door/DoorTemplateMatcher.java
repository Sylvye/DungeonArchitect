package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.template.DiagnosticText;

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
            if (!fitsWithin(rotatedDoorSize, slot.size())) {
                lastSizeReason = "Door footprint is " + DiagnosticText.size(rotatedDoorSize)
                    + " after rotation, but slot " + slot.id() + " allows " + DiagnosticText.size(slot.size()) + ".";
                continue;
            }
            if (slot.tags().isEmpty() || template.tags().isEmpty()) {
                return DoorTemplateMatchResult.matched(rotation, "matched");
            }
            boolean tagsMatch = slot.tags().stream().anyMatch(tag -> template.tags().stream().anyMatch(tag::equalsIgnoreCase));
            if (!tagsMatch) {
                return DoorTemplateMatchResult.rejected("Door tags " + template.tags() + " do not overlap slot tags " + slot.tags() + ".");
            }
            return DoorTemplateMatchResult.matched(rotation, "matched");
        }
        if (!facingCanMatch) {
            return DoorTemplateMatchResult.rejected("Gateway faces " + template.gateway().facing()
                + ", but slot faces " + slot.facing() + ". Door templates only rotate around Y.");
        }
        return DoorTemplateMatchResult.rejected(lastSizeReason);
    }

    private static boolean fitsWithin(IntVector3 size, IntVector3 slotSize) {
        return size.x() <= slotSize.x() && size.y() <= slotSize.y() && size.z() <= slotSize.z();
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
