package com.dungeonarchitect.api.event;

import com.dungeonarchitect.minigame.MinigameRun;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class MinigameEndedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MinigameRun run;
    private final String reason;
    public MinigameEndedEvent(MinigameRun run, String reason) { this.run = run; this.reason = reason; }
    public MinigameRun run() { return run; }
    public String reason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
