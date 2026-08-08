package com.dungeonarchitect.domain;

public enum SocketType {
    STANDARD,
    LARGE,
    SECRET,
    BOSS,
    STAIRS_UP,
    STAIRS_DOWN;

    public boolean compatibleWith(SocketType other) {
        return this == other;
    }
}
