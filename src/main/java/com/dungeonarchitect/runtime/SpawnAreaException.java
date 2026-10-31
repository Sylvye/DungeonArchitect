package com.dungeonarchitect.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** An actionable capacity failure shared by classic entry and minigame launches. */
public class SpawnAreaException extends IllegalArgumentException {
    private final String roomId;
    public SpawnAreaException(String roomId, int actual, int required) { this(roomId, actual, required, true); }
    public SpawnAreaException(String roomId, int actual, int required, boolean measured) {
        super(message(roomId, actual, required, measured)); this.roomId = roomId;
    }
    private static String message(String room, int actual, int required, boolean measured) {
        String prefix = "Spawn room '" + room + "' ";
        if (measured) return prefix + "has " + actual + " safe spawn positions; the entering group needs " + required + ". Enlarge its Player Spawn areas.";
        if (actual == 0) return prefix + "has no usable Player Spawn areas; the entering group needs " + required + " positions. Select floor blocks with the wand and add a Player Spawn marker.";
        return prefix + "has room for at most " + actual + " positions in its authored areas; the entering group needs " + required + ". Enlarge its Player Spawn areas.";
    }
    public String roomId() { return roomId; }
    public Component guidance() {
        return Component.text(getMessage() + " ").append(Component.text("[Edit Spawn Areas]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/da minigame spawns " + roomId)));
    }
}
