package com.dungeonarchitect.domain;

public record DungeonEdge(int fromNode, String fromDoorId, String fromDoorTemplateId, int toNode, String toDoorId, String toDoorTemplateId) {
    public DungeonEdge(int fromNode, String fromDoorId, int toNode, String toDoorId) {
        this(fromNode, fromDoorId, null, toNode, toDoorId, null);
    }
}
