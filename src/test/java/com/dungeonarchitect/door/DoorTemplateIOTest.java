package com.dungeonarchitect.door;

import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorGateway;
import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomMarker;
import com.dungeonarchitect.loot.LootBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DoorTemplateIOTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsVerticalGatewayMetadata() throws Exception {
        Path doorDir = tempDir.resolve("ceiling_hatch");
        Files.createDirectories(doorDir);
        DoorTemplate template = new DoorTemplate(
            "ceiling_hatch",
            new IntVector3(3, 1, 5),
            Set.of("hatch"),
            List.of(new RoomMarker("reward", "generic", new IntVector3(1, 0, 2))),
            List.of(),
            Map.of("reward", new LootBinding("door_loot", 2, 4)),
            new DoorGateway(new IntVector3(1, 0, 2), new IntVector3(1, 1, 1), Direction3.UP),
            doorDir.resolve("door.nbt")
        );

        DoorTemplateIO.save(template, doorDir);
        DoorTemplate loaded = DoorTemplateIO.load(doorDir);

        assertEquals(template.id(), loaded.id());
        assertEquals(template.size(), loaded.size());
        assertEquals(template.tags(), loaded.tags());
        assertEquals(template.gateway(), loaded.gateway());
        assertEquals(template.markers(), loaded.markers());
        assertEquals(template.lootBindings(), loaded.lootBindings());
        assertEquals(template.structureFile(), loaded.structureFile());
    }
}
