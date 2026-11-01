package com.dungeonarchitect.generation;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.door.DoorTemplateIO;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DoorSlotAvailabilityTest {
    Path root=Path.of("src/test/resources/generation/server-layout-current");
    List<DoorTemplate> doors(){return List.of("basic","diorite-arch").stream().map(id->DoorTemplateIO.load(root.resolve("doors").resolve(id))).toList();}
    @Test void missingAssignmentsAreDistinctFromCollisionChecks(){
        var medium=RoomTemplateIO.load(root.resolve("rooms/medium"));var empty=medium.doors().stream().filter(d->d.id().equals("door_2")).findFirst().orElseThrow().withEntries(List.of());
        var result=DoorSlotAvailability.assess(medium,empty,List.of(medium),doors());assertFalse(result.available());assertTrue(result.reasons().getFirst().contains("assigned"));
        var usable=medium.doors().stream().filter(d->!d.entries().isEmpty()&&d.facing()!=Direction3.UP).findFirst().orElseThrow();
        result=DoorSlotAvailability.assess(medium,usable,List.of(RoomTemplateIO.load(root.resolve("rooms/hallway"))),doors());assertTrue(result.available(),result.reasons().toString());assertTrue(result.reasons().stream().anyMatch(r->r.contains("collisions")));
    }
    @Test void roomRestrictionsAreNotSilentlyBypassed(){
        var large=RoomTemplateIO.load(root.resolve("rooms/large"));var result=DoorSlotAvailability.assess(large,large.doors().getFirst(),List.of(large),doors());
        assertFalse(result.available());assertTrue(result.reasons().stream().anyMatch(r->r.contains("Rule restriction")));assertTrue(large.tags().contains("poi"));
    }
}
