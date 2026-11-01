package com.dungeonarchitect.command;

import com.dungeonarchitect.domain.RoomMarker;
import java.util.*;

/** Shared, side-effect-free argument completion. Never falls back to player names. */
public final class CommandSuggestions {
    private CommandSuggestions() { }
    public static List<String> matching(String input, Collection<String> choices) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return choices.stream().filter(Objects::nonNull).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public static List<String> markerPurposes(boolean room) {
        List<String> result = new ArrayList<>();
        for (RoomMarker.Purpose purpose : RoomMarker.Purpose.values()) if (room || purpose != RoomMarker.Purpose.PLAYER_SPAWN) result.add(purpose.id());

        return result;
    }
    public static List<String> newId(String base, Collection<String> existing) {
        Set<String> used = new HashSet<>(); existing.forEach(value -> used.add(value.toLowerCase(Locale.ROOT)));
        String value = base; int next = 2;
        while (used.contains(value.toLowerCase(Locale.ROOT))) value = base + "_" + next++;
        return List.of(value);
    }
    /** Complete only the current comma-list element, retaining preceding elements. */
    public static List<String> list(String current, Collection<String> choices, Collection<String> earlier) {
        int split = current.lastIndexOf(','); String head = current.substring(0, split + 1), tail = current.substring(split + 1);
        Set<String> selected = new HashSet<>(); earlier.forEach(v -> selected.add(v.toLowerCase(Locale.ROOT)));
        if (!head.isEmpty()) Arrays.stream(head.split(",")).forEach(v -> selected.add(v.toLowerCase(Locale.ROOT)));
        return matching(tail, choices).stream().filter(v -> !selected.contains(v.toLowerCase(Locale.ROOT))).map(v -> head + v).toList();
    }
}
