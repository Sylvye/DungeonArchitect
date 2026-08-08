package com.dungeonarchitect.authoring;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class AuthoringListener implements Listener {
    private final AuthoringManager authoringManager;

    public AuthoringListener(AuthoringManager authoringManager) {
        this.authoringManager = authoringManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!authoringManager.isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("dungeonarchitect.admin")) {
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
}
