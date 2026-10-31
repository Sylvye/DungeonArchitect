package com.dungeonarchitect.minigame;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Serialized original state is also used for in-memory reconnect snapshots. */
final class PlayerSnapshot {
    private final YamlConfiguration data;
    private PlayerSnapshot(YamlConfiguration data) { this.data = data; }
    static PlayerSnapshot capture(Player player) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("schema-version", 1);
        data.set("inventory-size", player.getInventory().getContents().length);
        data.set("inventory", Arrays.asList(player.getInventory().getContents()).stream().map(i -> i == null ? null : i.clone()).toList());
        data.set("held-slot", player.getInventory().getHeldItemSlot());
        data.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        data.set("health", player.getHealth()); data.set("food", player.getFoodLevel());
        data.set("saturation", player.getSaturation()); data.set("exhaustion", player.getExhaustion());
        data.set("level", player.getLevel()); data.set("exp", player.getExp()); data.set("total-exp", player.getTotalExperience());
        data.set("mode", player.getGameMode().name()); data.set("flight", player.getAllowFlight()); data.set("flying", player.isFlying());
        data.set("invulnerable", player.isInvulnerable()); data.set("collidable", player.isCollidable());
        data.set("fire", player.getFireTicks()); data.set("air", player.getRemainingAir());
        data.set("absorption", player.getAbsorptionAmount()); data.set("freeze", player.getFreezeTicks());
        Location location = player.getLocation();
        data.set("world", location.getWorld().getName()); data.set("x", location.getX()); data.set("y", location.getY()); data.set("z", location.getZ());
        data.set("yaw", location.getYaw()); data.set("pitch", location.getPitch());
        return new PlayerSnapshot(data);
    }
    static PlayerSnapshot load(java.nio.file.Path file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file.toFile()); } catch (Exception ex) { throw new IllegalStateException("Could not read player recovery " + file.getFileName(), ex); }
        List<?> contents = yaml.getList("inventory");
        if (yaml.getInt("schema-version") != 1 || contents == null || contents.size() != yaml.getInt("inventory-size") || contents.size() < 36 || contents.size() > 128
            || contents.stream().anyMatch(item -> item != null && !(item instanceof ItemStack)) || !yaml.isString("world") || !yaml.isString("mode")) throw new IllegalStateException("Invalid player recovery " + file.getFileName() + " (schema=" + yaml.getInt("schema-version") + ", inventory size=" + (contents == null ? "missing" : contents.size()) + ")");
        return new PlayerSnapshot(yaml);
    }
    YamlConfiguration data() { return data; }
    Location location() {
        World world = Bukkit.getWorld(data.getString("world", ""));
        if (world == null) return Bukkit.getWorlds().getFirst().getSpawnLocation();
        return new Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"), (float) data.getDouble("yaw"), (float) data.getDouble("pitch"));
    }
    boolean apply(Player player) {
        if (player.isDead()) return false;
        List<?> items = data.getList("inventory", List.of());
        if (items.size() > player.getInventory().getContents().length) throw new IllegalStateException("Saved inventory has more slots than this server supports");
        player.closeInventory();
        if (!player.teleport(location())) throw new IllegalStateException("Return teleport was blocked for " + player.getName());
        clear(player);
        ItemStack[] inventory = new ItemStack[player.getInventory().getContents().length];
        for (int i = 0; i < items.size(); i++) inventory[i] = items.get(i) instanceof ItemStack item ? item.clone() : null;
        player.getInventory().setContents(inventory);
        player.getInventory().setHeldItemSlot(data.getInt("held-slot"));
        player.setGameMode(GameMode.valueOf(data.getString("mode", "SURVIVAL")));
        player.setAllowFlight(data.getBoolean("flight")); player.setFlying(data.getBoolean("flight") && data.getBoolean("flying"));
        player.setInvulnerable(data.getBoolean("invulnerable")); player.setCollidable(data.getBoolean("collidable", true));
        for (Object effect : data.getList("effects", List.of())) if (effect instanceof PotionEffect potion) player.addPotionEffect(potion);
        player.setLevel(data.getInt("level")); player.setExp((float) data.getDouble("exp")); player.setTotalExperience(data.getInt("total-exp"));
        player.setFoodLevel(data.getInt("food", 20)); player.setSaturation((float) data.getDouble("saturation")); player.setExhaustion((float) data.getDouble("exhaustion"));
        player.setHealth(Math.max(1, Math.min(data.getDouble("health", 20), player.getAttribute(Attribute.MAX_HEALTH).getValue())));
        player.setFireTicks(data.getInt("fire")); player.setRemainingAir(data.getInt("air", 300));
        player.setAbsorptionAmount(data.getDouble("absorption")); player.setFreezeTicks(data.getInt("freeze"));
        return true;
    }
    static void clear(Player player) {
        player.closeInventory(); player.getInventory().clear(); player.setItemOnCursor(null);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setLevel(0); player.setExp(0); player.setTotalExperience(0);
        player.setGameMode(GameMode.SURVIVAL); player.setFlying(false); player.setAllowFlight(false);
        player.setInvulnerable(false); player.setCollidable(true);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20); player.setSaturation(5); player.setExhaustion(0);
        player.setFireTicks(0); player.setFallDistance(0); player.setRemainingAir(player.getMaximumAir());
        player.setAbsorptionAmount(0); player.setFreezeTicks(0); player.setVelocity(new org.bukkit.util.Vector());
    }
}
