package com.dungeonarchitect.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ChatPromptManager implements Listener {
    private final Plugin plugin;
    private final Map<UUID, Prompt> prompts = new HashMap<>();

    public ChatPromptManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String message, Consumer<String> handler) {
        prompts.put(player.getUniqueId(), new Prompt(message, handler));
        player.closeInventory();
        player.sendMessage(Component.text(message + " Type cancel to abort."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                event.getPlayer().sendMessage(Component.text("Edit cancelled."));
                return;
            }
            try {
                prompt.handler.accept(message);
            } catch (RuntimeException ex) {
                event.getPlayer().sendMessage(Component.text("Edit failed: " + ex.getMessage()));
            }
        });
    }

    private record Prompt(String message, Consumer<String> handler) {
    }
}
