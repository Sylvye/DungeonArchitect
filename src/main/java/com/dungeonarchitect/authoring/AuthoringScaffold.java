package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;

import java.util.ArrayList;
import java.util.List;

public final class AuthoringScaffold {
    public static final int FLOOR_SIZE = 10;

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
}
