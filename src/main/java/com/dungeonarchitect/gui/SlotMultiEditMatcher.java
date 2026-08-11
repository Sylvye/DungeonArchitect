package com.dungeonarchitect.gui;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomFeatureSlot;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import com.dungeonarchitect.feature.FeatureMatcher;
import com.dungeonarchitect.template.TemplateLoadStatus;

import java.util.ArrayList;
import java.util.List;

final class SlotMultiEditMatcher {
    private SlotMultiEditMatcher() {
    }

    static List<String> doorConflicts(List<DoorSocket> slots, DoorTemplate template, TemplateLoadStatus<DoorTemplate> status) {
        List<String> conflicts = loadStatusConflicts(status);
        if (!conflicts.isEmpty()) {
            return conflicts;
        }
        for (DoorSocket slot : slots) {
            DoorTemplateMatcher.DoorTemplateMatchResult match = DoorTemplateMatcher.match(slot, template);
            if (!match.matched()) {
                conflicts.add(slot.id() + ": " + match.reason());
            }
        }
        return conflicts;
    }

    static List<String> featureConflicts(List<RoomFeatureSlot> slots, FeatureTemplate template, TemplateLoadStatus<FeatureTemplate> status) {
        List<String> conflicts = loadStatusConflicts(status);
        if (!conflicts.isEmpty()) {
            return conflicts;
        }
        for (RoomFeatureSlot slot : slots) {
            FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(slot, template);
            if (!match.matched()) {
                conflicts.add(slot.id() + ": " + match.reason());
            }
        }
        return conflicts;
    }

    private static <T> List<String> loadStatusConflicts(TemplateLoadStatus<T> status) {
        List<String> conflicts = new ArrayList<>();
        if (status == null || status.valid()) {
            return conflicts;
        }
        if (!status.loadable()) {
            conflicts.add("Template could not be loaded.");
            return conflicts;
        }
        status.errors().stream().limit(3).forEach(conflicts::add);
        if (conflicts.isEmpty()) {
            conflicts.add("Template is invalid.");
        }
        return conflicts;
    }
}
