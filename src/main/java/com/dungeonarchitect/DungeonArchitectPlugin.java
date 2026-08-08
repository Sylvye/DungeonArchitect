package com.dungeonarchitect;

import com.dungeonarchitect.api.DungeonArchitectAPI;
import com.dungeonarchitect.api.DungeonArchitectService;
import com.dungeonarchitect.authoring.AuthoringListener;
import com.dungeonarchitect.authoring.AuthoringManager;
import com.dungeonarchitect.authoring.SelectionParticleTask;
import com.dungeonarchitect.command.DungeonArchitectCommand;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.feature.FeaturePoolRegistry;
import com.dungeonarchitect.feature.FeatureService;
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
    private FeaturePoolRegistry featurePoolRegistry;
    private DungeonArchitectAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("feature-pools.yml", false);

        Path dataPath = getDataFolder().toPath();
        RoomStructureService structureService = new RoomStructureService(getServer());
        roomTemplateRegistry = new RoomTemplateRegistry(dataPath.resolve("rooms"), structureService);
        var validation = roomTemplateRegistry.reload();
        if (!validation.valid()) {
            validation.errors().forEach(error -> getLogger().warning(error));
        }

        featurePoolRegistry = new FeaturePoolRegistry(dataPath.resolve("feature-pools.yml").toFile());
        featurePoolRegistry.reload().forEach(error -> getLogger().warning(error));

        int maxAttempts = getConfig().getInt("generation.max-placement-attempts", 250);
        int spawnY = getConfig().getInt("worlds.spawn-y", 80);
        String worldPrefix = getConfig().getString("worlds.name-prefix", "da_");
        boolean deleteOnDestroy = getConfig().getBoolean("worlds.delete-on-destroy", true);

        FeatureService featureService = new FeatureService(featurePoolRegistry);
        RoomStructurePlacer structurePlacer = new RoomStructurePlacer(structureService, featureService);
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
            getServer(),
            roomTemplateRegistry.roomsDirectory(),
            new NamespacedKey(this, "authoring_wand"),
            wandMaterial,
            defaultCategory,
            defaultWeight
        );

        getServer().getPluginManager().registerEvents(new AuthoringListener(authoringManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRoomListener(dungeonManager), this);
        getServer().getScheduler().runTaskTimer(this, new SelectionParticleTask(authoringManager), 10L, 10L);

        ChatPromptManager chatPromptManager = new ChatPromptManager(this);
        MenuManager menuManager = new MenuManager(this, roomTemplateRegistry, dungeonManager, chatPromptManager, this::reloadContent);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        getServer().getPluginManager().registerEvents(menuManager, this);

        DungeonArchitectCommand command = new DungeonArchitectCommand(getPluginMeta().getVersion(), authoringManager, roomTemplateRegistry, dungeonManager, featurePoolRegistry::reload, featurePoolRegistry::poolIds, menuManager, structureService);
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
        roomTemplateRegistry.reload();
        featurePoolRegistry.reload();
    }
}
