package com.dungeonarchitect.api.event;

import com.dungeonarchitect.runtime.DungeonInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class DungeonDestroyedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance dungeon;

    public DungeonDestroyedEvent(DungeonInstance dungeon) {
        this.dungeon = dungeon;
    }

    public DungeonInstance dungeon() {
        return dungeon;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
