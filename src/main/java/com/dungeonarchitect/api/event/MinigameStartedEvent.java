package com.dungeonarchitect.api.event;

import com.dungeonarchitect.minigame.MinigameRun;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class MinigameStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MinigameRun run;

    public MinigameStartedEvent(MinigameRun run) { this.run = run;  }
    public MinigameRun run() { return run; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
