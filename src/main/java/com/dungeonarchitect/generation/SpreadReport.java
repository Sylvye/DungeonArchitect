package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.*;
import java.util.*;

/** Door-transition distances, independent of physical placement and root depth. */
public record SpreadReport(List<Integer> spawns, Map<String, Integer> distances, int shortest, int longest) {
    public SpreadReport {
        spawns = List.copyOf(spawns);
        distances = Collections.unmodifiableMap(new LinkedHashMap<>(distances));
    }

    public static int[] distancesFrom(DungeonGraph graph, int source) {
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < graph.nodes().size(); i++) neighbors.add(new ArrayList<>());
        for (DungeonEdge edge : graph.edges()) {
            neighbors.get(edge.fromNode()).add(edge.toNode());
            neighbors.get(edge.toNode()).add(edge.fromNode());
        }
        int[] distances = new int[neighbors.size()];
        Arrays.fill(distances, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distances[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int node = queue.remove();
            for (int neighbor : neighbors.get(node)) if (distances[neighbor] < 0) {
                distances[neighbor] = distances[node] + 1;
                queue.add(neighbor);
            }
        }
        return distances;
    }

    public static SpreadReport measure(DungeonGraph graph) {
        List<Integer> spawns = graph.nodes().stream().filter(n -> n.category() == RoomCategory.TEAM_SPAWN).map(DungeonNode::index).toList();
        Map<String, Integer> pairs = new LinkedHashMap<>();
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < spawns.size(); i++) {
            int[] distances = distancesFrom(graph, spawns.get(i));
            for (int j = i + 1; j < spawns.size(); j++) {
                int distance = distances[spawns.get(j)];
                pairs.put(spawns.get(i) + "–" + spawns.get(j), distance);
                min = Math.min(min, distance);
                max = Math.max(max, distance);
            }
        }
        return new SpreadReport(spawns, pairs, min == Integer.MAX_VALUE ? 0 : min, max);
    }

    public boolean accepts(TeamSpread spread) {
        return spawns.size() == spread.teams() && shortest >= spread.minimumDistance()
            && longest <= shortest * spread.maximumRatio();
    }

    public String summary() { return "spawns=" + spawns.size() + ", shortest=" + shortest + ", longest=" + longest + ", pairs=" + distances; }
}
