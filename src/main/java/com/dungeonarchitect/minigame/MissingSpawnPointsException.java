package com.dungeonarchitect.minigame;
/** @deprecated Use the shared spawn-area capacity error. */
@Deprecated
public final class MissingSpawnPointsException extends com.dungeonarchitect.runtime.SpawnAreaException {
    public MissingSpawnPointsException(String roomId, int actual, int required) { super(roomId, actual, required); }
    public MissingSpawnPointsException(String roomId, int actual, int required, boolean measured) { super(roomId, actual, required, measured); }
}
