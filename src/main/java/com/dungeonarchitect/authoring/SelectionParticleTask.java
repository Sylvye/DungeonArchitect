package com.dungeonarchitect.authoring;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class SelectionParticleTask implements Runnable {
    private static final Particle.DustOptions DOOR_DUST = new Particle.DustOptions(Color.fromRGB(0, 255, 171), 1.0f);
    private static final Particle.DustOptions GATEWAY_DUST = new Particle.DustOptions(Color.fromRGB(255, 213, 79), 1.0f);
    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 33, 117), 1.0f);
    private static final Particle.DustOptions FEATURE_DUST = new Particle.DustOptions(Color.fromRGB(33, 158, 255), 1.0f);
    private static final double OUTLINE_STEP = 0.5;
    private static final double FACING_LINE_LENGTH = 2.0;
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
            List<StyledOutline> outlines = buildOutlines(
                holdingWand,
                holdingSelector,
                authoringManager.currentSelection(player),
                authoringManager.selectedComponentSelection(player),
                authoringManager.componentSelections(player),
                authoringManager.roomBounds(player)
            );
            drawOutlines(player, outlines);
        }
    }

    static List<StyledOutline> buildOutlines(boolean holdingWand, boolean holdingSelector, java.util.Optional<SelectionBounds> currentSelection, java.util.Optional<AuthoringManager.ComponentSelection> selected, List<AuthoringManager.ComponentSelection> components, java.util.Optional<SelectionBounds> roomBounds) {
        List<StyledOutline> outlines = new ArrayList<>();
        if (holdingWand) {
            currentSelection.ifPresent(bounds -> outlines.add(StyledOutline.particle("current", bounds, Particle.WAX_OFF)));
        }
        if (holdingWand || holdingSelector) {
            selected.ifPresent(selection -> outlines.add(componentOutline("selected", selection)));
        }
        if (holdingSelector) {
            components.stream()
                .filter(selection -> selected.isEmpty() || !sameComponent(selection, selected.get()))
                .forEach(selection -> outlines.add(componentOutline("component", selection)));
        }
        roomBounds.ifPresent(bounds -> outlines.add(StyledOutline.particle("bounds", bounds, Particle.WAX_ON)));
        return List.copyOf(outlines);
    }

    private static StyledOutline componentOutline(String prefix, AuthoringManager.ComponentSelection selection) {
        return StyledOutline.dust(prefix + ":" + selection.type() + ":" + selection.id(), selection.worldBounds(), dust(selection.type()), selection.facing());
    }

    private static boolean sameComponent(AuthoringManager.ComponentSelection first, AuthoringManager.ComponentSelection second) {
        return first.type().equals(second.type()) && first.id().equalsIgnoreCase(second.id());
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
        if (outline.dust() != null && outline.facing() != null) {
            for (var point : SelectionOutlinePlanner.facingLinePoints(outline.bounds(), outline.facing(), OUTLINE_STEP, FACING_LINE_LENGTH, offset)) {
                world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, outline.dust());
            }
        }
    }

    private static Particle.DustOptions dust(String type) {
        return switch (type) {
            case "door" -> DOOR_DUST;
            case "gateway" -> GATEWAY_DUST;
            case "marker" -> MARKER_DUST;
            case "feature" -> FEATURE_DUST;
            default -> FEATURE_DUST;
        };
    }

    record StyledOutline(String key, SelectionBounds bounds, Particle particle, Particle.DustOptions dust, com.dungeonarchitect.domain.Direction3 facing) {
        private static StyledOutline particle(String key, SelectionBounds bounds, Particle particle) {
            return new StyledOutline(key, bounds, particle, null, null);
        }

        private static StyledOutline dust(String key, SelectionBounds bounds, Particle.DustOptions dust) {
            return dust(key, bounds, dust, null);
        }

        private static StyledOutline dust(String key, SelectionBounds bounds, Particle.DustOptions dust, com.dungeonarchitect.domain.Direction3 facing) {
            return new StyledOutline(key, bounds, null, dust, facing);
        }
    }
}
