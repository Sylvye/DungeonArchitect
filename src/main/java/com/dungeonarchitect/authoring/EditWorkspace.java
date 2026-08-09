package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.IntVector3;

import java.util.UUID;

public record EditWorkspace(UUID owner, int index, BoundingBox3i clearBounds, IntVector3 buildOrigin) {
    public boolean containsTemplate(IntVector3 size) {
        IntVector3 max = buildOrigin.add(size).subtract(new IntVector3(1, 1, 1));
        return clearBounds.contains(buildOrigin) && clearBounds.contains(max);
    }
}
