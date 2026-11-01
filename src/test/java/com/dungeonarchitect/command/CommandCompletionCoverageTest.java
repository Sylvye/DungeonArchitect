package com.dungeonarchitect.command;

import com.dungeonarchitect.authoring.*;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.runtime.*;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandCompletionCoverageTest {
    ServerMock server; PlayerMock admin, guest; AuthoringManager authoring; DungeonArchitectCommand command;
    RoomTemplate room; DoorTemplate door; FeatureTemplate feature;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); var world=server.addSimpleWorld("world"); var plugin=MockBukkit.createMockPlugin(); admin=server.addPlayer("Admin");admin.setOp(true);guest=server.addPlayer("Guest");
        var rooms=mock(RoomTemplateRegistry.class);var doors=mock(DoorTemplateRegistry.class);var features=mock(FeatureTemplateRegistry.class);var dungeons=mock(DungeonManager.class);
        var markers=List.of(new RoomMarker("marker_1","generic",new IntVector3(1,1,1)));
        var slots=List.of(new RoomFeatureSlot("feature_1",new IntVector3(2,1,1),new IntVector3(1,1,1),Direction3.NORTH));
        room=new RoomTemplate("crypt",RoomCategory.GENERIC,1,Set.of(),new IntVector3(8,5,8),null,List.of(),markers,slots,Path.of("room.nbt"));
        door=new DoorTemplate("arch",room.size(),Set.of(),markers,slots,null,Path.of("door.nbt"));feature=new FeatureTemplate("cache",room.size(),Set.of(),markers,slots,Map.of(),Path.of("feature.nbt"));
        when(rooms.visible()).thenReturn(List.of(room));when(doors.visible()).thenReturn(List.of(door));when(features.visible()).thenReturn(List.of(feature));
        var instance=new DungeonInstance(UUID.randomUUID(),1,null,"world",Set.of(),List.of(mock(RoomInstance.class),mock(RoomInstance.class)),DungeonState.READY);
        when(dungeons.instanceLabels()).thenReturn(List.of("#7"));when(dungeons.instances()).thenReturn(List.of(instance));when(dungeons.getDungeon(admin)).thenReturn(Optional.of(instance));when(dungeons.getDungeonByAliasOrId("#7")).thenReturn(Optional.of(instance));
        authoring=new AuthoringManager(plugin,server,Path.of("build/completion-rooms"),Path.of("build/completion-features"),new NamespacedKey(plugin,"wand"),new NamespacedKey(plugin,"selector"),Material.BLAZE_ROD,RoomCategory.GENERIC,1);
        command=new DungeonArchitectCommand("test",authoring,rooms,features,doors,dungeons,null,null);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    private List<String> complete(String text){return command.onTabComplete(admin,null,"da",text.split(" ",-1));}
    private void offers(String text,String... values){var found=complete(text);for(String value:values)assertTrue(found.contains(value),text+" -> "+found);}
    private void activate(String owner){var session=authoring.session(admin);var world=server.getWorld("world");switch(owner){case "room"->session.loadTemplateForEdit(room,world,IntVector3.ZERO);case "door"->session.loadDoorForEdit(door,world,IntVector3.ZERO);default->session.loadFeatureForEdit(feature,world,IntVector3.ZERO);}}

    @Test void everyExecutableRootAndTemplateActionIsSuggested() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/dungeonarchitect/command/DungeonArchitectCommand.java"));
        assertEquals(cases(source,"            switch (args[0]","    private void help"),new HashSet<>(complete("")));
        for(String owner:List.of("room","door","feature")){
            String start=owner.equals("room")?"    private void room(":"    private void "+owner+"Command(";
            String end=owner.equals("room")?"    private void rooms(":owner.equals("feature")?"    private void doorCommand(":"    private void component(";
            assertEquals(cases(source,start,end),new HashSet<>(complete(owner+" ")),owner);
        }
    }
    static Set<String> cases(String source,String from,String until){
        int start=source.indexOf(from);assertTrue(start>=0,from);int end=source.indexOf(until,start);assertTrue(end>start,until);
        var branches=Pattern.compile("case ([^\n]+?) ->").matcher(source.substring(start,end));Set<String> cases=new HashSet<>();
        while(branches.find()){var quoted=Pattern.compile("\"([^\"]+)\"").matcher(branches.group(1));while(quoted.find())cases.add(quoted.group(1));}return cases;
    }
    @Test void markerCreationPurposeAndRemovalCompleteForEveryOwner() {
        for(String owner:List.of("room","door","feature")){
            activate(owner);offers(owner+" marker ","add","area","purpose","remove");
            for(String action:List.of("add","area","purpose")){
                offers(owner+" marker "+action+" name ","generic","loot");
                if(owner.equals("room"))offers(owner+" marker "+action+" name ","spawn");
                else assertFalse(complete(owner+" marker "+action+" name ").contains("spawn"));
            }
            offers(owner+" marker remove ","marker_1");offers(owner+" marker purpose ","marker_1");
            assertFalse(complete(owner+" marker add ").contains("marker_1"));assertFalse(complete(owner+" marker add name loot ").contains("Guest"));
        }
    }
    @Test void templatesCompleteExistingNewAndUnsavedIdsAtAllSupportedPositions() {
        for(var entry:Map.of("room","crypt","door","arch","feature","cache").entrySet()){
            String owner=entry.getKey(), id=entry.getValue();activate(owner);
            for(String action:List.of("edit","inspect","delete","rename","duplicate","save","validate")) offers(owner+" "+action+" ",id);
            offers(owner+" validate ","all");offers("diagnose "+owner+" ","all",id);offers(owner+" create ",owner+"_1");
            for(String action:List.of("rename","duplicate"))offers(owner+" "+action+" "+id+" ","new_id");
            authoring.session(admin).roomId("unsaved");offers(owner+" save ","unsaved");
        }
        activate("room");offers("rooms ","edit");offers("rooms edit ","crypt");
    }
    @Test void componentAndDoorAliasArgumentsMatchTheirHandlers() {
        for(String owner:List.of("room","door","feature")){
            activate(owner);for(String action:List.of("select","remove","bounds","rename"))offers(owner+" component "+action+" marker ","marker_1");
            offers(owner+" component rename marker marker_1 ","new_id");offers(owner+" component rotate ","N","north","up","down");
            offers(owner+" component face ","S","south");offers(owner+" feature ","feature_1_2");
            assertTrue(complete(owner+" component rotate N ").isEmpty());
        }
        activate("door");assertFalse(complete("door component rename ").contains("gateway"));assertTrue(complete("door component rename gateway ").isEmpty());
        activate("room");offers("room door ","add","door_1");offers("room door add ","door_1");
        for(String prefix:List.of("room door slot ","room door add slot "))offers(prefix,"STANDARD","STAIRS_UP");
        for(String prefix:List.of("room door slot STANDARD ","room door add slot STANDARD "))offers(prefix,"NORTH","UP","DOWN");
    }
    @Test void generationDebugTeleportAndDestructionArgumentsAreCovered() {
        offers("generate ","5","32");offers("generate 10 ","0","42");offers("diagnose ","all","room","door","feature","generation");
        offers("debug ","instance","room");offers("debug instance ","#7");offers("destroy ","#7");offers("teleport ","#7","0","1");offers("teleport #7 ","0","1");
    }
    @Test void completionIsReadOnlyCaseInsensitiveAndDoesNotLeakPlayerFallbacks() {
        assertFalse(authoring.hasActiveSession(admin));offers("ROOM MARKER AREA floor SPA","spawn");assertFalse(authoring.hasActiveSession(admin));
        assertEquals(List.of("loot"),complete("room marker area floor LO"));
        for(String terminal:List.of("help","version","reload","wand","selector","gui","features","doors","loot","config","dungeons","exit","list","debug room","door gateway","room bounds","room pos1","room pos2","room cancel"))assertTrue(complete(terminal+" ").isEmpty(),terminal);
        assertTrue(command.onTabComplete(guest,null,"da",new String[]{""}).isEmpty());assertTrue(complete("nonsense ").isEmpty());
    }
    @Test void doorMarkerAreaAndPurposeRoutesUseTheSharedHandlers() {
        var manager=mock(AuthoringManager.class);var session=new AuthoringSession("door");session.doorSession(true);when(manager.session(admin)).thenReturn(session);
        var executor=new DungeonArchitectCommand("test",manager,null,null,null,null,null,null);
        assertTrue(executor.onCommand(admin,null,"da",new String[]{"door","marker","area","floor","loot"}));verify(manager).addMarkerArea(admin,"floor","loot");
        assertTrue(executor.onCommand(admin,null,"da",new String[]{"door","marker","purpose","floor","generic"}));verify(manager).markerPurpose(admin,"floor",RoomMarker.Purpose.GENERIC);
    }
}
