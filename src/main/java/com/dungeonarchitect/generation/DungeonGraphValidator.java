package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.RoomTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonGraphValidator {
    public List<String> validate(DungeonGraph graph, Collection<RoomTemplate> templates) {
        Map<String, RoomTemplate> byId = new HashMap<>();
        for (RoomTemplate template : templates) {
            byId.put(template.id(), template);
        }
        List<String> errors = new ArrayList<>();
        validateEdges(graph, byId, errors);
        validateBounds(graph, errors);
        return errors;
    }

    private void validateEdges(DungeonGraph graph, Map<String, RoomTemplate> templates, List<String> errors) {
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
            var fromFacing = from.transform().transformFacing(fromDoor.facing());
            var toFacing = to.transform().transformFacing(toDoor.facing());
            var fromPosition = from.transform().transformLocal(fromDoor.position());
            var toPosition = to.transform().transformLocal(toDoor.position());
            if (toFacing != fromFacing.opposite()) {
                errors.add("Edge " + edge + " has non-opposite facings: " + fromFacing + " and " + toFacing);
            }
            if (!toPosition.equals(fromPosition.add(fromFacing.vector()))) {
                errors.add("Edge " + edge + " doors are not adjacent: " + fromPosition + " -> " + toPosition + " expected " + fromPosition.add(fromFacing.vector()));
            }
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
