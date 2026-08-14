package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureSlotEntry;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomFeatureSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Validates feature dependencies and computes bounded worst-case expansion metrics. */
public final class FeatureGraphValidator {
    private final FeatureNestingPolicy policy;

    public FeatureGraphValidator(FeatureNestingPolicy policy) {
        this.policy = policy;
    }

    public Analysis analyze(Map<String, FeatureTemplate> templates, Map<String, List<String>> localErrors) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Metrics> metrics = new LinkedHashMap<>();
        for (FeatureTemplate template : templates.values()) {
            LinkedHashSet<String> featureErrors = new LinkedHashSet<>(localErrors.getOrDefault(template.id(), List.of()));
            List<String> cycle = cyclePath(template.id(), templates);
            if (!cycle.isEmpty()) {
                featureErrors.add(template.id() + ": feature nesting cycle: " + String.join(" -> ", cycle));
            }
            Evaluation evaluation = evaluate(template.id(), template.id(), templates, localErrors, new ArrayList<>(), new LinkedHashMap<>());
            featureErrors.addAll(evaluation.errors());
            if (featureErrors.isEmpty()) {
                metrics.put(template.id(), evaluation.metrics());
            }
            errors.put(template.id(), List.copyOf(featureErrors));
        }
        return new Analysis(Map.copyOf(errors), Map.copyOf(metrics));
    }

    private List<String> cyclePath(String root, Map<String, FeatureTemplate> templates) {
        if (!templates.containsKey(root)) return List.of();
        Deque<CycleFrame> frames = new ArrayDeque<>();
        List<String> path = new ArrayList<>();
        Map<String, Integer> pathIndex = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        pushCycleFrame(root, templates, frames, path, pathIndex);
        while (!frames.isEmpty()) {
            CycleFrame frame = frames.getLast();
            if (frame.children().hasNext()) {
                String child = frame.children().next();
                Integer cycleStart = pathIndex.get(child);
                if (cycleStart != null) {
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(child);
                    return cycle;
                }
                if (templates.containsKey(child) && !completed.contains(child)) {
                    pushCycleFrame(child, templates, frames, path, pathIndex);
                }
            } else {
                frames.removeLast();
                String finished = path.removeLast();
                pathIndex.remove(finished);
                completed.add(finished);
            }
        }
        return List.of();
    }

    private void pushCycleFrame(String id, Map<String, FeatureTemplate> templates, Deque<CycleFrame> frames,
                                List<String> path, Map<String, Integer> pathIndex) {
        pathIndex.put(id, path.size());
        path.add(id);
        Iterator<String> children = templates.get(id).featureSlots().stream()
            .flatMap(slot -> slot.entries().stream())
            .map(FeatureSlotEntry::featureId)
            .filter(child -> !child.equals(FeatureSlotEntry.EMPTY))
            .distinct()
            .iterator();
        frames.addLast(new CycleFrame(id, children));
    }

    private Evaluation evaluate(String root, String id, Map<String, FeatureTemplate> templates,
                                Map<String, List<String>> localErrors, List<String> stack,
                                Map<String, Metrics> successfulMemo) {
        int cycleAt = stack.indexOf(id);
        if (cycleAt >= 0) {
            List<String> cycle = new ArrayList<>(stack.subList(cycleAt, stack.size()));
            cycle.add(id);
            return Evaluation.failed(root + ": feature nesting cycle: " + String.join(" -> ", cycle));
        }
        if (stack.size() + 1 > policy.maxDepth()) {
            List<String> path = new ArrayList<>(stack);
            path.add(id);
            return Evaluation.failed(root + ": feature nesting depth " + (stack.size() + 1) + " exceeds maximum " + policy.maxDepth() + " at " + String.join(" -> ", path));
        }
        FeatureTemplate template = templates.get(id);
        if (template == null) {
            return Evaluation.failed(root + ": references missing feature " + id);
        }
        List<String> targetLocalErrors = localErrors.getOrDefault(id, List.of());
        if (!targetLocalErrors.isEmpty()) {
            return Evaluation.failed(root.equals(id)
                ? targetLocalErrors
                : List.of(root + ": depends on invalid feature " + id + ": " + targetLocalErrors.getFirst()));
        }
        Metrics memo = successfulMemo.get(id);
        if (memo != null) {
            return Evaluation.success(memo);
        }

        stack.add(id);
        int depth = 1;
        long placements = 1;
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        for (RoomFeatureSlot slot : template.featureSlots()) {
            int slotDepth = 0;
            long slotPlacements = 0;
            for (FeatureSlotEntry entry : slot.entries()) {
                if (entry.featureId().equals(FeatureSlotEntry.EMPTY)) {
                    continue;
                }
                FeatureTemplate child = templates.get(entry.featureId());
                if (child == null) {
                    errors.add(root + ": slot " + slot.id() + " references missing feature " + entry.featureId());
                    continue;
                }
                FeatureMatcher.FeatureMatchResult match = FeatureMatcher.match(slot, child);
                if (!match.matched()) {
                    errors.add(root + ": slot " + slot.id() + " cannot use feature " + child.id() + ": " + match.reason());
                    continue;
                }
                Evaluation childResult = evaluate(root, child.id(), templates, localErrors, stack, successfulMemo);
                errors.addAll(childResult.errors());
                if (childResult.valid()) {
                    slotDepth = Math.max(slotDepth, childResult.metrics().depth());
                    slotPlacements = Math.max(slotPlacements, childResult.metrics().expandedPlacements());
                }
            }
            depth = Math.max(depth, 1 + slotDepth);
            placements = Math.min(Integer.MAX_VALUE, placements + slotPlacements);
        }
        stack.removeLast();
        if (errors.isEmpty() && depth > policy.maxDepth()) {
            errors.add(root + ": feature nesting depth " + depth + " exceeds maximum " + policy.maxDepth());
        }
        if (errors.isEmpty() && placements > policy.maxExpandedPlacements()) {
            errors.add(root + ": worst-case expanded placements " + placements + " exceeds maximum " + policy.maxExpandedPlacements());
        }
        if (!errors.isEmpty()) {
            return Evaluation.failed(List.copyOf(errors));
        }
        Metrics result = new Metrics(depth, (int) placements);
        successfulMemo.put(id, result);
        return Evaluation.success(result);
    }

    public record Metrics(int depth, int expandedPlacements) {}

    public record Analysis(Map<String, List<String>> errorsByFeature, Map<String, Metrics> metricsByFeature) {
        public List<String> errors(String id) {
            return errorsByFeature.getOrDefault(id.toLowerCase(java.util.Locale.ROOT), List.of());
        }

        public Metrics metrics(String id) {
            return metricsByFeature.get(id.toLowerCase(java.util.Locale.ROOT));
        }
    }

    private record Evaluation(Metrics metrics, List<String> errors) {
        static Evaluation success(Metrics metrics) { return new Evaluation(metrics, List.of()); }
        static Evaluation failed(String error) { return new Evaluation(null, List.of(error)); }
        static Evaluation failed(List<String> errors) { return new Evaluation(null, List.copyOf(errors)); }
        boolean valid() { return errors.isEmpty(); }
    }

    private record CycleFrame(String id, Iterator<String> children) {}
}
