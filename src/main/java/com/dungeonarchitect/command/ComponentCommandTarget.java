package com.dungeonarchitect.command;

import com.dungeonarchitect.authoring.AuthoringSession;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

record ComponentCommandTarget(String type, String id) {
    private static final List<String> TYPES = List.of("door", "marker", "feature");

    static ComponentCommandTarget resolve(String[] args, int startIndex, Optional<AuthoringSession.SelectedComponent> selected) {
        int remaining = args.length - startIndex;
        if (remaining <= 0) {
            return selected
                .map(component -> new ComponentCommandTarget(component.type(), component.id()))
                .orElseThrow(() -> new IllegalArgumentException("Select a component first, or provide type and id."));
        }

        String type = normalizeType(args[startIndex]);
        if (remaining == 1) {
            return selected
                .filter(component -> component.type().equals(type))
                .map(component -> new ComponentCommandTarget(type, component.id()))
                .orElseThrow(() -> new IllegalArgumentException("Provide a " + type + " id, or select a " + type + " first."));
        }

        return new ComponentCommandTarget(type, args[startIndex + 1]);
    }

    static Optional<String> renameValue(String[] args, int startIndex) {
        return args.length > startIndex + 2 ? Optional.of(args[startIndex + 2]) : Optional.empty();
    }

    static String normalizeType(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Component type must be door, marker, or feature");
        }
        return normalized;
    }

    static List<String> types() {
        return TYPES;
    }
}
