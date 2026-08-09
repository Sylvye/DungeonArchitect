package com.dungeonarchitect.authoring;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class SelectionParticleTask implements Runnable {
    private static final Particle.DustOptions DOOR_DUST = new Particle.DustOptions(Color.fromRGB(0, 255, 171), 1.0f);
    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 33, 117), 1.0f);
    private static final Particle.DustOptions FEATURE_DUST = new Particle.DustOptions(Color.fromRGB(33, 158, 255), 1.0f);
    private static final double OUTLINE_STEP = 0.5;
    private final AuthoringManager authoringManager;

    public SelectionParticleTask(AuthoringManager authoringManager) {
        this.authoringManager = authoringManager;
    }

    @Override
    public void run() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            boolean holdingWand = authoringManager.isWand(player.getInventory().getItemInMainHand())
                || authoringManager.isWand(player.getInventory().getItemInOffHand());
            boolean holdingSelector = authoringManager.isSelector(player.getInventory().getItemInMainHand())
                || authoringManager.isSelector(player.getInventory().getItemInOffHand());
            if (!player.hasPermission("dungeonarchitect.admin") || (!holdingWand && !holdingSelector) || !authoringManager.isInEditWorld(player)) {
                continue;
            }
            List<StyledOutline> outlines = new ArrayList<>();
            if (holdingWand) {
                authoringManager.currentSelection(player).ifPresent(bounds -> authoringManager.selectedComponent(player)
                    .ifPresentOrElse(
                        component -> outlines.add(StyledOutline.dust("selected:" + component.type() + ":" + component.id(), bounds, dust(component.type()))),
                        () -> outlines.add(StyledOutline.particle("current", bounds, Particle.WAX_OFF))
                    ));
            }
            if (holdingSelector) {
                authoringManager.componentSelections(player)
                    .forEach(selection -> outlines.add(StyledOutline.dust("component:" + selection.type() + ":" + selection.id(), selection.worldBounds(), dust(selection.type()))));
            }
            authoringManager.roomBounds(player).ifPresent(bounds -> outlines.add(StyledOutline.particle("bounds", bounds, Particle.WAX_ON)));
            drawOutlines(player, outlines);
        }
    }

    private void drawOutlines(Player player, List<StyledOutline> outlines) {
        List<SelectionOutlinePlanner.Outline> requests = outlines.stream()
            .map(outline -> new SelectionOutlinePlanner.Outline(outline.key(), outline.bounds()))
            .toList();
        List<SelectionOutlinePlanner.PlannedOutline> planned = SelectionOutlinePlanner.plan(requests);
        for (int i = 0; i < planned.size(); i++) {
            draw(player, outlines.get(i), planned.get(i).offset());
        }
    }

    private void draw(Player player, StyledOutline outline, SelectionOutlinePlanner.Offset offset) {
        World world = player.getWorld();
        for (var point : SelectionOutlinePlanner.outlinePoints(outline.bounds(), OUTLINE_STEP, offset)) {
            if (outline.dust() == null) {
                world.spawnParticle(outline.particle(), point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0);
            } else {
                world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, outline.dust());
            }
        }
    }

    private Particle.DustOptions dust(String type) {
        return switch (type) {
            case "door" -> DOOR_DUST;
            case "marker" -> MARKER_DUST;
            case "feature" -> FEATURE_DUST;
            default -> FEATURE_DUST;
        };
    }

    private record StyledOutline(String key, SelectionBounds bounds, Particle particle, Particle.DustOptions dust) {
        private static StyledOutline particle(String key, SelectionBounds bounds, Particle particle) {
            return new StyledOutline(key, bounds, particle, null);
        }

        private static StyledOutline dust(String key, SelectionBounds bounds, Particle.DustOptions dust) {
            return new StyledOutline(key, bounds, null, dust);
        }
    }
}
