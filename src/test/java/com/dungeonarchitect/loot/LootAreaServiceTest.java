package com.dungeonarchitect.loot;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.domain.Rotation;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LootAreaServiceTest {
    World world; LootService service; Map<Integer,Inventory> blocks;
    @BeforeEach void setup() {
        MockBukkit.mock(); blocks=new HashMap<>();world=mock(World.class);when(world.getUID()).thenReturn(new UUID(0,1));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{ int x=i.getArgument(0); Block block=mock(Block.class); Inventory inventory=blocks.get(x); BlockState state= inventory==null?mock(BlockState.class):mock(Chest.class);if(state instanceof Chest chest)when(chest.getInventory()).thenReturn(inventory);when(block.getState()).thenReturn(state);return block; });
        LootTableRegistry registry=mock(LootTableRegistry.class);when(registry.get("supply")).thenReturn(Optional.of(new LootTable("supply",List.of(new LootEntry(new ItemStack(Material.DIAMOND),1,1,1)))));service=new LootService(registry,Logger.getAnonymousLogger());
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    private Inventory inventory(int x,int size) {
        Inventory inventory=mock(Inventory.class);when(inventory.getSize()).thenReturn(size);when(inventory.getLocation()).thenReturn(new Location(world,x,0,0));
        Map<Integer,ItemStack> slots=new HashMap<>();when(inventory.getItem(anyInt())).thenAnswer(i->slots.get(i.getArgument(0)));
        doAnswer(i->{ slots.put(i.getArgument(0),i.getArgument(1));return null; }).when(inventory).setItem(anyInt(),any(ItemStack.class));return inventory;
    }
    private RoomMarker area(String name,int x,int width) { return new RoomMarker(name,"loot",new IntVector3(x,0,0),new IntVector3(width,1,1)); }
    private void collect(LootService.Batch batch,String owner,RoomMarker marker,int rolls) {
        try(var scope=service.collect(batch)) { service.placeLoot(world,owner,List.of(marker),Map.of(marker.name(),new LootBinding("supply",rolls,rolls)),new RoomTransform(IntVector3.ZERO,Rotation.NONE,new IntVector3(20,5,20)),123,owner); }
    }
    private int filled(Inventory inventory) { int count=0;for(int i=0;i<inventory.getSize();i++)if(inventory.getItem(i)!=null)count++;return count; }
    @Test void eachSeparateInventoryReceivesItsOwnRollsAfterCompletePlacement() {
        var a=inventory(0,27);var b=inventory(2,27);blocks.put(0,a);blocks.put(2,b);
        var batch=service.batch();collect(batch,"room",area("all",0,3),2);assertEquals(0,filled(a));batch.finish();assertEquals(2,filled(a));assertEquals(2,filled(b));
    }
    private Inventory doubleChest() {
        var combined=inventory(0,54);var chest=mock(DoubleChest.class);var left=mock(InventoryHolder.class);var right=mock(InventoryHolder.class);
        var leftInventory=inventory(0,27);var rightInventory=inventory(1,27);when(left.getInventory()).thenReturn(leftInventory);when(right.getInventory()).thenReturn(rightInventory);when(chest.getLeftSide()).thenReturn(left);when(chest.getRightSide()).thenReturn(right);when(combined.getHolder()).thenReturn(chest);blocks.put(0,combined);blocks.put(1,combined);return combined;
    }
    @Test void selectingBothHalvesOfDoubleChestFillsOnlyOnce() {var combined=doubleChest();var batch=service.batch();collect(batch,"room",area("chest",0,2),3);batch.finish();assertEquals(3,filled(combined));}
    @Test void oppositeHalvesInDifferentOwnersConflictBeforeAnyLootIsWritten() {
        var combined=doubleChest();var batch=service.batch();collect(batch,"room",area("left",0,1),2);collect(batch,"feature",area("right",1,1),2);
        var error=assertThrows(IllegalArgumentException.class,batch::finish);assertTrue(error.getMessage().contains("left"));assertTrue(error.getMessage().contains("right"));assertEquals(0,filled(combined));
    }
    @Test void missingContainersAreActionable() {var batch=service.batch();collect(batch,"room",area("empty",0,3),1);assertTrue(assertThrows(IllegalArgumentException.class,batch::finish).getMessage().contains("contains no containers"));}
    @Test void independentDungeonBatchesCannotConflictOrFillEachOther() {
        var a=inventory(0,27);var b=inventory(5,27);blocks.put(0,a);blocks.put(5,b);var first=service.batch();var second=service.batch();collect(first,"room",area("a",0,1),1);collect(second,"room",area("b",5,1),2);second.finish();assertEquals(0,filled(a));assertEquals(2,filled(b));first.finish();assertEquals(1,filled(a));
    }
    @Test void sameSeedAndLayoutReproduceInventoryContents() {
        var first=inventory(0,27);blocks.put(0,first);var one=service.batch();collect(one,"room",area("a",0,1),4);one.finish();var second=inventory(0,27);blocks.put(0,second);var two=service.batch();collect(two,"room",area("a",0,1),4);two.finish();for(int i=0;i<27;i++)assertEquals(first.getItem(i),second.getItem(i));
    }
}
