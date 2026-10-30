package com.apexpvp.match;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a player's exact inventory, health, effects, and location
 * taken before entering a match, guaranteeing zero inventory loss or duplication.
 */
public class PlayerRestoreSnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offHand;

    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final List<PotionEffect> activeEffects;
    private final int level;
    private final float exp;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean isFlying;
    private final Location returnLocation;

    public PlayerRestoreSnapshot(@NotNull Player player) {
        Objects.requireNonNull(player, "player cannot be null");

        ItemStack[] invContents = player.getInventory().getContents();
        this.contents = new ItemStack[invContents.length];
        for (int i = 0; i < invContents.length; i++) {
            if (invContents[i] != null) {
                this.contents[i] = invContents[i].clone();
            }
        }

        ItemStack[] invArmor = player.getInventory().getArmorContents();
        this.armor = new ItemStack[invArmor.length];
        for (int i = 0; i < invArmor.length; i++) {
            if (invArmor[i] != null) {
                this.armor[i] = invArmor[i].clone();
            }
        }

        ItemStack off = player.getInventory().getItemInOffHand();
        this.offHand = off != null ? off.clone() : null;

        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.activeEffects = new ArrayList<>(player.getActivePotionEffects());
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.gameMode = player.getGameMode();
        this.allowFlight = player.getAllowFlight();
        this.isFlying = player.isFlying();
        this.returnLocation = player.getLocation().clone();
    }

    /**
     * Safely restores the player's pristine inventory, vitals, and position.
     */
    public void restore(@NotNull Player player) {
        player.getInventory().clear();

        // Restore inventory items
        ItemStack[] restoredContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                restoredContents[i] = contents[i].clone();
            }
        }
        player.getInventory().setContents(restoredContents);

        // Restore armor
        ItemStack[] restoredArmor = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                restoredArmor[i] = armor[i].clone();
            }
        }
        player.getInventory().setArmorContents(restoredArmor);

        // Restore offhand
        player.getInventory().setItemInOffHand(offHand != null ? offHand.clone() : null);

        // Clear match potion effects and restore original ones
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : activeEffects) {
            player.addPotionEffect(effect);
        }

        // Restore vitals
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(Math.min(health, maxHp));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setFireTicks(0);

        // Reset attack speed to standard (in case kit had combo mode)
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.setBaseValue(4.0);
        }

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(isFlying);

        // Teleport back to original position / lobby (redirect to default world if saved location is in arena world)
        Location targetLoc = returnLocation;
        if (targetLoc.getWorld() != null && targetLoc.getWorld().getName().equalsIgnoreCase("apex_arenas")) {
            org.bukkit.World defaultWorld = org.bukkit.Bukkit.getWorlds().get(0);
            if (defaultWorld != null) {
                targetLoc = defaultWorld.getSpawnLocation();
            }
        }
        player.teleport(targetLoc);
        player.updateInventory();
    }

    @NotNull
    public Location getReturnLocation() {
        return returnLocation.clone();
    }
}
