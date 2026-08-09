package com.dungeonarchitect;

import com.dungeonarchitect.api.DungeonArchitectAPI;
import com.dungeonarchitect.api.DungeonArchitectService;
import com.dungeonarchitect.authoring.AuthoringListener;
import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.SelectionParticleTask;
import com.dungeonarchitect.command.DungeonArchitectCommand;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.door.DoorService;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureService;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;
import com.dungeonarchitect.gui.ChatPromptManager;
import com.dungeonarchitect.gui.MenuManager;
import com.dungeonarchitect.generation.DeterministicDungeonGenerator;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.runtime.DungeonWorldManager;
import com.dungeonarchitect.runtime.PlayerRoomListener;
import com.dungeonarchitect.runtime.RoomStructurePlacer;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomStructureService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Locale;

public final class DungeonArchitectPlugin extends JavaPlugin {
    private DungeonManager dungeonManager;
    private RoomTemplateRegistry roomTemplateRegistry;
    private FeatureTemplateRegistry featureTemplateRegistry;
    private DoorTemplateRegistry doorTemplateRegistry;
    private DungeonArchitectAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Path dataPath = getDataFolder().toPath();
        RoomStructureService structureService = new RoomStructureService(getServer());
        featureTemplateRegistry = new FeatureTemplateRegistry(dataPath.resolve("features"), structureService);
        featureTemplateRegistry.reload().errors().forEach(error -> getLogger().warning(error));
        doorTemplateRegistry = new DoorTemplateRegistry(dataPath.resolve("doors"), structureService);
        doorTemplateRegistry.reload().errors().forEach(error -> getLogger().warning(error));
        roomTemplateRegistry = new RoomTemplateRegistry(dataPath.resolve("rooms"), structureService, featureTemplateRegistry, doorTemplateRegistry);
        var validation = roomTemplateRegistry.reload();
        if (!validation.valid()) {
            validation.errors().forEach(error -> getLogger().warning(error));
        }

        int maxAttempts = getConfig().getInt("generation.max-placement-attempts", 250);
        int spawnY = getConfig().getInt("worlds.spawn-y", 80);
        String worldPrefix = getConfig().getString("worlds.name-prefix", "da_");
        boolean deleteOnDestroy = getConfig().getBoolean("worlds.delete-on-destroy", true);

        FeatureService featureService = new FeatureService(featureTemplateRegistry, structureService, getLogger());
        DoorService doorService = new DoorService(doorTemplateRegistry, structureService, featureService, getLogger());
        RoomStructurePlacer structurePlacer = new RoomStructurePlacer(structureService, featureService, doorService);
        DungeonWorldManager worldManager = new DungeonWorldManager(this, worldPrefix, deleteOnDestroy);
        dungeonManager = new DungeonManager(
            this,
            roomTemplateRegistry,
            new DeterministicDungeonGenerator(maxAttempts, spawnY),
            worldManager,
            structurePlacer
        );

        Material wandMaterial = Material.matchMaterial(getConfig().getString("authoring.wand-material", "BLAZE_ROD"));
        if (wandMaterial == null) {
            wandMaterial = Material.BLAZE_ROD;
        }
        RoomCategory defaultCategory = RoomCategory.valueOf(getConfig().getString("authoring.default-category", "GENERIC").toUpperCase(Locale.ROOT));
        int defaultWeight = getConfig().getInt("authoring.default-weight", 10);
        AuthoringManager authoringManager = new AuthoringManager(
            this,
            getServer(),
            roomTemplateRegistry.roomsDirectory(),
            featureTemplateRegistry.featuresDirectory(),
            new NamespacedKey(this, "authoring_wand"),
            new NamespacedKey(this, "authoring_selector"),
            wandMaterial,
            defaultCategory,
            defaultWeight
        );

        getServer().getPluginManager().registerEvents(new AuthoringListener(authoringManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRoomListener(dungeonManager), this);
        getServer().getScheduler().runTaskTimer(this, new SelectionParticleTask(authoringManager), 10L, 10L);
        getServer().getScheduler().runTaskLater(this, () -> {
            worldManager.warmupWorld();
            authoringManager.prepareEditWorld();
        }, 20L);

        ChatPromptManager chatPromptManager = new ChatPromptManager(this);
        MenuManager menuManager = new MenuManager(this, authoringManager, roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry, dungeonManager, chatPromptManager, this::reloadContent);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        getServer().getPluginManager().registerEvents(menuManager, this);

        DungeonArchitectCommand command = new DungeonArchitectCommand(getPluginMeta().getVersion(), authoringManager, roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry, dungeonManager, menuManager, structureService);
        PluginCommand da = getCommand("da");
        if (da == null) {
            throw new IllegalStateException("Missing /da command registration");
        }
        da.setExecutor(command);
        da.setTabCompleter(command);

        api = new DungeonArchitectService(dungeonManager, roomTemplateRegistry);
        getServer().getServicesManager().register(DungeonArchitectAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("DungeonArchitect enabled with " + roomTemplateRegistry.all().size() + " room templates.");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            for (var instance : dungeonManager.instances()) {
                dungeonManager.destroyDungeon(instance.id());
            }
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public DungeonArchitectAPI api() {
        return api;
    }

    private void reloadContent() {
        reloadConfig();
        featureTemplateRegistry.reload();
        doorTemplateRegistry.reload();
        roomTemplateRegistry.reload();
    }
}
