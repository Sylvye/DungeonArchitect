package com.dungeonarchitect.authoring;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AuthoringListener implements Listener {
    private static final double SELECTOR_RANGE = 64.0;
    private final AuthoringManager authoringManager;

    public AuthoringListener(AuthoringManager authoringManager) {
        this.authoringManager = authoringManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (authoringManager.isSelector(event.getItem())) {
            onSelectorInteract(event);
            return;
        }
        if (!authoringManager.isWand(event.getItem())) {
            return;
        }
        if (!event.getPlayer().hasPermission("dungeonarchitect.admin")) {
            return;
        }
        if (!authoringManager.isInEditWorld(event.getPlayer())) {
            event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Architect's Wand only works in da_edit."));
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.getPlayer().sendActionBar(Component.text("Offhand wand is view-only."));
                event.setCancelled(true);
            }
            return;
        }
        if (!canSetWandSelection(event.getHand(), event.getAction(), event.getClickedBlock() != null)) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            authoringManager.setSelection(event.getPlayer(), 1, event.getClickedBlock().getLocation());
            authoringManager.currentSelection(event.getPlayer())
                .ifPresentOrElse(
                    bounds -> event.getPlayer().sendActionBar(Component.text("pos1 set; selection " + bounds.describe())),
                    () -> event.getPlayer().sendActionBar(Component.text("pos1 set; select pos2"))
                );
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            authoringManager.setSelection(event.getPlayer(), 2, event.getClickedBlock().getLocation());
            authoringManager.currentSelection(event.getPlayer())
                .ifPresentOrElse(
                    bounds -> event.getPlayer().sendActionBar(Component.text("pos2 set; selection " + bounds.describe())),
                    () -> event.getPlayer().sendActionBar(Component.text("pos2 set; select pos1"))
                );
            event.setCancelled(true);
        }
    }

    static boolean canSetWandSelection(EquipmentSlot hand, Action action, boolean clickedBlock) {
        return hand == EquipmentSlot.HAND && clickedBlock && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK);
    }

    private void onSelectorInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().hasPermission("dungeonarchitect.admin")) {
            return;
        }
        if (!authoringManager.isInEditWorld(event.getPlayer())) {
            event.getPlayer().sendActionBar(Component.text("Architect's Selector only works in da_edit."));
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        if (!authoringManager.hasEditableRoomSession(event.getPlayer())) {
            event.getPlayer().sendActionBar(Component.text("Paste a room for editing first."));
            return;
        }
        var hit = authoringManager.raycastComponentHit(event.getPlayer(), SELECTOR_RANGE);
        authoringManager.spawnSelectorRay(event.getPlayer(), hit.map(SelectionRaycaster.Hit::distance).orElse(SELECTOR_RANGE));
        hit.map(SelectionRaycaster.Hit::value)
            .ifPresentOrElse(selection -> {
                authoringManager.selectComponent(event.getPlayer(), selection.type(), selection.id());
                event.getPlayer().sendMessage(Component.text("Selected " + selection.type() + " " + selection.id() + "."));
            }, () -> event.getPlayer().sendActionBar(Component.text("No component selected.")));
    }
}
