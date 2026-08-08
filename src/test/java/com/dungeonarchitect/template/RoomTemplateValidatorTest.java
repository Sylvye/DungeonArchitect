package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.SocketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoomTemplateValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsDuplicateDoorsAndOutOfBoundsPositions() throws Exception {
        Path nbt = tempDir.resolve("room.nbt");
        Files.writeString(nbt, "fake");
        RoomTemplate template = new RoomTemplate(
            "bad",
            RoomCategory.COMBAT,
            1,
            Set.of(),
            new IntVector3(3, 3, 3),
            null,
            List.of(
                new DoorSocket("door", new IntVector3(1, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2),
                new DoorSocket("door", new IntVector3(9, 1, 0), Direction3.NORTH, SocketType.STANDARD, 1, 2)
            ),
            List.of(),
            List.of(),
            nbt
        );

        var result = new RoomTemplateValidator().validate(template);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("duplicate door")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("outside room bounds")));
    }
}
