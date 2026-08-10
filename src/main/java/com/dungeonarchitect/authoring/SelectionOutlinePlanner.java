package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.Direction3;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class SelectionOutlinePlanner {
    private static final double OFFSET_STEP = 0.035;

    private SelectionOutlinePlanner() {
    }

    public static List<PlannedOutline> plan(List<Outline> outlines) {
        List<PlannedOutline> planned = new ArrayList<>(outlines.size());
        List<SelectionBounds> previous = new ArrayList<>();
        for (Outline outline : outlines) {
            int overlaps = 0;
            for (SelectionBounds bounds : previous) {
                if (touchesOrOverlaps(outline.bounds(), bounds)) {
                    overlaps++;
                }
            }
            planned.add(new PlannedOutline(outline.key(), outline.bounds(), offset(overlaps)));
            previous.add(outline.bounds());
        }
        return List.copyOf(planned);
    }

    public static List<Vector> outlinePoints(SelectionBounds bounds, double step, Offset offset) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        List<Vector> points = new ArrayList<>();
        var min = bounds.min();
        var max = bounds.visualMax();
        for (double x = min.x(); x <= max.x(); x += step) {
            add(points, offset, x, min.y(), min.z());
            add(points, offset, x, min.y(), max.z());
            add(points, offset, x, max.y(), min.z());
            add(points, offset, x, max.y(), max.z());
        }
        for (double y = min.y(); y <= max.y(); y += step) {
            add(points, offset, min.x(), y, min.z());
            add(points, offset, min.x(), y, max.z());
            add(points, offset, max.x(), y, min.z());
            add(points, offset, max.x(), y, max.z());
        }
        for (double z = min.z(); z <= max.z(); z += step) {
            add(points, offset, min.x(), min.y(), z);
            add(points, offset, min.x(), max.y(), z);
            add(points, offset, max.x(), min.y(), z);
            add(points, offset, max.x(), max.y(), z);
        }
        return List.copyOf(points);
    }

    public static List<Vector> facingLinePoints(SelectionBounds bounds, Direction3 facing, double step, double length, Offset offset) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        if (facing == null) {
            return List.of();
        }
        var min = bounds.min();
        var max = bounds.visualMax();
        double centerX = (min.x() + max.x()) / 2.0;
        double centerY = (min.y() + max.y()) / 2.0;
        double centerZ = (min.z() + max.z()) / 2.0;
        Vector start = switch (facing) {
            case NORTH -> new Vector(centerX, centerY, min.z());
            case SOUTH -> new Vector(centerX, centerY, max.z());
            case EAST -> new Vector(max.x(), centerY, centerZ);
            case WEST -> new Vector(min.x(), centerY, centerZ);
            case UP -> new Vector(centerX, max.y(), centerZ);
            case DOWN -> new Vector(centerX, min.y(), centerZ);
        };
        Vector direction = new Vector(facing.vector().x(), facing.vector().y(), facing.vector().z());
        List<Vector> points = new ArrayList<>();
        for (double distance = 0; distance <= length; distance += step) {
            Vector point = start.clone().add(direction.clone().multiply(distance));
            points.add(new Vector(point.getX() + offset.x(), point.getY() + offset.y(), point.getZ() + offset.z()));
        }
        return List.copyOf(points);
    }

    private static Offset offset(int overlapCount) {
        if (overlapCount <= 0) {
            return Offset.ZERO;
        }
        int layer = ((overlapCount - 1) / 6) + 1;
        double amount = OFFSET_STEP * layer;
        return switch ((overlapCount - 1) % 6) {
            case 0 -> new Offset(amount, 0, 0);
            case 1 -> new Offset(-amount, 0, 0);
            case 2 -> new Offset(0, 0, amount);
            case 3 -> new Offset(0, 0, -amount);
            case 4 -> new Offset(0, amount, 0);
            default -> new Offset(0, -amount, 0);
        };
    }

    private static boolean touchesOrOverlaps(SelectionBounds first, SelectionBounds second) {
        return first.min().x() <= second.visualMax().x() && first.visualMax().x() >= second.min().x()
            && first.min().y() <= second.visualMax().y() && first.visualMax().y() >= second.min().y()
            && first.min().z() <= second.visualMax().z() && first.visualMax().z() >= second.min().z();
    }

    private static void add(List<Vector> points, Offset offset, double x, double y, double z) {
        points.add(new Vector(x + offset.x(), y + offset.y(), z + offset.z()));
    }

    public record Outline(String key, SelectionBounds bounds) {
    }

    public record PlannedOutline(String key, SelectionBounds bounds, Offset offset) {
    }

    public record Offset(double x, double y, double z) {
        public static final Offset ZERO = new Offset(0, 0, 0);
    }
}
