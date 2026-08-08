package com.dungeonarchitect.gui;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface MenuAction {
    void click(Player player);
}
