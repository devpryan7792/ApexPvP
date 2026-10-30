package com.apexpvp.kit;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combat Kit holding armor, hotbar/inventory items, potion effects, and ruleset flags.
 */
public class Kit {

    private final String id;
    private final String displayName;
    private final ItemStack icon;
    private final ItemStack[] mainContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final List<PotionEffect> potionEffects;
    private final KitRules rules;

    public Kit(@NotNull String id,
               @NotNull String displayName,
               @NotNull ItemStack icon,
               @NotNull ItemStack[] mainContents,
               @NotNull ItemStack[] armorContents,
               @Nullable ItemStack offHand,
               @NotNull List<PotionEffect> potionEffects,
               @NotNull KitRules rules) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.icon = Objects.requireNonNull(icon, "icon cannot be null").clone();
        this.mainContents = cloneItemArray(mainContents, 36);
        this.armorContents = cloneItemArray(armorContents, 4);
        this.offHand = offHand != null ? offHand.clone() : null;
        this.potionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects));
        this.rules = Objects.requireNonNull(rules, "rules cannot be null");
    }

    /**
     * Applies this kit to a player, respecting any custom hotbar layout saved in their profile.
     */
    public void apply(@NotNull Player player, @Nullable String customHotbarLayout) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        // Clear active potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Prepare main inventory
        ItemStack[] finalContents = cloneItemArray(mainContents, 36);

        // Apply custom hotbar permutation if valid
        if (customHotbarLayout != null && !customHotbarLayout.isBlank()) {
            Map<Integer, Integer> mapping = HotbarSerializer.deserialize(customHotbarLayout);
            if (HotbarLayoutValidator.isValid(mapping)) {
                ItemStack[] reordered = cloneItemArray(finalContents, 36);
                // Clear all source slots being moved to prevent cloning items
                for (int sourceSlot : mapping.values()) {
                    if (sourceSlot < reordered.length) {
                        reordered[sourceSlot] = null;
                    }
                }
                // Clear target hotbar slots before placing relocated items
                for (int targetHotbar : mapping.keySet()) {
                    if (targetHotbar < 9) {
                        reordered[targetHotbar] = null;
                    }
                }
                for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                    int targetHotbar = entry.getKey();
                    int sourceSlot = entry.getValue();
                    if (sourceSlot < mainContents.length && mainContents[sourceSlot] != null) {
                        reordered[targetHotbar] = mainContents[sourceSlot].clone();
                    }
                }
                finalContents = reordered;
            }
        }

        // Set storage contents (slots 0..35) without wiping armor/offhand
        player.getInventory().setStorageContents(finalContents);

        // Set armor contents (slots 36..39)
        player.getInventory().setArmorContents(cloneItemArray(armorContents, 4));

        // Set offhand (slot 40)
        if (offHand != null) {
            player.getInventory().setItemInOffHand(offHand.clone());
        } else {
            player.getInventory().setItemInOffHand(null);
        }

        // Apply potion effects
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        // Apply attack cooldown rule for Combo Mode (1.8 no-delay combat emulation)
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            if (rules.comboMode()) {
                attackSpeed.setBaseValue(1024.0); // Eliminates 1.9+ weapon cooldown
            } else {
                attackSpeed.setBaseValue(4.0); // Modern default
            }
        }

        // Reset vitals
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.updateInventory();
    }

    private static ItemStack[] cloneItemArray(ItemStack[] source, int expectedSize) {
        ItemStack[] result = new ItemStack[expectedSize];
        if (source != null) {
            for (int i = 0; i < Math.min(source.length, expectedSize); i++) {
                if (source[i] != null) {
                    result[i] = source[i].clone();
                }
            }
        }
        return result;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public ItemStack getIcon() {
        return icon.clone();
    }

    @NotNull
    public ItemStack[] getMainContents() {
        return cloneItemArray(mainContents, 36);
    }

    @NotNull
    public ItemStack[] getArmorContents() {
        return cloneItemArray(armorContents, 4);
    }

    @Nullable
    public ItemStack getOffHand() {
        return offHand != null ? offHand.clone() : null;
    }

    @NotNull
    public List<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    @NotNull
    public KitRules getRules() {
        return rules;
    }
}
