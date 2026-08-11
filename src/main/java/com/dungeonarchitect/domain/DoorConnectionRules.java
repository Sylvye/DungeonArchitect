package com.dungeonarchitect.domain;

import java.util.LinkedHashSet;
import java.util.Set;

public record DoorConnectionRules(Set<String> allowedTags, Set<String> deniedTags, Set<String> allowedRoomTags, Set<String> deniedRoomTags, boolean mustConnect) {
    public static final DoorConnectionRules DEFAULT = new DoorConnectionRules(Set.of(), Set.of(), Set.of(), Set.of(), false);

    public DoorConnectionRules {
        allowedTags = clean(allowedTags);
        deniedTags = clean(deniedTags);
        allowedRoomTags = clean(allowedRoomTags);
        deniedRoomTags = clean(deniedRoomTags);
    }

    public DoorConnectionRules(Set<String> allowedTags, Set<String> deniedTags, boolean mustConnect) {
        this(allowedTags, deniedTags, Set.of(), Set.of(), mustConnect);
    }

    public boolean hasDoorTagPolicy() {
        return !allowedTags.isEmpty() || !deniedTags.isEmpty();
    }

    public boolean hasRoomTagPolicy() {
        return !allowedRoomTags.isEmpty() || !deniedRoomTags.isEmpty();
    }

    public boolean hasTagPolicy() {
        return hasDoorTagPolicy();
    }

    public boolean isDefault() {
        return !hasDoorTagPolicy() && !hasRoomTagPolicy() && !mustConnect;
    }

    public String rejectionReason(Set<String> oppositeTags) {
        if (containsAny(deniedTags, oppositeTags)) {
            return "denies opposite-door tags " + deniedTags;
        }
        if (!allowedTags.isEmpty() && !containsAny(allowedTags, oppositeTags)) {
            return "requires one of " + allowedTags + " but opposite door has " + oppositeTags;
        }
        return null;
    }

    public String roomRejectionReason(Set<String> oppositeRoomTags) {
        if (containsAny(deniedRoomTags, oppositeRoomTags)) {
            return "denies opposite-room tags " + deniedRoomTags;
        }
        if (!allowedRoomTags.isEmpty() && !containsAny(allowedRoomTags, oppositeRoomTags)) {
            return "requires one of room tags " + allowedRoomTags + " but opposite room has " + oppositeRoomTags;
        }
        return null;
    }

    private static Set<String> clean(Set<String> tags) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isBlank()) {
                    cleaned.add(tag.trim());
                }
            }
        }
        return Set.copyOf(cleaned);
    }

    private static boolean containsAny(Set<String> first, Set<String> second) {
        return first.stream().anyMatch(tag -> second.stream().anyMatch(tag::equalsIgnoreCase));
    }
}
