package com.dungeonarchitect.authoring;

import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class SelectionRaycaster {
    private SelectionRaycaster() {
    }

    public static <T> Optional<Hit<T>> firstHit(List<T> values, Function<T, SelectionBounds> bounds, Vector origin, Vector direction, double maxDistance) {
        if (direction.lengthSquared() == 0 || maxDistance < 0) {
            return Optional.empty();
        }
        Vector normalized = direction.clone().normalize();
        return values.stream()
            .map(value -> intersect(value, bounds.apply(value), origin, normalized, maxDistance))
            .flatMap(Optional::stream)
            .min(Comparator.comparingDouble(Hit::distance));
    }

    public static List<Vector> rayPoints(Vector origin, Vector direction, double distance, double step) {
        if (direction.lengthSquared() == 0 || distance < 0 || step <= 0) {
            return List.of();
        }
        Vector normalized = direction.clone().normalize();
        List<Vector> points = new ArrayList<>();
        for (double current = 0; current <= distance; current += step) {
            points.add(origin.clone().add(normalized.clone().multiply(current)));
        }
        return List.copyOf(points);
    }

    private static <T> Optional<Hit<T>> intersect(T value, SelectionBounds bounds, Vector origin, Vector direction, double maxDistance) {
        double tMin = 0.0;
        double tMax = maxDistance;

        double[] originValues = {origin.getX(), origin.getY(), origin.getZ()};
        double[] directionValues = {direction.getX(), direction.getY(), direction.getZ()};
        double[] minValues = {bounds.min().x(), bounds.min().y(), bounds.min().z()};
        double[] maxValues = {bounds.visualMax().x(), bounds.visualMax().y(), bounds.visualMax().z()};

        for (int axis = 0; axis < 3; axis++) {
            double axisDirection = directionValues[axis];
            if (Math.abs(axisDirection) < 1.0E-9) {
                if (originValues[axis] < minValues[axis] || originValues[axis] > maxValues[axis]) {
                    return Optional.empty();
                }
                continue;
            }

            double inverse = 1.0 / axisDirection;
            double t1 = (minValues[axis] - originValues[axis]) * inverse;
            double t2 = (maxValues[axis] - originValues[axis]) * inverse;
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return Optional.empty();
            }
        }

        if (tMin > maxDistance) {
            return Optional.empty();
        }
        return Optional.of(new Hit<>(value, tMin));
    }

    public record Hit<T>(T value, double distance) {
    }
}
