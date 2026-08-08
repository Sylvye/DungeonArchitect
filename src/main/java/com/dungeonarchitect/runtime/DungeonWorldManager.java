package com.dungeonarchitect.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

public final class DungeonWorldManager {
    private final Plugin plugin;
    private final String prefix;
    private final boolean deleteOnDestroy;

    public DungeonWorldManager(Plugin plugin, String prefix, boolean deleteOnDestroy) {
        this.plugin = plugin;
        this.prefix = prefix;
        this.deleteOnDestroy = deleteOnDestroy;
    }

    public World createWorld(UUID dungeonId) {
        String name = prefix + dungeonId.toString().replace("-", "");
        World world = WorldCreator.name(name)
            .environment(World.Environment.NORMAL)
            .generator(new VoidChunkGenerator())
            .generateStructures(false)
            .createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create dungeon world " + name);
        }
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000L);
        world.setStorm(false);
        return world;
    }

    public void destroyWorld(String worldName) throws IOException {
        World world = Bukkit.getWorld(worldName);
        Path folder = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        if (deleteOnDestroy && Files.exists(folder)) {
            try (var walk = Files.walk(folder)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
