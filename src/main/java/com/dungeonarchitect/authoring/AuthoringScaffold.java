package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.List;

public final class AuthoringScaffold {
    public static final int FLOOR_SIZE = 10;
    public static final int SUPPORT_PADDING = 2;

    private AuthoringScaffold() {
    }

    public static List<IntVector3> floorBlocks(IntVector3 buildOrigin) {
        int minOffset = -(FLOOR_SIZE / 2);
        int maxOffset = minOffset + FLOOR_SIZE - 1;
        List<IntVector3> blocks = new ArrayList<>(FLOOR_SIZE * FLOOR_SIZE);
        for (int x = minOffset; x <= maxOffset; x++) {
            for (int z = minOffset; z <= maxOffset; z++) {
                blocks.add(new IntVector3(buildOrigin.x() + x, buildOrigin.y() - 1, buildOrigin.z() + z));
            }
        }
        return List.copyOf(blocks);
    }

    public static SelectionBounds floorBounds(IntVector3 buildOrigin) {
        int minOffset = -(FLOOR_SIZE / 2);
        int maxOffset = minOffset + FLOOR_SIZE - 1;
        return SelectionBounds.between(
            new IntVector3(buildOrigin.x() + minOffset, buildOrigin.y() - 1, buildOrigin.z() + minOffset),
            new IntVector3(buildOrigin.x() + maxOffset, buildOrigin.y() - 1, buildOrigin.z() + maxOffset)
        );
    }

    public static SelectionBounds supportPlatformBounds(SelectionBounds footprint) {
        return SelectionBounds.between(
            new IntVector3(footprint.min().x() - SUPPORT_PADDING, footprint.min().y() - 1, footprint.min().z() - SUPPORT_PADDING),
            new IntVector3(footprint.max().x() + SUPPORT_PADDING, footprint.min().y() - 1, footprint.max().z() + SUPPORT_PADDING)
        );
    }

    public static List<IntVector3> supportPlatformBlocks(SelectionBounds footprint) {
        SelectionBounds platform = supportPlatformBounds(footprint);
        IntVector3 size = platform.size();
        List<IntVector3> blocks = new ArrayList<>(size.x() * size.z());
        for (int x = platform.min().x(); x <= platform.max().x(); x++) {
            for (int z = platform.min().z(); z <= platform.max().z(); z++) {
                blocks.add(new IntVector3(x, platform.min().y(), z));
            }
        }
        return List.copyOf(blocks);
    }
}
