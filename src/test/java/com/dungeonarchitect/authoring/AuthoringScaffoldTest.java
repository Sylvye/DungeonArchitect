package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthoringScaffoldTest {
    @Test
    void tenByTenFloorIsCenteredOnBuildOrigin() {
        IntVector3 origin = new IntVector3(32, 80, 32);

        var blocks = AuthoringScaffold.floorBlocks(origin);

        assertEquals(100, blocks.size());
        assertTrue(blocks.contains(new IntVector3(27, 79, 27)));
        assertTrue(blocks.contains(new IntVector3(36, 79, 36)));
        assertTrue(blocks.contains(new IntVector3(32, 79, 32)));
    }

    @Test
    void supportPlatformExpandsFootprintByTwoBlocksInXZ() {
        SelectionBounds footprint = SelectionBounds.between(new IntVector3(10, 80, 20), new IntVector3(14, 85, 27));

        SelectionBounds platform = AuthoringScaffold.supportPlatformBounds(footprint);
        var blocks = AuthoringScaffold.supportPlatformBlocks(footprint);

        assertEquals(new IntVector3(8, 79, 18), platform.min());
        assertEquals(new IntVector3(16, 79, 29), platform.max());
        assertEquals(9 * 12, blocks.size());
        assertTrue(blocks.contains(new IntVector3(8, 79, 18)));
        assertTrue(blocks.contains(new IntVector3(16, 79, 29)));
    }
}
