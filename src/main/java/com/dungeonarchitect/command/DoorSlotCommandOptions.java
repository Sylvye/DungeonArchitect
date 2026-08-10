package com.dungeonarchitect.command;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.SocketType;

import java.util.Locale;

record DoorSlotCommandOptions(String id, SocketType socketType, Direction3 facing) {
    static DoorSlotCommandOptions parse(String[] args, int startIndex) {
        if (args.length > startIndex && args[startIndex].equalsIgnoreCase("add")) {
            startIndex++;
        }
        String id = args.length > startIndex ? args[startIndex] : null;
        SocketType socketType = args.length > startIndex + 1 ? enumValue(SocketType.class, args[startIndex + 1], "socket type") : SocketType.STANDARD;
        Direction3 facing = args.length > startIndex + 2 ? enumValue(Direction3.class, args[startIndex + 2], "facing") : null;
        if (args.length > startIndex + 3) {
            throw new IllegalArgumentException("Usage: /da room door [id] [socketType] [facing]");
        }
        return new DoorSlotCommandOptions(id, socketType, facing);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + label + " " + value);
        }
    }
}
