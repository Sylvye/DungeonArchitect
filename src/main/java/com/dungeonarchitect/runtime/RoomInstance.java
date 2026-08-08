package com.dungeonarchitect.runtime;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.RoomState;
import com.dungeonarchitect.domain.RoomTemplate;

public final class RoomInstance {
    private final DungeonNode node;
    private final RoomTemplate template;
    private RoomState state = RoomState.UNSEEN;

    public RoomInstance(DungeonNode node, RoomTemplate template) {
        this.node = node;
        this.template = template;
    }

    public DungeonNode node() {
        return node;
    }

    public RoomTemplate template() {
        return template;
    }

    public BoundingBox3i bounds() {
        return node.transform().transformedBounds();
    }

    public RoomState state() {
        return state;
    }

    public void setState(RoomState state) {
        this.state = state;
    }
}
