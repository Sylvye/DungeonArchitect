package com.dungeonarchitect.command;
import com.dungeonarchitect.domain.*;
import com.dungeonarchitect.minigame.*;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinigameCommandCompletionTest {
    @TempDir Path directory;
    ServerMock server; PlayerMock admin,participant; MinigameService games; MinigameCommand commands; DungeonArchitectCommand da; MinigameRun run,ended; Command adminCommand,playerCommand;
    @BeforeEach void setup(){
        server=MockBukkit.mock();server.addSimpleWorld("world");admin=server.addPlayer("Admin");admin.setOp(true);participant=server.addPlayer("Participant");
        var plugin=spy(MockBukkit.createMockPlugin());participant.addAttachment(plugin,"dungeonarchitect.play",true);doReturn(directory.toFile()).when(plugin).getDataFolder();var templates=mock(RoomTemplateRegistry.class);
        when(templates.visible()).thenReturn(List.of(room("root",RoomCategory.START),room("hall",RoomCategory.GENERIC),room("spawn",RoomCategory.TEAM_SPAWN)));
        games=spy(new MinigameService(plugin,mock(DungeonManager.class),templates));doReturn(List.of("bow","sword","food")).when(games).availableKits();
        games.presets().create(new MinigamePreset("arena","root",List.of("hall"),List.of("spawn"),16,4,1.25,List.of("bow","sword")));
        run=games.createRun("arena",42);ended=games.createRun("arena",99);games.end(ended.id(),"test");
        commands=new MinigameCommand(games,mock(MinigameMenus.class));da=new DungeonArchitectCommand("test",null,templates,null,null,null,null);da.minigames(commands);
        adminCommand=mock(Command.class);when(adminCommand.getName()).thenReturn("dminigame");playerCommand=mock(Command.class);when(playerCommand.getName()).thenReturn("dplay");
    }
    @AfterEach void cleanup(){games.close();MockBukkit.unmock();}
    private RoomTemplate room(String id,RoomCategory category){return new RoomTemplate(id,category,1,Set.of(),new IntVector3(5,4,5),null,List.of(),List.of(),List.of(),Path.of(id+".nbt"));}
    private List<String> complete(String value){return commands.onTabComplete(admin,adminCommand,"dmg",value.split(" ",-1));}
    private void offers(String value,String... expected){var actual=complete(value);for(String item:expected)assertTrue(actual.contains(item),value+" -> "+actual);}
    @Test void everyExecutableAdminAndPresetActionHasCompletion() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/dungeonarchitect/minigame/MinigameCommand.java"));
        assertEquals(CommandCompletionCoverageTest.cases(source,"    private void admin(","    private void preset("),new HashSet<>(complete("")));
        assertEquals(CommandCompletionCoverageTest.cases(source,"    private void preset(","    private void player("),new HashSet<>(complete("preset ")));
        offers("preset create ","preset_1");offers("preset duplicate ","arena");offers("preset duplicate arena ","new_id");offers("preset set ","arena");
        assertEquals(new HashSet<>(MinigamePreset.FIELDS),new HashSet<>(complete("preset set arena ")));
    }
    @Test void everyPresetFieldHasContextualValueCompletion() {
        offers("preset set arena root ","root");offers("preset set arena rooms ","hall","none");offers("preset set arena spawns ","spawn","none");
        assertFalse(complete("preset set arena spawns ").contains("root"));assertFalse(complete("preset set arena rooms ").contains("spawn"));
        offers("preset set arena kits ","bow","sword","none");offers("preset set arena count ","16","32");offers("preset set arena distance ","4");offers("preset set arena ratio ","1.25");
        offers("create ","arena");offers("create arena ","42");offers("spawns ","root","hall","spawn");
    }
    @Test void runPlayerAndTeamArgumentsHaveLiveSuggestions() {
        for(String action:List.of("assign","balance","kits","prepare","start","inspect","cancel","end"))offers(action+" ",run.id().toString());
        offers("inspect ",ended.id().toString());assertFalse(complete("start ").contains(ended.id().toString()));
        offers("assign "+run.id()+" ","Admin","Participant");offers("assign "+run.id()+" Participant ","0","1","8");offers("balance "+run.id()+" ","2","8");
        offers("balance "+run.id()+" 2 Admin,Pa","Admin,Participant");assertFalse(complete("balance "+run.id()+" 2 Admin,").contains("Admin,Admin"));
        offers("kits "+run.id()+" ","bow","sword","none");
    }
    @Test void listArgumentsCompleteTheCurrentElementAndExcludeAlreadySelectedValues() {
        offers("preset set arena kits bow,s","bow,sword");assertFalse(complete("preset set arena kits bow,").contains("bow,bow"));
        offers("preset set arena kits bow ","sword","food");assertFalse(complete("preset set arena kits bow ").contains("bow"));assertFalse(complete("preset set arena kits bow ").contains("none"));
        offers("kits "+run.id()+" bow,s","bow,sword");assertTrue(complete("kits "+run.id()+" none ").isEmpty());assertTrue(complete("kits "+run.id()+" none,").isEmpty());
    }
    @Test void aliasesCaseFilteringPermissionsAndTerminalCommandsAreConsistent() {
        assertEquals(complete(""),da.onTabComplete(admin,null,"da",new String[]{"minigame",""}));
        assertEquals(complete("preset set arena kits s"),da.onTabComplete(admin,null,"da",new String[]{"MINIGAME","PRESET","SET","arena","KITS","s"}));
        assertEquals(List.of("spawn"),CommandSuggestions.matching("SPA",CommandSuggestions.markerPurposes(true)));
        assertTrue(commands.onTabComplete(participant,adminCommand,"dmg",new String[]{""}).isEmpty());
        for(String terminal:List.of("list","preset list","preset create name","preset duplicate arena name","spawns spawn","assign "+run.id()+" Participant 1","balance "+run.id()+" 2 Participant","prepare "+run.id(),"start "+run.id(),"inspect "+run.id(),"cancel "+run.id(),"end "+run.id()))assertTrue(complete(terminal+" ").isEmpty(),terminal);
        for(String invalid:List.of("bogus ","preset bogus ","preset list arena ","preset set arena bogus "))assertTrue(complete(invalid).isEmpty(),invalid);
    }
    @Test void participantKitCompletionUsesOnlyTheirRunAndNeverRequiresAdmin() {
        assertFalse(participant.hasPermission("dungeonarchitect.admin"));assertTrue(participant.hasPermission("dungeonarchitect.play"));
        assertEquals(List.of("exit","kit"),commands.onTabComplete(participant,playerCommand,"dplay",new String[]{""}));
        doReturn(Optional.of(run)).when(games).getRun(participant);
        assertEquals(List.of("bow","random","sword"),commands.onTabComplete(participant,playerCommand,"dplay",new String[]{"kit",""}));
        assertEquals(List.of("sword"),commands.onTabComplete(participant,playerCommand,"dplay",new String[]{"KIT","S"}));
        assertTrue(commands.onTabComplete(participant,playerCommand,"dplay",new String[]{"exit",""}).isEmpty());assertTrue(commands.onTabComplete(participant,playerCommand,"dplay",new String[]{"bogus",""}).isEmpty());
        verify(games,never()).chooseKit(any(),anyString());assertEquals(2,games.runs().size());
    }
}
