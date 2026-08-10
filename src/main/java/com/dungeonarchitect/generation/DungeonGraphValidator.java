package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.door.DoorTemplateMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonGraphValidator {
    public List<String> validate(DungeonGraph graph, Collection<RoomTemplate> templates) {
        return validate(graph, templates, List.of());
    }

    public List<String> validate(DungeonGraph graph, Collection<RoomTemplate> templates, Collection<DoorTemplate> doorTemplates) {
        Map<String, RoomTemplate> byId = new HashMap<>();
        for (RoomTemplate template : templates) {
            byId.put(template.id(), template);
        }
        Map<String, DoorTemplate> doorsById = new HashMap<>();
        for (DoorTemplate template : doorTemplates) {
            doorsById.put(template.id(), template);
        }
        List<String> errors = new ArrayList<>();
        validateEdges(graph, byId, doorsById, errors);
        validateBounds(graph, errors);
        return errors;
    }

    private void validateEdges(DungeonGraph graph, Map<String, RoomTemplate> templates, Map<String, DoorTemplate> doorTemplates, List<String> errors) {
        for (DungeonEdge edge : graph.edges()) {
            DungeonNode from = graph.nodes().get(edge.fromNode());
            DungeonNode to = graph.nodes().get(edge.toNode());
            RoomTemplate fromTemplate = templates.get(from.templateId());
            RoomTemplate toTemplate = templates.get(to.templateId());
            if (fromTemplate == null || toTemplate == null) {
                errors.add("Edge references missing template: " + edge);
                continue;
            }
            DoorSocket fromDoor = findDoor(fromTemplate, edge.fromDoorId(), errors);
            DoorSocket toDoor = findDoor(toTemplate, edge.toDoorId(), errors);
            if (fromDoor == null || toDoor == null) {
                continue;
            }
            if (edge.fromDoorTemplateId() != null || edge.toDoorTemplateId() != null) {
                validateTemplateDoorEdge(edge, from, to, fromDoor, toDoor, doorTemplates, errors);
                continue;
            }
            var fromFacing = from.transform().transformFacing(fromDoor.facing());
            var toFacing = to.transform().transformFacing(toDoor.facing());
            var fromPosition = from.transform().transformLocal(fromDoor.position());
            var toPosition = to.transform().transformLocal(toDoor.position());
            if (toFacing != fromFacing.opposite()) {
                errors.add("Edge " + edge + " has non-opposite facings: " + fromFacing + " and " + toFacing);
            }
            var fromBounds = DoorGeometry.transformedBounds(fromDoor, from.transform());
            var toBounds = DoorGeometry.transformedBounds(toDoor, to.transform());
            if (!fromBounds.size().equals(toBounds.size())) {
                errors.add("Edge " + edge + " has mismatched door aperture sizes: " + fromBounds.size() + " and " + toBounds.size());
                continue;
            }
            var expectedToBounds = DoorGeometry.shifted(fromBounds, fromFacing.vector());
            if (!toBounds.equals(expectedToBounds)) {
                errors.add("Edge " + edge + " door rectangles are not aligned: expected " + expectedToBounds + " actual " + toBounds + " delta=" + DoorGeometry.delta(expectedToBounds, toBounds));
            }
        }
    }

    private void validateTemplateDoorEdge(DungeonEdge edge, DungeonNode from, DungeonNode to, DoorSocket fromDoor, DoorSocket toDoor, Map<String, DoorTemplate> doorTemplates, List<String> errors) {
        DoorTemplate fromTemplate = doorTemplates.get(edge.fromDoorTemplateId());
        DoorTemplate toTemplate = doorTemplates.get(edge.toDoorTemplateId());
        if (fromTemplate == null || toTemplate == null) {
            errors.add("Edge references missing door template: " + edge);
            return;
        }
        var fromMatch = DoorTemplateMatcher.match(fromDoor, fromTemplate);
        if (!fromMatch.matched()) {
            errors.add("Edge " + edge + " from door template " + fromTemplate.id() + " does not match slot " + fromDoor.id() + ": " + fromMatch.reason());
            return;
        }
        var toMatch = DoorTemplateMatcher.match(toDoor, toTemplate);
        if (!toMatch.matched()) {
            errors.add("Edge " + edge + " to door template " + toTemplate.id() + " does not match slot " + toDoor.id() + ": " + toMatch.reason());
            return;
        }
        var fromDoorTransform = DoorGeometry.doorTransform(fromDoor, fromTemplate, from.transform());
        var toDoorTransform = DoorGeometry.doorTransform(toDoor, toTemplate, to.transform());
        var fromFacing = DoorGeometry.gatewayFacing(fromTemplate, fromDoorTransform);
        var toFacing = DoorGeometry.gatewayFacing(toTemplate, toDoorTransform);
        if (toFacing != fromFacing.opposite()) {
            errors.add("Edge " + edge + " has non-opposite gateway facings: " + fromFacing + " and " + toFacing);
        }
        var fromBounds = DoorGeometry.transformedBounds(fromTemplate.gateway(), fromDoorTransform);
        var toBounds = DoorGeometry.transformedBounds(toTemplate.gateway(), toDoorTransform);
        if (!fromBounds.size().equals(toBounds.size())) {
            errors.add("Edge " + edge + " has mismatched gateway sizes: " + fromBounds.size() + " and " + toBounds.size());
            return;
        }
        var expectedToBounds = DoorGeometry.shifted(fromBounds, fromFacing.vector());
        if (!toBounds.equals(expectedToBounds)) {
            errors.add("Edge " + edge + " gateways are not aligned: expected " + expectedToBounds + " actual " + toBounds + " delta=" + DoorGeometry.delta(expectedToBounds, toBounds));
        }
    }

    private DoorSocket findDoor(RoomTemplate template, String doorId, List<String> errors) {
        return template.doors().stream()
            .filter(door -> door.id().equals(doorId))
            .findFirst()
            .orElseGet(() -> {
                errors.add("Template " + template.id() + " has no door " + doorId);
                return null;
            });
    }

    private void validateBounds(DungeonGraph graph, List<String> errors) {
        for (int i = 0; i < graph.nodes().size(); i++) {
            for (int j = i + 1; j < graph.nodes().size(); j++) {
                var first = graph.nodes().get(i).transform().transformedBounds();
                var second = graph.nodes().get(j).transform().transformedBounds();
                if (first.intersects(second)) {
                    errors.add("Room bounds overlap: node " + i + " " + first + " and node " + j + " " + second);
                }
            }
        }
    }
}
