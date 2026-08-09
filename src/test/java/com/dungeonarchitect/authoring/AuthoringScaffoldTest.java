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
}
