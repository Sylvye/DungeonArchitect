package com.dungeonarchitect.domain;

public enum TagDomain {
    ROOM("Room Tags"),
    DOOR("Door Tags");

    private final String label;

    TagDomain(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
