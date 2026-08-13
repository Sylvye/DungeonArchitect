package com.dungeonarchitect.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

enum ComponentCommandContext {
    ROOM(Map.of(
        "door", Set.of("select", "remove", "bounds", "rename", "rotate", "face"),
        "marker", Set.of("select", "remove", "bounds", "rename"),
        "feature", Set.of("select", "remove", "bounds", "rename", "rotate", "face")
    )),
    DOOR(Map.of(
        "gateway", Set.of("select", "bounds", "rotate", "face"),
        "marker", Set.of("select", "remove", "bounds", "rename"),
        "feature", Set.of("select", "remove", "bounds", "rename", "rotate", "face")
    )),
    FEATURE(Map.of(
        "marker", Set.of("select", "remove", "bounds", "rename")
    ));

    private final Map<String, Set<String>> actionsByType;

    ComponentCommandContext(Map<String, Set<String>> actionsByType) {
        this.actionsByType = actionsByType;
    }

    List<String> types() {
        return actionsByType.keySet().stream().sorted().toList();
    }

    List<String> actions() {
        return actionsByType.values().stream()
            .flatMap(Set::stream)
            .distinct()
            .sorted()
            .toList();
    }

    boolean hasComponents() {
        return !actionsByType.isEmpty();
    }

    void requireType(String type) {
        if (!actionsByType.containsKey(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(displayName() + " templates do not have " + type + " components");
        }
    }

    void requireAction(String action, String type) {
        requireType(type);
        if (!actionsByType.get(type.toLowerCase(Locale.ROOT)).contains(action.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Cannot " + action + " a " + type + " component in a " + displayName().toLowerCase(Locale.ROOT) + " template");
        }
    }

    String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }
}
