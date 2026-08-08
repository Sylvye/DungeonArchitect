package com.dungeonarchitect.authoring;

import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SelectionParticleTask implements Runnable {
    private final AuthoringManager authoringManager;

    public SelectionParticleTask(AuthoringManager authoringManager) {
        this.authoringManager = authoringManager;
    }

    @Override
    public void run() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            boolean holdingWand = authoringManager.isWand(player.getInventory().getItemInMainHand())
                || authoringManager.isWand(player.getInventory().getItemInOffHand());
            if (!player.hasPermission("dungeonarchitect.admin") || !holdingWand) {
                continue;
            }
            authoringManager.currentSelection(player).ifPresent(bounds -> draw(player, bounds, Particle.WAX_OFF));
            authoringManager.roomBounds(player).ifPresent(bounds -> draw(player, bounds, Particle.WAX_ON));
        }
    }

    private void draw(Player player, SelectionBounds bounds, Particle particle) {
        World world = player.getWorld();
        IntVector3 min = bounds.min();
        IntVector3 max = bounds.visualMax();
        double step = 0.5;
        for (double x = min.x(); x <= max.x(); x += step) {
            spawn(world, particle, x, min.y(), min.z());
            spawn(world, particle, x, min.y(), max.z());
            spawn(world, particle, x, max.y(), min.z());
            spawn(world, particle, x, max.y(), max.z());
        }
        for (double y = min.y(); y <= max.y(); y += step) {
            spawn(world, particle, min.x(), y, min.z());
            spawn(world, particle, min.x(), y, max.z());
            spawn(world, particle, max.x(), y, min.z());
            spawn(world, particle, max.x(), y, max.z());
        }
        for (double z = min.z(); z <= max.z(); z += step) {
            spawn(world, particle, min.x(), min.y(), z);
            spawn(world, particle, min.x(), max.y(), z);
            spawn(world, particle, max.x(), min.y(), z);
            spawn(world, particle, max.x(), max.y(), z);
        }
    }

    private void spawn(World world, Particle particle, double x, double y, double z) {
        world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
    }
}
