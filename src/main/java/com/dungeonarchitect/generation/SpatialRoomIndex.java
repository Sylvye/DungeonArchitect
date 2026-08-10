package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SpatialRoomIndex {
    private static final int CELL_SIZE = 32;

    private final Map<Integer, BoundingBox3i> boundsByNode = new HashMap<>();
    private final Map<Cell, List<Integer>> nodesByCell = new HashMap<>();

    void add(int nodeIndex, BoundingBox3i bounds) {
        remove(nodeIndex);
        boundsByNode.put(nodeIndex, bounds);
        for (Cell cell : cells(bounds)) {
            nodesByCell.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(nodeIndex);
        }
    }

    void remove(int nodeIndex) {
        BoundingBox3i bounds = boundsByNode.remove(nodeIndex);
        if (bounds == null) {
            return;
        }
        for (Cell cell : cells(bounds)) {
            List<Integer> nodes = nodesByCell.get(cell);
            if (nodes == null) {
                continue;
            }
            nodes.remove(Integer.valueOf(nodeIndex));
            if (nodes.isEmpty()) {
                nodesByCell.remove(cell);
            }
        }
    }

    boolean intersects(BoundingBox3i bounds) {
        Set<Integer> checked = new HashSet<>();
        for (Cell cell : cells(bounds)) {
            List<Integer> nodes = nodesByCell.get(cell);
            if (nodes == null) {
                continue;
            }
            for (int nodeIndex : nodes) {
                if (checked.add(nodeIndex) && bounds.intersects(boundsByNode.get(nodeIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Cell> cells(BoundingBox3i bounds) {
        IntVector3 min = bounds.min();
        IntVector3 max = bounds.max();
        int minX = Math.floorDiv(min.x(), CELL_SIZE);
        int minY = Math.floorDiv(min.y(), CELL_SIZE);
        int minZ = Math.floorDiv(min.z(), CELL_SIZE);
        int maxX = Math.floorDiv(max.x(), CELL_SIZE);
        int maxY = Math.floorDiv(max.y(), CELL_SIZE);
        int maxZ = Math.floorDiv(max.z(), CELL_SIZE);
        List<Cell> cells = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cells.add(new Cell(x, y, z));
                }
            }
        }
        return cells;
    }

    private record Cell(int x, int y, int z) {
    }
}
