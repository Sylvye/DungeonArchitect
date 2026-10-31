package com.dungeonarchitect.runtime;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.domain.Rotation;
import com.dungeonarchitect.runtime.SpawnAreaException;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpawnAreaResolverTest {
    private RoomTemplate room(List<RoomMarker> markers) { return new RoomTemplate("start",RoomCategory.START,1,Set.of(),new IntVector3(8,6,8),null,List.of(),markers,List.of(),Path.of("room.nbt")); }
    private RoomMarker area(String name, IntVector3 position, IntVector3 size) { return new RoomMarker(name,"player_spawn_area",position,size); }
    @Test void combinesOverlappingAreasAndSupportsSteppedFloors() {
        var room = room(List.of(area("flat",IntVector3.ZERO,new IntVector3(3,1,3)),area("overlap",new IntVector3(1,0,1),new IntVector3(2,1,2)),area("step",new IntVector3(4,1,4),new IntVector3(1,1,1))));
        var positions = SpawnAreaResolver.candidates(room);
        assertEquals(10,positions.size()); assertTrue(positions.contains(new IntVector3(4,2,4)));
    }
    @Test void rotationsAndSafetyAreAppliedBeforeAllocation() {
        var room = room(List.of(area("floor",new IntVector3(1,0,2),new IntVector3(3,1,2))));
        var transform = new RoomTransform(new IntVector3(100,80,20),Rotation.CLOCKWISE_90,room.size());
        var positions = SpawnAreaResolver.safePositions(null,room,transform,p -> p.getZ() == 21.5);
        assertEquals(2,positions.size()); assertEquals(81,positions.getFirst().getY());
        var error=assertThrows(SpawnAreaException.class,()->SpawnAreaResolver.resolve(null,room,transform,3,1,p -> p.getZ()==21.5));
        assertTrue(error.getMessage().contains("2 safe spawn positions"));
    }
    @Test void spreadingIsStableDistinctAndGreedilyMaximizesSeparation() {
        List<Location> candidates=new ArrayList<>();for(int x=0;x<7;x++)for(int z=0;z<7;z++)candidates.add(new Location(null,x+.5,1,z+.5));
        var selected=SpawnAreaResolver.spread(candidates,4,123);
        Collections.reverse(candidates); assertEquals(selected,SpawnAreaResolver.spread(candidates,4,123));assertEquals(4,new HashSet<>(selected).size());
        for(int n=1;n<selected.size();n++) { var prefix=selected.subList(0,n); double actual=nearest(selected.get(n),prefix);for(Location candidate:candidates) if(!prefix.contains(candidate)) assertTrue(actual>=nearest(candidate,prefix)); }
    }
    private double nearest(Location p,List<Location> selected) { return selected.stream().mapToDouble(q -> Math.pow(q.getX()-p.getX(),2)+Math.pow(q.getZ()-p.getZ(),2)).min().orElse(0); }
    @Test void missingAuthoredAreaAndInsufficientHeadroomCannotUseFallback() {
        assertThrows(SpawnAreaException.class,()->SpawnAreaResolver.resolve(null,room(List.of()),new RoomTransform(IntVector3.ZERO,Rotation.NONE,new IntVector3(8,6,8)),1,0,p->true));
        assertTrue(SpawnAreaResolver.candidates(room(List.of(area("ceiling",new IntVector3(1,5,1),new IntVector3(1,1,1))))).isEmpty());
    }
    @Test void safetyRejectsHazardsLiquidsAndObstructions() {
        World world=mock(World.class); Block floor=mock(Block.class),body=mock(Block.class),head=mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenAnswer(i -> { Location l=i.getArgument(0);return l.getBlockY()==0?floor:l.getBlockY()==1?body:head; });
        when(floor.getType()).thenReturn(Material.STONE); when(floor.getBoundingBox()).thenReturn(new BoundingBox(0,0,0,1,1,1));when(floor.getY()).thenReturn(0);
        when(body.getType()).thenReturn(Material.AIR);when(head.getType()).thenReturn(Material.AIR);when(body.isPassable()).thenReturn(true);when(head.isPassable()).thenReturn(true);
        Location feet=new Location(world,.5,1,.5);assertTrue(SpawnAreaResolver.safe(feet));
        when(head.isPassable()).thenReturn(false);assertFalse(SpawnAreaResolver.safe(feet));when(head.isPassable()).thenReturn(true);
        when(body.isLiquid()).thenReturn(true);assertFalse(SpawnAreaResolver.safe(feet));when(body.isLiquid()).thenReturn(false);
        when(floor.getType()).thenReturn(Material.MAGMA_BLOCK);assertFalse(SpawnAreaResolver.safe(feet));
    }
}
