package com.dungeonarchitect.api.event;

import com.dungeonarchitect.domain.RoomState;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.RoomInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class RoomStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance dungeon;
    private final RoomInstance room;
    private final RoomState oldState;
    private final RoomState newState;

    public RoomStateChangeEvent(DungeonInstance dungeon, RoomInstance room, RoomState oldState, RoomState newState) {
        this.dungeon = dungeon;
        this.room = room;
        this.oldState = oldState;
        this.newState = newState;
    }

    public DungeonInstance dungeon() {
        return dungeon;
    }

    public RoomInstance room() {
        return room;
    }

    public RoomState oldState() {
        return oldState;
    }

    public RoomState newState() {
        return newState;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
