package com.dungeonarchitect.template;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.loot.LootBinding;
import com.dungeonarchitect.door.DoorTemplateIO;
import com.dungeonarchitect.feature.FeatureTemplateIO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MarkerAreaTest {
    @TempDir Path directory;
    @Test void areasKeepDimensionsAndRejectEmptySelections() {
        var marker = new RoomMarker("a", "generic", new IntVector3(2,1,3), new IntVector3(3,2,4));
        assertEquals(new IntVector3(4,2,6), marker.bounds().max());
        assertEquals(marker.size(), marker.renamed("b").size());
        assertThrows(IllegalArgumentException.class, () -> new RoomMarker("a", "loot", IntVector3.ZERO, new IntVector3(0,1,1)));
    }
    @Test void legacyFeetAndEntriesMigrateOnceWithoutChangingBlocks() throws Exception {
        Path room = directory.resolve("room"); Files.createDirectories(room);
        Files.writeString(room.resolve("room.nbt"), "original blocks");
        Files.writeString(room.resolve("room.yml"), """
            id: room
            category: START
            size: [8, 5, 8]
            spawn: [2, 1, 2]
            markers:
              - name: spawn
                type: player_spawn
                position: [2, 1, 2]
              - name: chest
                type: custom
                position: [3, 1, 2]
              - name: note
                type: plugin_custom
                position: [1, 1, 1]
            lootBindings:
              chest:
                table: supplies
                minimumRolls: 1
                maximumRolls: 2
            """);
        var original = Files.readString(room.resolve("room.yml"));
        RoomTemplate first = RoomTemplateIO.load(room);
        assertEquals(3, first.markers().size()); assertNull(first.spawn());
        assertEquals(new IntVector3(2,0,2), first.markers().getFirst().position());
        assertEquals(RoomMarker.Purpose.PLAYER_SPAWN, first.markers().getFirst().purpose());
        assertEquals("plugin_custom", first.markers().get(2).type());
        assertEquals(RoomMarker.Purpose.LOOT,first.markers().get(1).purpose());
        assertEquals(original, Files.readString(room.resolve("room.yml")));
        RoomTemplateIO.save(first, room); assertEquals(first, RoomTemplateIO.load(room));
        assertEquals("original blocks", Files.readString(room.resolve("room.nbt")));
    }
    @Test void serializationIsSharedForRoomsDoorsAndFeatures() throws Exception {
        var marker = new RoomMarker("supply", "loot", new IntVector3(1,1,1), new IntVector3(2,1,3));
        var bindings = Map.of("supply", new LootBinding("supplies",2,3));
        var size = new IntVector3(8,5,8);
        var room = new RoomTemplate("room", RoomCategory.GENERIC,1,0,Set.of(),size,null,List.of(),List.of(marker),List.of(),bindings,directory.resolve("room/room.nbt"));
        var door = new DoorTemplate("door",size,Set.of(),List.of(marker),List.of(),bindings,null,directory.resolve("door/door.nbt"));
        var feature = new FeatureTemplate("feature",size,Set.of(),List.of(marker),List.of(),bindings,directory.resolve("feature/feature.nbt"));
        RoomTemplateIO.save(room,directory.resolve("room")); DoorTemplateIO.save(door,directory.resolve("door")); FeatureTemplateIO.save(feature,directory.resolve("feature"));
        assertEquals(room,RoomTemplateIO.load(directory.resolve("room"))); assertEquals(door,DoorTemplateIO.load(directory.resolve("door"))); assertEquals(feature,FeatureTemplateIO.load(directory.resolve("feature")));
    }
    @Test void validationChecksFullBoundsOwnerAndIncompatibleBinding() {
        var result = new TemplateValidationResult();
        MarkerIO.validate("door", List.of(new RoomMarker("spawn","player_spawn_area",IntVector3.ZERO,new IntVector3(5,1,1))),Map.of("spawn",new LootBinding("supplies")),BoundingBox3i.fromMinAndSize(IntVector3.ZERO,new IntVector3(3,3,3)),false,result);
        assertEquals(3,result.errors().size());
    }
    @Test void explicitlyIncompatibleTypedBindingsAreFlaggedWithoutDeletingData() {
        var yaml=new YamlConfiguration();yaml.set("markers",List.of(Map.of("name","spawn","type","player_spawn_area","position",List.of(1,0,1),"size",List.of(2,1,2))));
        String original=yaml.saveToString();assertThrows(IllegalArgumentException.class,()->MarkerIO.load(yaml,Map.of("spawn",new LootBinding("supply"))));assertEquals(original,yaml.saveToString());
    }
    @Test void entryMigrationAvoidsComponentNameCollisions() {
        var markers = MarkerIO.withLegacyEntry(List.of(),new IntVector3(1,1,1),Set.of("DUNGEON_ENTRY","dungeon_entry_2"));
        assertEquals("dungeon_entry_3",markers.getFirst().name());
        assertEquals(markers,MarkerIO.withLegacyEntry(markers,new IntVector3(1,1,1),Set.of()));
    }
}
