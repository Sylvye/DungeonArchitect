package com.dungeonarchitect.generation;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.door.DoorTemplateMatcher;
import java.util.*;

/** Metadata-only expansion preview. Physical collisions are checked by generation. */
public record DoorSlotAvailability(boolean available, List<String> reasons) {
    public DoorSlotAvailability { reasons=List.copyOf(reasons); }
    public static DoorSlotAvailability assess(RoomTemplate owner, DoorSocket slot, Collection<RoomTemplate> rooms, Collection<DoorTemplate> doors) {
        boolean templateMode=!doors.isEmpty();
        List<DoorTemplate> own=choices(slot,doors);
        if(templateMode&&own.isEmpty()){
            boolean assigned=slot.entries().stream().anyMatch(e->!e.doorId().equals(DoorSlotEntry.EMPTY));
            return new DoorSlotAvailability(false,List.of(assigned ? "Assigned door templates are missing or incompatible with this slot." : "No usable door template assigned.","Select a compatible template in this slot's settings."));
        }
        Set<String> rejected=new LinkedHashSet<>();
        for(RoomTemplate room:rooms){
            if(room.category()==RoomCategory.START)continue;
            for(DoorSocket candidate:room.doors()){
                var match=slot.connectionMatch(candidate,owner.tags(),room.tags());
                if(!match.compatible()){rejected.add("Rule restriction: "+match.reason());continue;}
                for(Rotation rotation:Rotation.values()){
                    if(candidate.facing().rotateY(rotation)!=slot.facing().opposite())continue;
                    if(!templateMode){if(DoorGeometry.relativeBounds(candidate,rotation,room.size()).size().equals(slot.size()))return ready();}
                    else for(DoorTemplate from:own)for(DoorTemplate to:choices(candidate,doors)){
                        var fromTransform=DoorGeometry.doorTransform(slot,from,new RoomTransform(IntVector3.ZERO,Rotation.NONE,owner.size()));
                        var toTransform=DoorGeometry.doorTransform(candidate,to,new RoomTransform(IntVector3.ZERO,rotation,room.size()));
                        if(DoorGeometry.gatewayFacing(from,fromTransform).opposite()==DoorGeometry.gatewayFacing(to,toTransform)&&DoorGeometry.transformedBounds(from.gateway(),fromTransform).size().equals(DoorGeometry.transformedBounds(to.gateway(),toTransform).size()))return ready();
                    }
                }
            }
        }
        List<String> reasons=new ArrayList<>();reasons.add("No compatible expansion candidate.");rejected.stream().limit(2).forEach(reasons::add);
        reasons.add("Check opposite gateway sizes, assignments, and room-tag rules.");return new DoorSlotAvailability(false,reasons);
    }
    private static DoorSlotAvailability ready(){return new DoorSlotAvailability(true,List.of("Compatible expansion candidates exist.","Placement may still be blocked by collisions.","No per-room connection cap is applied."));}
    private static List<DoorTemplate> choices(DoorSocket slot,Collection<DoorTemplate> templates){return templates.stream().filter(t->slot.entries().stream().anyMatch(e->e.doorId().equals(t.id()))&&DoorTemplateMatcher.matches(slot,t)).toList();}
}
