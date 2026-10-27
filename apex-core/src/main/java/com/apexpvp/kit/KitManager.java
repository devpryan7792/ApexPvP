package com.apexpvp.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages kit loading, serialization, and retrieval.
 */
public class KitManager {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();

    public KitManager(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void initialize() {
        plugin.getLogger().info("Loading kits from kits.yml...");
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("kits");
        if (section == null) {
            plugin.getLogger().warning("No kits found in kits.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection kSec = section.getConfigurationSection(key);
            if (kSec == null) continue;

            try {
                String displayName = kSec.getString("display-name", key);
                String iconMatName = kSec.getString("icon", "DIAMOND_SWORD");
                Material iconMat = Material.matchMaterial(iconMatName);
                if (iconMat == null) iconMat = Material.DIAMOND_SWORD;
                ItemStack icon = new ItemStack(iconMat);

                // Rules
                boolean allowBuild = kSec.getBoolean("rules.allow-build", false);
                boolean allowHunger = kSec.getBoolean("rules.allow-hunger", true);
                boolean comboMode = kSec.getBoolean("rules.combo-mode", false);
                boolean sumoMode = kSec.getBoolean("rules.sumo-mode", false);
                KitRules rules = new KitRules(allowBuild, allowHunger, comboMode, sumoMode);

                // Armor
                ItemStack[] armor = new ItemStack[4];
                armor[3] = parseItem(kSec.getString("armor.helmet"));
                armor[2] = parseItem(kSec.getString("armor.chestplate"));
                armor[1] = parseItem(kSec.getString("armor.leggings"));
                armor[0] = parseItem(kSec.getString("armor.boots"));

                // Offhand
                ItemStack offhand = parseItem(kSec.getString("offhand"));

                // Main items
                ItemStack[] main = new ItemStack[36];
                ConfigurationSection mainSec = kSec.getConfigurationSection("main-items");
                if (mainSec != null) {
                    for (String slotKey : mainSec.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotKey);
                            if (slot >= 0 && slot < 36) {
                                main[slot] = parseItem(mainSec.getString(slotKey));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                Kit kit = new Kit(key, displayName, icon, main, armor, offhand, Collections.emptyList(), rules);
                kits.put(key.toLowerCase(), kit);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load kit: " + key, e);
            }
        }

        plugin.getLogger().info("Successfully loaded " + kits.size() + " kits.");
    }

    @Nullable
    private ItemStack parseItem(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null) return null;

        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ItemStack(mat, amount);
    }

    @Nullable
    public Kit getKit(@NotNull String id) {
        return kits.get(id.toLowerCase());
    }

    @NotNull
    public Collection<Kit> getAllKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public void registerKit(@NotNull Kit kit) {
        kits.put(kit.getId(), kit);
    }
}
