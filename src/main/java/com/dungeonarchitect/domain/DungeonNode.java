package com.dungeonarchitect.domain;

public record DungeonNode(
    int index,
    String templateId,
    RoomCategory category,
    int depth,
    RoomTransform transform
) {
}
