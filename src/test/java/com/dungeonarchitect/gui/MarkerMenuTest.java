package com.dungeonarchitect.gui;
import com.dungeonarchitect.authoring.*;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.door.*;
import com.dungeonarchitect.feature.*;
import com.dungeonarchitect.loot.*;
import com.dungeonarchitect.runtime.*;
import com.dungeonarchitect.template.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MarkerMenuTest {
    @TempDir Path directory;
    ServerMock server; PlayerMock player; AuthoringManager authoring; MenuManager menus; RoomTemplateRegistry rooms; FeatureTemplateRegistry features; DoorTemplateRegistry doors; ChatPromptManager prompts; World world;
    @BeforeEach void setup() throws Exception {
        server=MockBukkit.mock();var plugin=MockBukkit.createMockPlugin();world=server.addSimpleWorld("da_edit");player=server.addPlayer();player.setOp(true);player.teleport(new Location(world,1,61,1));
        Path roomDir=directory.resolve("rooms/room");Files.createDirectories(roomDir);Files.writeString(roomDir.resolve("room.nbt"),"original blocks");
        RoomTemplateIO.save(new RoomTemplate("room",RoomCategory.TEAM_SPAWN,1,0,Set.of(),new IntVector3(8,5,8),null,List.of(),List.of(new RoomMarker("chest","loot",new IntVector3(1,1,1),new IntVector3(2,1,1))),List.of(),Map.of("chest",new LootBinding("supplies")),roomDir.resolve("room.nbt")),roomDir);
        features=new FeatureTemplateRegistry(directory.resolve("features"),null);doors=new DoorTemplateRegistry(directory.resolve("doors"),null,features);rooms=new RoomTemplateRegistry(directory.resolve("rooms"),null,features,doors);rooms.reload();
        authoring=spy(new AuthoringManager(plugin,server,directory.resolve("rooms"),directory.resolve("features"),new NamespacedKey(plugin,"wand"),new NamespacedKey(plugin,"selector"),Material.BLAZE_ROD,RoomCategory.GENERIC,10));
        var dungeons=mock(DungeonManager.class);when(dungeons.getDungeon(any(org.bukkit.entity.Player.class))).thenReturn(Optional.empty());
        prompts=new ChatPromptManager(plugin);menus=new MenuManager(plugin,authoring,rooms,features,doors,new LootTableRegistry(directory.resolve("loot")),dungeons,prompts,new AssetRenameCoordinator(rooms,features,doors),new TagCatalog(directory.resolve("tags.yml")),()->{});
        server.getPluginManager().registerEvents(prompts,plugin);server.getPluginManager().registerEvents(menus,plugin);
    }
    @AfterEach void cleanup(){MockBukkit.unmock();}
    private void click(int slot){menus.onClick(new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));}
    private AuthoringSession edit(){var session=authoring.session(player);session.loadTemplateForEdit(rooms.getVisible("room").orElseThrow(),world,new IntVector3(0,60,0));session.selectCurrentBounds(SelectionBounds.between(new IntVector3(1,60,1),new IntVector3(4,60,3)));return session;}
    private void list(){menus.openRoom(player,"room");click(30);}
    @Test void markerClickOpensSharedSettingsAndPurposeChangeSavesMetadataOnly() throws Exception {
        list();click(0);assertEquals("Marker: chest",player.getOpenInventory().getTitle());click(12);click(10);
        var marker=rooms.getVisible("room").orElseThrow().markers().getFirst();assertEquals(RoomMarker.Purpose.GENERIC,marker.purpose());assertEquals(new IntVector3(2,1,1),marker.size());assertTrue(rooms.getVisible("room").orElseThrow().lootBindings().isEmpty());assertEquals("original blocks",Files.readString(directory.resolve("rooms/room/room.nbt")));
    }
    @Test void wandAreaCreationUsesAutomaticNamesAndFullBoundsWithoutSaving() {
        var session=edit();list();click(46);click(12);
        var marker=session.markers().getLast();assertEquals("spawn_1",marker.name());assertEquals(new IntVector3(4,1,3),marker.size());assertEquals(RoomMarker.Purpose.PLAYER_SPAWN,marker.purpose());assertEquals(1,rooms.getVisible("room").orElseThrow().markers().size());
    }
    @Test void boundsReplacementAndRenamePreserveDimensionsAndBindings() {
        var session=edit();list();click(0);click(16);assertEquals(new IntVector3(4,1,3),session.markers().getFirst().size());
        click(10);server.getPluginManager().callEvent(new AsyncPlayerChatEvent(false,player,"supplies",Set.of()));server.getScheduler().performTicks(1);
        assertEquals("supplies",session.markers().getFirst().name());assertEquals(new IntVector3(4,1,3),session.markers().getFirst().size());assertTrue(session.lootBindings().containsKey("supplies"));assertEquals("chest",rooms.getVisible("room").orElseThrow().markers().getFirst().name());
    }
    @Test void cancelDiscardsPurposeAndLootEdits() {
        var session=edit();list();click(0);click(12);click(10);assertTrue(session.lootBindings().isEmpty());click(49);click(51);
        assertFalse(authoring.hasActiveSession(player));assertEquals(RoomMarker.Purpose.LOOT,rooms.getVisible("room").orElseThrow().markers().getFirst().purpose());assertTrue(rooms.getVisible("room").orElseThrow().lootBindings().containsKey("chest"));
    }
    @Test void saveEditCommitsStagedMarkerChangesThroughExistingSaveAction() throws Exception {
        var session=edit();doAnswer(i->{var t=rooms.getVisible("room").orElseThrow();RoomTemplateIO.save(new RoomTemplate(t.id(),t.category(),1,0,t.tags(),t.size(),null,t.doors(),session.markers(),session.featureSlots(),session.lootBindings(),t.structureFile()),t.structureFile().getParent());return new TemplateValidationResult();}).when(authoring).save(player,"room");
        list();click(0);click(12);click(10);click(49);click(50);verify(authoring).save(player,"room");assertEquals(RoomMarker.Purpose.GENERIC,rooms.getVisible("room").orElseThrow().markers().getFirst().purpose());assertTrue(rooms.getVisible("room").orElseThrow().lootBindings().isEmpty());
    }
    @Test void bulkLootEditsStayInTheWorkspaceUntilSave() {
        var session=edit();list();click(44);click(0);click(53);click(0);click(53);
        assertTrue(session.lootBindings().isEmpty());assertTrue(rooms.getVisible("room").orElseThrow().lootBindings().containsKey("chest"));
    }
    @Test void retainedRoomWorkspaceReusesTheSameEntryResolver() {
        edit();doNothing().when(authoring).teleportToRoomEdit(player);list();click(48);verify(authoring).teleportToRoomEdit(player);verify(authoring,never()).editSession(any(),any());
    }
    @Test void anotherActiveWorkspaceCannotBeReplaced() {
        var t=rooms.getVisible("room").orElseThrow();authoring.session(player).loadTemplateForEdit(new RoomTemplate("other",t.category(),1,t.tags(),t.size(),null,List.of(),List.of(),List.of(),Path.of("other.nbt")),world,new IntVector3(0,60,0));
        list();click(48);assertEquals("other",authoring.session(player).roomId());verify(authoring,never()).editSession(any(),any());
    }
    @Test void featureMarkerEditsUseTheSamePurposeAndCancelWorkflow() throws Exception {
        Path target=directory.resolve("features/feature");Files.createDirectories(target);Files.writeString(target.resolve("feature.nbt"),"blocks");
        FeatureTemplateIO.save(new FeatureTemplate("feature",new IntVector3(8,5,8),Set.of(),List.of(new RoomMarker("loot","loot",new IntVector3(1,1,1))),List.of(),Map.of("loot",new LootBinding("supplies")),target.resolve("feature.nbt")),target);features.reload();
        var template=features.getVisible("feature").orElseThrow();authoring.session(player).loadFeatureForEdit(template,world,new IntVector3(0,60,0));
        menus.openFeature(player,"feature");click(28);click(0);click(12);assertNull(player.getOpenInventory().getTopInventory().getItem(12));click(10);
        assertTrue(authoring.session(player).lootBindings().isEmpty());click(49);click(51);assertEquals(RoomMarker.Purpose.LOOT,features.getVisible("feature").orElseThrow().markers().getFirst().purpose());
    }
    @Test void doorMarkerEditsUseTheSamePurposeAndCancelWorkflow() throws Exception {
        Path target=directory.resolve("doors/door");Files.createDirectories(target);Files.writeString(target.resolve("door.nbt"),"blocks");
        DoorTemplateIO.save(new DoorTemplate("door",new IntVector3(8,5,8),Set.of(),List.of(new RoomMarker("loot","loot",new IntVector3(1,1,1))),List.of(),Map.of("loot",new LootBinding("supplies")),new DoorGateway(new IntVector3(2,1,0),new IntVector3(2,2,1),Direction3.NORTH),target.resolve("door.nbt")),target);doors.reload();
        var template=doors.getVisible("door").orElseThrow();authoring.session(player).loadDoorForEdit(template,world,new IntVector3(0,60,0));
        menus.openDoor(player,"door");click(28);click(0);click(12);assertNull(player.getOpenInventory().getTopInventory().getItem(12));click(10);
        assertTrue(authoring.session(player).lootBindings().isEmpty());click(49);click(51);assertEquals(RoomMarker.Purpose.LOOT,doors.getVisible("door").orElseThrow().markers().getFirst().purpose());
    }
    @Test void returnToMatchAndDaNavigationUseTheSameSharedMarkerScreen() {
        final boolean[] returned={false};menus.openRoomSpawnMarkers(player,"room",p->returned[0]=true);assertTrue(menus.ownsMarkerNavigation(player,player.getOpenInventory().getTopInventory()));
        player.closeInventory();menus.openMain(player);assertTrue(player.getOpenInventory().getTitle().startsWith("Spawn Areas:"));click(52);assertTrue(returned[0]);assertFalse(menus.ownsMarkerNavigation(player,player.getOpenInventory().getTopInventory()));
    }
    @Test void staleQueuedClicksCannotCreateAnotherMarker() {
        var session=edit();list();click(46);var stale=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,12,ClickType.LEFT,InventoryAction.PICKUP_ALL);menus.onClick(stale);menus.onClick(stale);assertEquals(2,session.markers().size());
    }
}
