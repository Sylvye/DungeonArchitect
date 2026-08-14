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
import com.dungeonarchitect.feature.FeatureNestingPolicy;
import com.dungeonarchitect.gui.ChatPromptManager;
import com.dungeonarchitect.gui.MenuManager;
import com.dungeonarchitect.gui.TagCatalog;
import com.dungeonarchitect.loot.LootService;
import com.dungeonarchitect.loot.LootTableRegistry;
import com.dungeonarchitect.loot.LootRollMigration;
import com.dungeonarchitect.generation.DeterministicDungeonGenerator;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.runtime.DungeonWorldManager;
import com.dungeonarchitect.runtime.PlayerRoomListener;
import com.dungeonarchitect.runtime.RoomStructurePlacer;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import com.dungeonarchitect.template.RoomStructureService;
import com.dungeonarchitect.template.AssetRenameCoordinator;
import com.dungeonarchitect.template.TemplateDiagnostics;
import com.dungeonarchitect.template.TemplateValidationResult;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Locale;

public final class DungeonArchitectPlugin extends JavaPlugin {
    private DungeonManager dungeonManager;
    private RoomStructureService structureService;
    private RoomTemplateRegistry roomTemplateRegistry;
    private FeatureTemplateRegistry featureTemplateRegistry;
    private DoorTemplateRegistry doorTemplateRegistry;
    private LootTableRegistry lootTableRegistry;
    private AssetRenameCoordinator assetRenameCoordinator;
    private TagCatalog tagCatalog;
    private DungeonArchitectAPI api;
    private FeatureNestingPolicy featureNestingPolicy;
    private AuthoringManager authoringManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Path dataPath = getDataFolder().toPath();
        featureNestingPolicy = new FeatureNestingPolicy();
        updateFeatureNestingPolicy();
        structureService = new RoomStructureService(getServer());
        featureTemplateRegistry = new FeatureTemplateRegistry(dataPath.resolve("features"), structureService, featureNestingPolicy);
        featureTemplateRegistry.reload();
        doorTemplateRegistry = new DoorTemplateRegistry(dataPath.resolve("doors"), structureService, featureTemplateRegistry);
        doorTemplateRegistry.reload();
        roomTemplateRegistry = new RoomTemplateRegistry(dataPath.resolve("rooms"), structureService, featureTemplateRegistry, doorTemplateRegistry);
        roomTemplateRegistry.reload();
        lootTableRegistry = new LootTableRegistry(dataPath.resolve("loot-tables"));
        lootTableRegistry.reload();
        migrateLegacyLoot(dataPath);
        tagCatalog = new TagCatalog(dataPath.resolve("tags.yml"));
        synchronizeTags();
        assetRenameCoordinator = new AssetRenameCoordinator(roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry);
        logDiagnostics();

        int maxSearchSteps = getConfig().contains("generation.max-search-steps")
            ? getConfig().getInt("generation.max-search-steps", 25_000)
            : getConfig().getInt("generation.max-placement-attempts", 25_000);
        int spawnY = getConfig().getInt("worlds.spawn-y", 80);
        int placementTimeBudgetMillis = getConfig().getInt("generation.placement-time-budget-ms", 8);
        if (placementTimeBudgetMillis <= 0) {
            getLogger().warning("generation.placement-time-budget-ms must be positive; using 8 ms");
            placementTimeBudgetMillis = 8;
        }
        String worldPrefix = getConfig().getString("worlds.name-prefix", "da_");
        boolean deleteOnDestroy = getConfig().getBoolean("worlds.delete-on-destroy", true);

        LootService lootService = new LootService(lootTableRegistry, getLogger());
        FeatureService featureService = new FeatureService(featureTemplateRegistry, structureService, getLogger(), lootService);
        DoorService doorService = new DoorService(doorTemplateRegistry, structureService, featureService, getLogger(), lootService);
        RoomStructurePlacer structurePlacer = new RoomStructurePlacer(structureService, featureService, doorService, lootService);
        DungeonWorldManager worldManager = new DungeonWorldManager(this, worldPrefix, deleteOnDestroy);
        dungeonManager = new DungeonManager(
            this,
            roomTemplateRegistry,
            new DeterministicDungeonGenerator(maxSearchSteps, spawnY, doorTemplateRegistry::all),
            worldManager,
            structurePlacer,
            placementTimeBudgetMillis
        );

        Material wandMaterial = Material.matchMaterial(getConfig().getString("authoring.wand-material", "BLAZE_ROD"));
        if (wandMaterial == null) {
            wandMaterial = Material.BLAZE_ROD;
        }
        RoomCategory defaultCategory = RoomCategory.valueOf(getConfig().getString("authoring.default-category", "GENERIC").toUpperCase(Locale.ROOT));
        int defaultWeight = getConfig().getInt("authoring.default-weight", 10);
        authoringManager = new AuthoringManager(
            this,
            getServer(),
            roomTemplateRegistry.roomsDirectory(),
            featureTemplateRegistry.featuresDirectory(),
            new NamespacedKey(this, "authoring_wand"),
            new NamespacedKey(this, "authoring_selector"),
            wandMaterial,
            defaultCategory,
            defaultWeight,
            structureService
        );
        authoringManager.featureRegistry(featureTemplateRegistry);

