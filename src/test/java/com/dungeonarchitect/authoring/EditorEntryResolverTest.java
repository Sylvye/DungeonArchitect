package com.dungeonarchitect.authoring;
import com.dungeonarchitect.domain.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EditorEntryResolverTest {
    RoomTemplate room(List<RoomMarker> markers){return new RoomTemplate("room",RoomCategory.GENERIC,1,Set.of(),new IntVector3(7,5,7),null,List.of(),markers,List.of(),Path.of("room.nbt"));}
    RoomTransform transform(){return new RoomTransform(new IntVector3(10,80,10),Rotation.NONE,new IntVector3(7,5,7));}
    Location fallback(){return new Location(null,10.5,82,10.5,123,-25);}
    @Test void authoredSpawnWinsAndLookingDirectionIsPreserved(){
        var area=new RoomMarker("spawn","spawn",new IntVector3(2,0,2),new IntVector3(2,1,2));var room=room(List.of(area,area.renamed("overlap")));
        var entry=EditorEntryResolver.resolve(null,room,transform(),fallback(),42,p->true);assertEquals(EditorEntryResolver.Source.SPAWN,entry.source());assertEquals(81,entry.location().getY());assertEquals(123,entry.location().getYaw());assertEquals(-25,entry.location().getPitch());
        assertEquals(entry,EditorEntryResolver.resolve(null,room,transform(),fallback(),42,p->true));
    }
    @Test void missingAndBlockedAreasUseSafeInteriorFromCenterOut(){
        for(var markers:List.of(List.<RoomMarker>of(),List.of(new RoomMarker("spawn","spawn",IntVector3.ZERO,new IntVector3(1,1,1))))){
            var entry=EditorEntryResolver.resolve(null,room(markers),transform(),fallback(),42,p->p.getX()==13.5&&p.getY()==81&&p.getZ()==13.5);
            assertEquals(EditorEntryResolver.Source.INTERIOR,entry.source());assertEquals(13.5,entry.location().getX());assertEquals(81,entry.location().getY());
        }
        var entry=EditorEntryResolver.resolve(null,room(List.of()),transform(),fallback(),42,p->true);assertEquals(new Location(null,13.5,82,13.5,123,-25),entry.location());
    }
    @Test void unsafeRoomOrInvalidAreaCannotPreventEditing(){
        var invalid=new RoomMarker("invalid","spawn",new IntVector3(-1,0,0),new IntVector3(2,1,2));
        assertEquals(EditorEntryResolver.Source.INTERIOR,EditorEntryResolver.resolve(null,room(List.of(invalid)),transform(),fallback(),42,p->true).source());
        var entry=EditorEntryResolver.resolve(null,room(List.of()),transform(),fallback(),42,p->false);assertEquals(EditorEntryResolver.Source.WORKSPACE,entry.source());assertEquals(fallback(),entry.location());
    }
}
