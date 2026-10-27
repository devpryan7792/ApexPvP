package com.apexpvp.match.listener;

import com.apexpvp.match.Match;
import com.apexpvp.match.MatchManager;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.match.MatchState;
import com.apexpvp.match.PlayerRestoreSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import com.apexpvp.util.TextUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Combat, movement restriction, and vital mechanics listener for active matches.
 */
public class MatchListener implements Listener {

    private final MatchManager matchManager;

    public MatchListener(@NotNull MatchManager matchManager) {
        this.matchManager = Objects.requireNonNull(matchManager, "matchManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        Match victimMatch = matchManager.getMatch(victim);
        if (victimMatch == null) return;

        // Disallow combat unless state is ACTIVE
        if (victimMatch.getState() != MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        if (attacker != null) {
            Match attackerMatch = matchManager.getMatch(attacker);
            if (victimMatch != attackerMatch) {
                event.setCancelled(true);
                return;
            }

            MatchParticipant victimPart = victimMatch.getParticipant(victim.getUniqueId());
            MatchParticipant attackerPart = victimMatch.getParticipant(attacker.getUniqueId());

            // Prevent friendly fire in team matches
            if (victimPart != null && attackerPart != null && victimPart.getTeamIndex() == attackerPart.getTeamIndex()) {
                event.setCancelled(true);
                return;
            }

            // Sumo Mode logic: Zero weapon damage, boosted horizontal knockback
            if (victimMatch.getKit().getRules().sumoMode()) {
                event.setDamage(0.0);
                Vector knockback = attacker.getLocation().getDirection().setY(0).normalize().multiply(0.45);
                knockback.setY(0.28);
                victim.setVelocity(victim.getVelocity().add(knockback));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Match match = matchManager.getMatch(victim);
        if (match == null) return;

        if (match.getState() != MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Cancel environmental damage in Sumo
        if (match.getKit().getRules().sumoMode() && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setCancelled(true);
            return;
        }

        // Handle lethal damage: Cancel vanilla death screen, trigger match outcome
        if (victim.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            victim.setHealth(20.0);

            Player killer = victim.getKiller();
            if (killer == null && event instanceof EntityDamageByEntityEvent edbe) {
                if (edbe.getDamager() instanceof Player kp) {
                    killer = kp;
                } else if (edbe.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player kp) {
                    killer = kp;
                }
            }
            match.onPlayerDeath(victim, killer);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match match = matchManager.getMatch(player);
        if (match == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // 1. Freeze movement during PREPARING & COUNTDOWN (freeze X, Y, and Z)
        if (match.getState().isFrozen()) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
                return;
            }
        }

        // 2. Clamp Spectators within Arena bounds
        MatchParticipant part = match.getParticipant(player.getUniqueId());
        if (part != null && part.isSpectator()) {
            if (!match.getArena().getBounds().contains(to)) {
                player.teleport(match.getArena().getSpectatorSpawn());
                return;
            }
        }

        // 3. Sumo Mode Ring-Out Detection (water or dropped below arena baseline)
        if (match.getKit().getRules().sumoMode() && match.getState() == MatchState.ACTIVE) {
            if (to.getBlock().isLiquid() || to.getY() < match.getArena().getOriginY()) {
                match.onPlayerDeath(player, null);
                return;
            }
        }

        // 4. Void fall-off safety catch (drop below originY - 10 in any mode)
        if (match.getState() == MatchState.ACTIVE && to.getY() < (match.getArena().getOriginY() - 10)) {
            match.onPlayerDeath(player, null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRestoreSnapshot pending = matchManager.consumePendingRestore(player.getUniqueId());
        if (pending != null) {
            pending.restore(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = matchManager.getMatch(player);
        if (match != null) {
            match.onPlayerQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = matchManager.getMatch(player);
        if (match != null && !match.getKit().getRules().allowHunger()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Match match = matchManager.getMatch(event.getPlayer());
        if (match != null && match.getState() != MatchState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent event) {
        Match match = matchManager.getMatch(event.getPlayer());
        if (match != null && match.getState() != MatchState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Match match = matchManager.getMatch(event.getPlayer());
        if (match != null && match.getState().isFrozen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (matchManager.isInMatch(player)) {
            String msg = event.getMessage().toLowerCase();
            if (!player.hasPermission("apexpvp.admin") && !msg.startsWith("/apex leave") && !msg.startsWith("/spec")) {
                event.setCancelled(true);
                TextUtil.sendPrefixed(player, "<red>You cannot use commands during an active match!");
            }
        }
    }
}
