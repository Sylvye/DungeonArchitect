package com.dungeonarchitect.minigame;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/** All restrictions are scoped by player membership and the affected run footprint. */
public final class MinigameListener implements Listener {
    private final Plugin plugin;
    private final MinigameService games;
    public MinigameListener(Plugin plugin, MinigameService games) { this.plugin = plugin; this.games = games; }
    private boolean locked(Player player) {
        var run = games.getRun(player).orElse(null);
        return run != null && games.entered(player) && (run.frozen() || run.eliminated(player.getUniqueId()));
    }
    private boolean mayInteract(Player player, Location location) {
        var run = games.getRun(player).orElse(null); var target = games.at(location).orElse(null);
        if (locked(player)) return false;
        if (target != null && (target != run || target.state() != RunState.RUNNING)) return false;
        return !games.entered(player) || target == run;
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Player player = event.getPlayer(); if (!games.entered(player)) return;
        MinigameRun run = games.getRun(player).orElseThrow();
        Location to = event.getTo();
        if (run.frozen()) {
            Location anchor = games.anchor(player);
            if (to.getWorld() != anchor.getWorld() || to.distanceSquared(anchor) > .0001) {
                anchor.setYaw(to.getYaw()); anchor.setPitch(to.getPitch()); event.setTo(anchor);
            }
        } else if (!run.contains(to)) event.setTo(event.getFrom());
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        MinigameRun run = games.getRun(event.getPlayer()).orElse(null);
        MinigameRun destination = games.at(event.getTo()).orElse(null);
        if (destination != null && destination != run) { event.setCancelled(true); return; }
        if (games.entered(event.getPlayer()) && (!run.contains(event.getTo())
            || run.frozen() && event.getTo().distanceSquared(games.anchor(event.getPlayer())) > .0001)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && locked(victim)) { event.setCancelled(true); return; }
        if (!(event instanceof EntityDamageByEntityEvent damage)) return;
        Entity source = damage.getDamageSource().getCausingEntity();
        if (source == null) source = damage.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (source instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) source = owner;
        Player attacker = source instanceof Player player ? player : null;
        MinigameRun target = games.at(event.getEntity().getLocation()).orElse(null);
        MinigameRun attackRun = attacker == null ? null : games.getRun(attacker).filter(r -> games.entered(attacker)).orElse(null);
        if (attacker != null && locked(attacker)) { event.setCancelled(true); return; }
        if (target != null && (target.state() != RunState.RUNNING || attacker != null && attackRun != target)) { event.setCancelled(true); return; }
        if (attackRun != null && target != attackRun) { event.setCancelled(true); return; }
        if (attacker != null && event.getEntity() instanceof Player victim && attackRun != null) {
            MinigameRun victimRun = games.getRun(victim).orElse(null);
            if (victimRun != attackRun || Objects.equals(attackRun.teams().get(attacker.getUniqueId()), attackRun.teams().get(victim.getUniqueId()))) event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (locked(event.getPlayer()) || event.getClickedBlock() != null && (!mayInteract(event.getPlayer(), event.getClickedBlock().getLocation())
            || games.entered(event.getPlayer()) && event.getClickedBlock().getType() == Material.ENDER_CHEST)) event.setCancelled(true);
    }
    private boolean mayAffect(Player source, LivingEntity target, boolean harmful) {
        if (locked(source) || target instanceof Player player && locked(player)) return false;
        MinigameRun from = games.getRun(source).filter(r -> games.entered(source)).orElse(null);
        MinigameRun to = games.at(target.getLocation()).orElse(null);
        if (from != to && (from != null || to != null)) return false;
        return !harmful || from == null || !(target instanceof Player player) || source == target
            || !Objects.equals(from.teams().get(source.getUniqueId()), from.teams().get(player.getUniqueId()));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player source)) return;
        boolean harmful = event.getPotion().getEffects().stream().anyMatch(e -> e.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL);
        for (LivingEntity target : event.getAffectedEntities()) if (!mayAffect(source, target, harmful)) event.setIntensity(target, 0);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity().getSource() instanceof Player source)) return;
        var cloud = event.getEntity();
        boolean harmful = cloud.getCustomEffects().stream().anyMatch(e -> e.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL)
            || cloud.getBasePotionType() != null && cloud.getBasePotionType().getPotionEffects().stream().anyMatch(e -> e.getType().getCategory() == org.bukkit.potion.PotionEffectTypeCategory.HARMFUL);
        event.getAffectedEntities().removeIf(target -> !mayAffect(source, target, harmful));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent event) {
        if (games.at(event.getBlock().getLocation()).isPresent() || event.getPlayer() != null && games.entered(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entityInteract(PlayerInteractEntityEvent event) { if (!mayInteract(event.getPlayer(), event.getRightClicked().getLocation())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { if (games.entered(event.getPlayer()) || games.at(event.getBlock().getLocation()).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) { if (games.entered(event.getPlayer()) || games.at(event.getBlock().getLocation()).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent event) { if (games.entered(event.getPlayer()) || games.at(event.getBlock().getLocation()).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent event) { if (games.entered(event.getPlayer()) || games.at(event.getBlock().getLocation()).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { if (!mayInteract(event.getPlayer(), event.getItemDrop().getLocation())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player && !mayInteract(player, event.getItem().getLocation())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) { if (locked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void shoot(ProjectileLaunchEvent event) { if (event.getEntity().getShooter() instanceof Player player && locked(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hunger(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player player && locked(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void inventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player && locked(player)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && locked(player) && !(event.getInventory().getHolder() instanceof MinigameMenus.View)) event.setCancelled(true);
        if (event.getPlayer() instanceof Player player && games.entered(player) && event.getInventory().getType() == InventoryType.ENDER_CHEST) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void spectate(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        MinigameRun run = games.getRun(event.getPlayer()).orElse(null);
        if (run != null && games.entered(event.getPlayer()) && !run.contains(event.getNewSpectatorTarget().getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent event) { event.blockList().removeIf(block -> games.at(block.getLocation()).isPresent()); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent event) { event.blockList().removeIf(block -> games.at(block.getLocation()).isPresent()); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void changeBlock(EntityChangeBlockEvent event) { if (games.at(event.getBlock().getLocation()).isPresent()) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        MinigameRun run = games.getRun(event.getEntity()).orElse(null);
        if (run == null || !games.entered(event.getEntity())) return;
        games.eliminate(run.id(), event.getEntity().getUniqueId());
        event.setKeepInventory(false); event.setKeepLevel(false); event.setDroppedExp(0);
    }
    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        MinigameRun run = games.getRun(event.getPlayer()).orElse(null);
        if (run != null && games.entered(event.getPlayer())) event.setRespawnLocation(games.anchor(event.getPlayer()));
        Bukkit.getScheduler().runTask(plugin, () -> games.respawn(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { games.quit(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(plugin, () -> games.join(event.getPlayer())); }
    @EventHandler
    public void destroyed(com.dungeonarchitect.api.event.DungeonDestroyedEvent event) {
        for (MinigameRun run : games.runs()) if (run.dungeon().map(d -> d.id().equals(event.dungeon().id())).orElse(false)) games.end(run.id(), "Dungeon destroyed");
    }
}
