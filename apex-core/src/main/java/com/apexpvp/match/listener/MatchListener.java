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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import com.apexpvp.util.TextUtil;
import com.apexpvp.ApexPvPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderPearl;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Combat, movement restriction, and vital mechanics listener for active matches.
 */
public class MatchListener implements Listener {

    private final MatchManager matchManager;
    private final ApexPvPPlugin plugin;

    public MatchListener(@NotNull MatchManager matchManager) {
        this(matchManager, null);
    }

    public MatchListener(@NotNull MatchManager matchManager, @Nullable ApexPvPPlugin plugin) {
        this.matchManager = Objects.requireNonNull(matchManager, "matchManager cannot be null");
        this.plugin = plugin;
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

        // Handle lethal damage: Cancel vanilla death screen, trigger match outcome.
        // Exception: a totem in either hand must be allowed to fire first, otherwise
        // the match listener would "kill" the player before vanilla resurrects them.
        if (victim.getHealth() - event.getFinalDamage() <= 0.0) {
            if (hasTotemInHand(victim)) {
                return;
            }
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

    /**
     * Vanilla only consumes a totem of undying from the main hand or offhand.
     */
    private boolean hasTotemInHand(@NotNull Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.TOTEM_OF_UNDYING) {
            return true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.TOTEM_OF_UNDYING;
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
            // Only freeze if player is actually inside the arena world
            if (from.getWorld() != null && from.getWorld().equals(match.getArena().getWorld())) {
                if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                    event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
                }
            } else {
                // If player is still in lobby/overworld, ensure they are sent to their designated arena spawn
                MatchParticipant part = match.getParticipant(player.getUniqueId());
                if (part != null) {
                    Location spawn = (part.getTeamIndex() == 0) ? match.getArena().getSpawnA() : match.getArena().getSpawnB();
                    player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
            return;
        }

        // Only enforce in-arena boundary/void checks if the player is in the arena world
        if (to.getWorld() == null || !to.getWorld().equals(match.getArena().getWorld())) {
            return;
        }

        // 2. Clamp Spectators within Arena bounds
        MatchParticipant part = match.getParticipant(player.getUniqueId());
        if (part != null && part.isSpectator()) {
            if (!match.getArena().getBounds().contains(to)) {
                player.teleport(match.getArena().getSpectatorSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSoup(PlayerInteractEvent event) {
        if (!event.hasItem()) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Material mat = item.getType();
        if (mat != Material.MUSHROOM_STEW && mat != Material.RABBIT_STEW && mat != Material.BEETROOT_SOUP) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Match match = matchManager.getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;
        if (!match.getKit().getRules().soupMode()) return;

        double maxHealth = 20.0;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }

        if (player.getHealth() < maxHealth) {
            event.setCancelled(true);
            double newHealth = Math.min(maxHealth, player.getHealth() + 7.0);
            player.setHealth(newHealth);

            ItemStack bowl = new ItemStack(Material.BOWL);
            if (event.getHand() == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(bowl);
            } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(bowl);
            }
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Match match = matchManager.getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;

        ItemStack item = event.getItem();
        // Remove empty bottle if enabled
        if (item.getType() == Material.POTION && match.getKit().getRules().removeEmptyBottles()) {
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().remove(Material.GLASS_BOTTLE);
                });
            } else {
                player.getInventory().remove(Material.GLASS_BOTTLE);
            }
        }

        // Golden apple custom cooldown
        if ((item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE)
                && match.getKit().getRules().goldenAppleCooldown() > 0) {
            player.setCooldown(item.getType(), match.getKit().getRules().goldenAppleCooldown() * 20);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        Match match = matchManager.getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;

        int pearlCd = match.getKit().getRules().enderPearlCooldown();
        if (pearlCd > 0) {
            player.setCooldown(Material.ENDER_PEARL, pearlCd * 20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Match match = matchManager.getMatch(player);
        if (match != null && match.getState() == MatchState.ACTIVE && match.getKit().getRules().disableCrafting()) {
            event.setCancelled(true);
            TextUtil.sendPrefixed(player, "<red>Crafting is disabled during this match.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            Match match = matchManager.getMatch(player);
            if (match != null && match.getState() == MatchState.ACTIVE && match.getKit().getRules().disableCrafting()) {
                event.setCancelled(true);
            }
        }
    }
}
