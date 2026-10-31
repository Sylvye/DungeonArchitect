package com.dungeonarchitect.api.event;

import com.dungeonarchitect.minigame.MinigameRun;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class MinigameEliminatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MinigameRun run;
    private final java.util.UUID playerId;
    public MinigameEliminatedEvent(MinigameRun run, java.util.UUID playerId) { this.run = run; this.playerId = playerId; }
    public MinigameRun run() { return run; }
    public java.util.UUID playerId() { return playerId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
