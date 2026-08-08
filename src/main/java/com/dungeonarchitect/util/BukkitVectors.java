package com.dungeonarchitect.util;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public final class BukkitVectors {
    private BukkitVectors() {
    }

    public static IntVector3 blockVector(Location location) {
        return new IntVector3(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static Direction3 direction(BlockFace face) {
        return switch (face) {
            case NORTH -> Direction3.NORTH;
            case EAST -> Direction3.EAST;
            case SOUTH -> Direction3.SOUTH;
            case WEST -> Direction3.WEST;
            case UP -> Direction3.UP;
            case DOWN -> Direction3.DOWN;
            default -> Direction3.NORTH;
        };
    }
}
