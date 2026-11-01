package com.dungeonarchitect.generation;
import com.dungeonarchitect.domain.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoopGenerationTest {
    static RoomTemplate corner(String id,RoomCategory category,boolean required){
        var rules=new DoorConnectionRules(Set.of(),Set.of(),required);
        var doors=List.of(new DoorSocket("north",new IntVector3(1,1,0),Direction3.NORTH,SocketType.STANDARD,1,2).withConnectionRules(rules),new DoorSocket("east",new IntVector3(2,1,1),Direction3.EAST,SocketType.STANDARD,1,2).withConnectionRules(rules));
        return new RoomTemplate(id,category,1,Set.of(),new IntVector3(3,4,3),null,doors,List.of(),List.of(),Path.of(id+".nbt"));
    }
    @Test void closesRequiredRotatedSocketsAtExactRoomCountAndIsDeterministic(){
        var templates=List.of(corner("root",RoomCategory.START,true),corner("corner",RoomCategory.GENERIC,true));
        var generator=new DeterministicDungeonGenerator(2000,80);var request=new DungeonGenerationRequest(4,42);
        var result=generator.generate(templates,request);assertTrue(result.successful(),result.errors().toString());assertEquals(4,result.graph().nodes().size());assertEquals(4,result.graph().edges().size());
        assertEquals(result.graph(),generator.generate(templates,request).graph());assertTrue(new DungeonGraphValidator().validate(result.graph(),templates).isEmpty());
    }
    @Test void templateGatewaysCanAlsoCloseLoops(){
        var entry=List.of(new DoorSlotEntry("basic",1));
        var templates=List.of(corner("root",RoomCategory.START,true),corner("corner",RoomCategory.GENERIC,true)).stream().map(t->new RoomTemplate(t.id(),t.category(),1,0,t.tags(),t.size(),null,t.doors().stream().map(d->d.withEntries(entry)).toList(),t.markers(),t.featureSlots(),t.lootBindings(),t.structureFile())).toList();
        var door=new DoorTemplate("basic",new IntVector3(1,2,1),Set.of(),List.of(),List.of(),new DoorGateway(IntVector3.ZERO,new IntVector3(1,2,1),Direction3.NORTH),Path.of("basic.nbt"));
        var result=new DeterministicDungeonGenerator(2000,80,()->List.of(door)).generate(templates,new DungeonGenerationRequest(4,42));
        assertTrue(result.successful(),result.errors().toString());assertEquals(4,result.graph().edges().size());assertTrue(new DungeonGraphValidator().validate(result.graph(),templates,List.of(door)).isEmpty());
    }
    @Test void requiredLoopShortcutCannotBypassTeamSeparation(){
        var templates=List.of(corner("root",RoomCategory.START,true),corner("ordinary",RoomCategory.GENERIC,true),corner("spawn",RoomCategory.TEAM_SPAWN,true));
        var spread=new TeamSpread("root",Set.of("ordinary"),"spawn",2,3,1.25);
        var result=new DeterministicDungeonGenerator(300,80).generate(templates,new DungeonGenerationRequest(4,42,spread));
        assertFalse(result.successful());assertTrue(result.errors().toString().contains("20 attempts"));
    }
    @Test void validatorRejectsRequiredSocketsReuseDisconnectedAndSelfEdges(){
        var room=corner("root",RoomCategory.START,true);var other=corner("corner",RoomCategory.GENERIC,true);
        var nodes=List.of(new DungeonNode(0,"root",RoomCategory.START,0,new RoomTransform(IntVector3.ZERO,Rotation.NONE,room.size())),new DungeonNode(1,"corner",RoomCategory.GENERIC,1,new RoomTransform(new IntVector3(10,0,0),Rotation.NONE,other.size())));
        var validator=new DungeonGraphValidator();
        var errors=validator.validate(new DungeonGraph(nodes,List.of()),List.of(room,other));assertTrue(errors.stream().anyMatch(e->e.contains("Required socket")));assertTrue(errors.stream().anyMatch(e->e.contains("disconnected")));
        var duplicate=new DungeonEdge(0,"north",1,"north");errors=validator.validate(new DungeonGraph(nodes,List.of(duplicate,duplicate)),List.of(room,other));assertTrue(errors.stream().anyMatch(e->e.contains("more than once")));
        assertFalse(validator.validate(new DungeonGraph(nodes,List.of(new DungeonEdge(0,"north",0,"east"))),List.of(room,other)).isEmpty());
    }
}