        getServer().getPluginManager().registerEvents(new AuthoringListener(authoringManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRoomListener(dungeonManager), this);
        getServer().getPluginManager().registerEvents(dungeonManager.entityTracker(), this);
        getServer().getScheduler().runTaskTimer(this, new SelectionParticleTask(authoringManager), 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, dungeonManager.entityTracker(), 20L, 20L);
        getServer().getScheduler().runTaskLater(this, () -> {
            var dungeonWorld = worldManager.warmupWorld();
            dungeonManager.purgeStaleEntities(dungeonWorld);
            authoringManager.prepareEditWorld();
        }, 20L);

        ChatPromptManager chatPromptManager = new ChatPromptManager(this);
        MenuManager menuManager = new MenuManager(this, authoringManager, roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry, lootTableRegistry, dungeonManager, chatPromptManager, assetRenameCoordinator, tagCatalog, this::reloadContent);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        getServer().getPluginManager().registerEvents(menuManager, this);

        DungeonArchitectCommand command = new DungeonArchitectCommand(getPluginMeta().getVersion(), authoringManager, roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry, dungeonManager, menuManager, structureService, assetRenameCoordinator);
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
        updateFeatureNestingPolicy();
        structureService.clearCache();
        featureTemplateRegistry.reload();
        doorTemplateRegistry.reload();
        roomTemplateRegistry.reload();
        lootTableRegistry.reload();
        migrateLegacyLoot(getDataFolder().toPath());
        synchronizeTags();
        logDiagnostics();
    }

    private void migrateLegacyLoot(Path dataPath) {
        try {
            if (!LootRollMigration.migrate(dataPath, lootTableRegistry)) return;
            featureTemplateRegistry.reload();
            doorTemplateRegistry.reload();
            roomTemplateRegistry.reload();
            lootTableRegistry.reload();
            if (authoringManager != null) {
                roomTemplateRegistry.visible().forEach(template -> authoringManager.synchronizeRoomLootBindings(template.id(), template.lootBindings()));
                doorTemplateRegistry.visible().forEach(template -> authoringManager.synchronizeDoorLootBindings(template.id(), template.lootBindings()));
                featureTemplateRegistry.visible().forEach(template -> authoringManager.synchronizeFeatureLootBindings(template.id(), template.lootBindings()));
            }
            getLogger().info("Migrated legacy loot-table rolls to marker bindings.");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to migrate legacy loot rolls safely: " + ex.getMessage(), ex);
        }
    }

    private void updateFeatureNestingPolicy() {
        int depth = getConfig().getInt("features.max-nesting-depth", FeatureNestingPolicy.DEFAULT_MAX_DEPTH);
        int placements = getConfig().getInt("features.max-expanded-placements", FeatureNestingPolicy.DEFAULT_MAX_EXPANDED_PLACEMENTS);
        if (depth <= 0) {
            getLogger().warning("features.max-nesting-depth must be positive; using " + FeatureNestingPolicy.DEFAULT_MAX_DEPTH);
            depth = FeatureNestingPolicy.DEFAULT_MAX_DEPTH;
        }
        if (placements <= 0) {
            getLogger().warning("features.max-expanded-placements must be positive; using " + FeatureNestingPolicy.DEFAULT_MAX_EXPANDED_PLACEMENTS);
            placements = FeatureNestingPolicy.DEFAULT_MAX_EXPANDED_PLACEMENTS;
        }
        featureNestingPolicy.update(depth, placements);
    }

    private void synchronizeTags() {
        tagCatalog.synchronize(roomTemplateRegistry.visible(), doorTemplateRegistry.visible());
    }

    private void logDiagnostics() {
        TemplateValidationResult result = TemplateDiagnostics.analyze(roomTemplateRegistry, featureTemplateRegistry, doorTemplateRegistry, lootTableRegistry);
        int errors = result.errors().size();
        int warnings = result.warnings().size();
        if (errors == 0 && warnings == 0 && result.repairs().isEmpty()) {
            getLogger().info("Template diagnostics OK.");
            return;
        }
        getLogger().warning("Template diagnostics: errors=" + errors + " warnings=" + warnings + " repairs=" + result.repairs().size()
            + ". Run /da diagnose in game for the full report.");
        result.repairs().stream().limit(2).forEach(repair -> getLogger().warning("Repair: " + repair));
        result.diagnostics().stream().limit(3).forEach(diagnostic -> getLogger().warning(diagnostic.severity() + ": " + diagnostic.display()));
    }
}
