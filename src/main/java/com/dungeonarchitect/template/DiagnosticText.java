package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticText {
    private static final int DEFAULT_LINE_WIDTH = 42;
    private static final int DEFAULT_LINE_LIMIT = 8;

    private DiagnosticText() {
    }

    public static String size(IntVector3 size) {
        if (size == null) {
            return "unset";
        }
        return size.x() + "x" + size.y() + "x" + size.z();
    }

    public static String position(IntVector3 position) {
        if (position == null) {
            return "unset";
        }
        return "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
    }

    public static String box(BoundingBox3i bounds) {
        if (bounds == null) {
            return "unset";
        }
        return "min " + position(bounds.min()) + ", max " + position(bounds.max()) + ", size " + size(bounds.size());
    }

    public static List<String> lore(List<String> lines) {
        return lore(lines, DEFAULT_LINE_WIDTH, DEFAULT_LINE_LIMIT);
    }

    public static List<String> lore(List<String> lines, int width, int limit) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            for (String part : wrap(line, width)) {
                if (wrapped.size() >= limit) {
                    wrapped.set(limit - 1, "More details in /da diagnose.");
                    return List.copyOf(wrapped);
                }
                wrapped.add(part);
            }
        }
        return List.copyOf(wrapped);
    }

    private static List<String> wrap(String line, int width) {
        if (line.length() <= width) {
            return List.of(line);
        }
        List<String> wrapped = new ArrayList<>();
        String remaining = line;
        while (remaining.length() > width) {
            int breakAt = remaining.lastIndexOf(' ', width);
            if (breakAt < Math.max(12, width / 2)) {
                breakAt = width;
            }
            wrapped.add(remaining.substring(0, breakAt).trim());
            remaining = remaining.substring(breakAt).trim();
        }
        if (!remaining.isBlank()) {
            wrapped.add(remaining);
        }
        return wrapped;
    }
}
