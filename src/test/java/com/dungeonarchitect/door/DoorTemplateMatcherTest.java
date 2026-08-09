package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.Rotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoorTemplateMatcherTest {
    @Test
    void matchesEqualDoorBoundsAndCompatibleTags() {
        DoorSocket slot = slot(new IntVector3(3, 4, 1), Set.of("stone"));
        DoorTemplate template = template(new IntVector3(3, 4, 1), new IntVector3(1, 2, 1), Direction3.NORTH, Set.of("stone", "locked"));

        assertTrue(DoorTemplateMatcher.matches(slot, template));
    }

    @Test
    void matchesDoorBoundsAfterYawRotationToSlotFacing() {
        DoorSocket slot = slot(new IntVector3(3, 4, 1), Set.of("stone"));
        DoorTemplate template = template(new IntVector3(1, 4, 3), new IntVector3(1, 2, 1), Direction3.WEST, Set.of("stone"));

        DoorTemplateMatcher.DoorTemplateMatchResult result = DoorTemplateMatcher.match(slot, template);

        assertTrue(result.matched());
        assertEquals(Rotation.CLOCKWISE_90, result.rotation());
    }

    @Test
    void emptyTagsAreCompatibleButSizeMustMatch() {
        assertTrue(DoorTemplateMatcher.matches(slot(new IntVector3(3, 4, 1), Set.of()), template(new IntVector3(3, 4, 1), new IntVector3(1, 2, 1), Direction3.NORTH, Set.of("stone"))));
        DoorTemplateMatcher.DoorTemplateMatchResult result = DoorTemplateMatcher.match(slot(new IntVector3(5, 4, 1), Set.of()), template(new IntVector3(3, 4, 1), new IntVector3(1, 2, 1), Direction3.NORTH, Set.of("stone")));
        assertFalse(result.matched());
        assertNotNull(result.reason());
    }

    @Test
    void rejectsIncompatibleTags() {
        DoorTemplateMatcher.DoorTemplateMatchResult result = DoorTemplateMatcher.match(slot(new IntVector3(3, 4, 1), Set.of("wood")), template(new IntVector3(3, 4, 1), new IntVector3(1, 2, 1), Direction3.NORTH, Set.of("stone")));
        assertFalse(result.matched());
        assertTrue(result.reason().contains("tags"));
    }

    @Test
    void rejectsMissingGatewayWithReason() {
        DoorTemplate template = new DoorTemplate("arch", new IntVector3(7, 7, 4), Set.of(), List.of(), List.of(), null, Path.of("door.nbt"));

        DoorTemplateMatcher.DoorTemplateMatchResult result = DoorTemplateMatcher.match(slot(new IntVector3(3, 4, 1), Set.of()), template);

        assertFalse(result.matched());
        assertTrue(result.reason().contains("gateway"));
    }

    private static DoorSocket slot(IntVector3 size, Set<String> tags) {
        return new DoorSocket("door_1", new IntVector3(8, 2, 0), size, Direction3.NORTH, tags, List.of());
    }

    private static DoorTemplate template(IntVector3 doorSize, IntVector3 gatewaySize, Direction3 gatewayFacing, Set<String> tags) {
        return new DoorTemplate(
            "arch",
            doorSize,
            tags,
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(2, 1, 0), gatewaySize, gatewayFacing),
            Path.of("door.nbt")
        );
    }
}
