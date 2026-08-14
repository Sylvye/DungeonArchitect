package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.domain.RoomMarker;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical naming rules shared by authoring, persistence, and validation. */
public final class IdentityRules {
    private IdentityRules() {}

    public static String canonical(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Name is required");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static void requireRoomComponentAvailable(String name, Collection<DoorSocket> doors, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, String excludedName) {
        requireAvailable(name, componentNames(doors, markers, features, false), excludedName);
    }

    public static void requireDoorComponentAvailable(String name, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, String excludedName) {
        requireAvailable(name, componentNames(java.util.List.of(), markers, features, true), excludedName);
    }

    public static void requireFeatureMarkerAvailable(String name, Collection<RoomMarker> markers, String excludedName) {
        requireAvailable(name, componentNames(java.util.List.of(), markers, java.util.List.of(), false), excludedName);
    }

    public static void requireFeatureComponentAvailable(String name, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, String excludedName) {
        requireAvailable(name, componentNames(java.util.List.of(), markers, features, false), excludedName);
    }

    public static void validateRoomComponents(String owner, Collection<DoorSocket> doors, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, TemplateValidationResult result) {
        validate(owner, componentNames(doors, markers, features, false), result);
    }

    public static void validateDoorComponents(String owner, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, TemplateValidationResult result) {
        validate(owner, componentNames(java.util.List.of(), markers, features, true), result);
    }

    public static void validateFeatureMarkers(String owner, Collection<RoomMarker> markers, TemplateValidationResult result) {
        validate(owner, componentNames(java.util.List.of(), markers, java.util.List.of(), false), result);
    }

    public static void validateFeatureComponents(String owner, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, TemplateValidationResult result) {
        validate(owner, componentNames(java.util.List.of(), markers, features, false), result);
    }

    public static void assertRoomComponents(Collection<DoorSocket> doors, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features) {
        assertUnique(componentNames(doors, markers, features, false));
    }

    public static void assertDoorComponents(Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features) {
        assertUnique(componentNames(java.util.List.of(), markers, features, true));
    }

    public static void assertFeatureMarkers(Collection<RoomMarker> markers) {
        assertUnique(componentNames(java.util.List.of(), markers, java.util.List.of(), false));
    }

    public static void assertFeatureComponents(Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features) {
        assertUnique(componentNames(java.util.List.of(), markers, features, false));
    }

    private static void requireAvailable(String name, List<Name> existing, String excludedName) {
        String canonical = canonical(name);
        String excluded = excludedName == null ? null : canonical(excludedName);
        existing.stream().filter(value -> value.canonical.equals(canonical) && !canonical.equals(excluded)).findFirst()
            .ifPresent(value -> { throw new IllegalArgumentException("Name already exists: " + value.label); });
    }

    private static void validate(String owner, List<Name> names, TemplateValidationResult result) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Name entry : names) {
            String prior = seen.putIfAbsent(entry.canonical, entry.label);
            if (prior != null) result.add(owner + ": component name collision between " + prior + " and " + entry.label);
        }
    }

    private static void assertUnique(List<Name> names) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Name entry : names) {
            String prior = seen.putIfAbsent(entry.canonical, entry.label);
            if (prior != null) throw new IllegalArgumentException("Component name collision between " + prior + " and " + entry.label);
        }
    }

    private static List<Name> componentNames(Collection<DoorSocket> doors, Collection<RoomMarker> markers, Collection<RoomFeatureSlot> features, boolean reserveGateway) {
        List<Name> names = new ArrayList<>();
        if (reserveGateway) names.add(new Name("gateway", "gateway"));
        for (DoorSocket door : doors) add(names, door.id(), "door " + door.id());
        for (RoomMarker marker : markers) add(names, marker.name(), "marker " + marker.name());
        for (RoomFeatureSlot feature : features) add(names, feature.id(), "feature slot " + feature.id());
        return names;
    }

    private static void add(List<Name> names, String id, String label) {
        names.add(new Name(canonical(id), label));
    }

    private record Name(String canonical, String label) {}
}
