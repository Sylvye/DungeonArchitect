package com.dungeonarchitect.generation;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.template.RoomTemplateIO;
import com.dungeonarchitect.door.DoorTemplateIO;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerLayoutRegressionTest {
    static Path fixtures(){return Path.of("src/test/resources/generation/server-layout");}
    static List<RoomTemplate> rooms(){return List.of("start","hallway","large").stream().map(id->RoomTemplateIO.load(fixtures().resolve(id))).toList();}
    @Test void savedServerTemplatesGenerateBranchesAndHonorEveryHallwayEnd(){
        var rooms=rooms();var basic=DoorTemplateIO.load(fixtures().resolve("basic"));
        for(int count:List.of(8,16,32))for(long seed:List.of(1L,42L,12345L)){
            var generator=new DeterministicDungeonGenerator(250,80,()->List.of(basic));
            var result=generator.generate(rooms,new DungeonGenerationRequest(count,seed));
            assertTrue(result.successful(),count+"/"+seed+": "+result.errors());
            var graph=result.graph();assertEquals(count,graph.nodes().size());
            var used=new HashSet<String>();int[] degrees=new int[count];
            for(var edge:graph.edges()){assertTrue(used.add(edge.fromNode()+":"+edge.fromDoorId()));assertTrue(used.add(edge.toNode()+":"+edge.toDoorId()));degrees[edge.fromNode()]++;degrees[edge.toNode()]++;}
            for(var node:graph.nodes())if(node.templateId().equals("hallway")){assertTrue(used.contains(node.index()+":door_1"));assertTrue(used.contains(node.index()+":door_2"));}
            assertTrue(Arrays.stream(degrees).anyMatch(n->n>=3),"Layout stayed a chain for "+count+"/"+seed);
        }
    }    @Test void branchesBeyondRootAtSmallAndLargeRoomCounts() {
        for(int count:List.of(16,32,100))for(long seed:List.of(1L,42L,12345L)){
            var controlled=List.of(TeamSpreadGenerationTest.room("root",RoomCategory.START),TeamSpreadGenerationTest.room("ordinary",RoomCategory.GENERIC));
            var result=new DeterministicDungeonGenerator(250,80).generate(controlled,new DungeonGenerationRequest(count,seed));
            assertTrue(result.successful(),count+"/"+seed+": "+result.errors());
            int[] degrees=new int[count];for(var edge:result.graph().edges()){degrees[edge.fromNode()]++;degrees[edge.toNode()]++;}
            long branching=result.graph().nodes().stream().filter(n->n.category()!=RoomCategory.START&&degrees[n.index()]>=3).count();
            assertTrue(branching>=2,"Only "+branching+" non-start branches for "+count+"/"+seed);
        }
    }
    @Test void currentServerTemplatesKeepRestrictionsAndBranchBeyondRoot() {
        Path root=Path.of("src/test/resources/generation/server-layout-current");
        var templates=List.of("start","hallway","large","medium","spawn").stream().map(id->RoomTemplateIO.load(root.resolve("rooms").resolve(id))).toList();
        var doors=List.of("basic","diorite-arch").stream().map(id->DoorTemplateIO.load(root.resolve("doors").resolve(id))).toList();
        for(int count:List.of(16,32,100)){
            var generator=new DeterministicDungeonGenerator(250,80,()->doors);var request=new DungeonGenerationRequest(count,1);
            var result=generator.generate(templates,request);assertTrue(result.successful(),count+": "+result.errors());
            assertEquals(result.graph(),generator.generate(templates,request).graph());
            int[] degrees=new int[count];for(var edge:result.graph().edges()){degrees[edge.fromNode()]++;degrees[edge.toNode()]++;}
            assertTrue(result.graph().nodes().stream().filter(n->n.category()!=RoomCategory.START&&degrees[n.index()]>=3).count()>=(count==16?1:2),"Count "+count+" degrees="+Arrays.toString(degrees)+" templates="+result.graph().nodes().stream().map(n->n.templateId()).toList());
            assertTrue(new DungeonGraphValidator().validate(result.graph(),templates,doors).isEmpty());
        }
    }

}
