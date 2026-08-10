package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DoorTemplateValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsGatewayFacingThatDoesNotMatchDominantBoundsFace() throws Exception {
        Path nbt = tempDir.resolve("door.nbt");
        Files.writeString(nbt, "fake");
        DoorTemplate template = new DoorTemplate(
            "bad_gateway",
            new IntVector3(5, 2, 5),
            Set.of(),
            List.of(),
            List.of(),
            new DoorGateway(new IntVector3(1, 0, 1), new IntVector3(3, 1, 3), Direction3.NORTH),
            nbt
        );

        var result = new DoorTemplateValidator(null).validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("does not match inferred bounds face DOWN")), result.errors().toString());
    }
}
