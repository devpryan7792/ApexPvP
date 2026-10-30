package com.apexpvp.kit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import com.apexpvp.util.TextUtil;
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
    public static ItemStack parseItem(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        Material mat = Material.matchMaterial(parts[0].trim());
        if (mat == null) return null;

        int amount = 1;
        String metaStr = null;

        if (parts.length == 2) {
            if (parts[1].contains("=")) {
                metaStr = parts[1].trim();
            } else {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (parts.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                StringBuilder rest = new StringBuilder();
                for (int i = 2; i < parts.length; i++) {
                    if (!rest.isEmpty()) rest.append(":");
                    rest.append(parts[i]);
                }
                metaStr = rest.toString().trim();
            } catch (NumberFormatException ex) {
                StringBuilder rest = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (!rest.isEmpty()) rest.append(":");
                    rest.append(parts[i]);
                }
                metaStr = rest.toString().trim();
            }
        }

        ItemStack item = new ItemStack(mat, amount);
        if (metaStr != null && !metaStr.isBlank()) {
            applyMetadata(item, metaStr);
        }
        return item;
    }

    private static void applyMetadata(@NotNull ItemStack item, @NotNull String metaStr) {
        String[] tokens = metaStr.split(";");
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq == -1) continue;
            String key = token.substring(0, eq).trim().toUpperCase();
            String val = token.substring(eq + 1).trim();

            switch (key) {
                case "POTION" -> {
                    try {
                        if (item.getItemMeta() instanceof PotionMeta pm) {
                            PotionType pt = resolvePotionType(val);
                            if (pt != null) {
                                pm.setBasePotionType(pt);
                                item.setItemMeta(pm);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                case "ENCHANTS" -> {
                    String[] enchPairs = val.split(",");
                    for (String pair : enchPairs) {
                        String[] kv = pair.split("=");
                        if (kv.length == 2) {
                            Enchantment ench = resolveEnchantment(kv[0].trim());
                            if (ench != null) {
                                try {
                                    int lvl = Integer.parseInt(kv[1].trim());
                                    item.addUnsafeEnchantment(ench, lvl);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }
                case "NAME" -> {
                    try {
                        ItemMeta im = item.getItemMeta();
                        if (im != null) {
                            im.displayName(TextUtil.parse(val));
                            item.setItemMeta(im);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    @Nullable
    public static PotionType resolvePotionType(@NotNull String name) {
        String clean = name.trim().toUpperCase();
        try {
            return PotionType.valueOf(clean);
        } catch (IllegalArgumentException ignored) {
        }
        return switch (clean) {
            case "HEAL", "HEALING_2", "HEAL_2", "INSTANT_HEAL", "INSTANT_HEAL_2" -> PotionType.STRONG_HEALING;
            case "HARM", "HARMING_2", "HARM_2", "INSTANT_HARM", "INSTANT_DAMAGE", "INSTANT_DAMAGE_2" -> PotionType.STRONG_HARMING;
            case "HARMING", "HARM_1", "INSTANT_DAMAGE_1" -> PotionType.HARMING;
            case "SPEED", "SPEED_2", "SWIFTNESS_2" -> PotionType.STRONG_SWIFTNESS;
            case "FIRE_RES", "FIRE_RESISTANCE_LONG", "FIRE_RES_8" -> PotionType.LONG_FIRE_RESISTANCE;
            case "STRENGTH_2" -> PotionType.STRONG_STRENGTH;
            case "POISON_2" -> PotionType.STRONG_POISON;
            case "SLOWNESS_2" -> PotionType.STRONG_SLOWNESS;
            default -> null;
        };
    }

    @Nullable
    public static Enchantment resolveEnchantment(@NotNull String name) {
        try {
            String clean = name.trim().toLowerCase();
            try {
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(clean));
                if (ench != null) return ench;
            } catch (Throwable ignored) {
            }

            try {
                Enchantment ench = Enchantment.getByName(name.trim().toUpperCase());
                if (ench != null) return ench;
            } catch (Throwable ignored) {
            }

            return switch (clean) {
                case "sharpness", "sharp" -> Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));
                case "fire_aspect", "fire" -> Enchantment.getByKey(NamespacedKey.minecraft("fire_aspect"));
                case "unbreaking", "unb" -> Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
                case "knockback", "kb" -> Enchantment.getByKey(NamespacedKey.minecraft("knockback"));
                case "protection", "prot" -> Enchantment.getByKey(NamespacedKey.minecraft("protection"));
                case "feather_falling", "feather" -> Enchantment.getByKey(NamespacedKey.minecraft("feather_falling"));
                case "respiration" -> Enchantment.getByKey(NamespacedKey.minecraft("respiration"));
                case "aqua_affinity" -> Enchantment.getByKey(NamespacedKey.minecraft("aqua_affinity"));
                case "thorns" -> Enchantment.getByKey(NamespacedKey.minecraft("thorns"));
                case "depth_strider" -> Enchantment.getByKey(NamespacedKey.minecraft("depth_strider"));
                case "power" -> Enchantment.getByKey(NamespacedKey.minecraft("power"));
                case "punch" -> Enchantment.getByKey(NamespacedKey.minecraft("punch"));
                case "flame" -> Enchantment.getByKey(NamespacedKey.minecraft("flame"));
                case "infinity" -> Enchantment.getByKey(NamespacedKey.minecraft("infinity"));
                case "efficiency", "eff" -> Enchantment.getByKey(NamespacedKey.minecraft("efficiency"));
                case "mending", "mend" -> Enchantment.getByKey(NamespacedKey.minecraft("mending"));
                case "lunge" -> Enchantment.getByKey(NamespacedKey.minecraft("lunge"));
                default -> null;
            };
        } catch (Throwable ignored) {
            return null;
        }
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

    public boolean createKitFromPlayer(@NotNull String id, @NotNull String displayName, @NotNull org.bukkit.entity.Player player) {
        String key = id.toLowerCase();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] main = new ItemStack[36];
        System.arraycopy(contents, 0, main, 0, Math.min(contents.length, 36));

        ItemStack icon = (!isAir(main[0]))
                ? main[0].clone()
                : new ItemStack(Material.DIAMOND_SWORD);

        KitRules rules = new KitRules(false, true, false, false);
        Kit kit = new Kit(key, displayName, icon, main, armor, offhand, Collections.emptyList(), rules);
        registerKit(kit);
        saveKitToConfig(kit);
        return true;
    }

    public boolean updateKitFromPlayer(@NotNull String kitId, @NotNull org.bukkit.entity.Player player) {
        String key = kitId.toLowerCase();
        Kit existing = kits.get(key);
        if (existing == null) {
            return false;
        }

        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] main = new ItemStack[36];
        System.arraycopy(contents, 0, main, 0, Math.min(contents.length, 36));

        ItemStack icon = (!isAir(main[0]))
                ? main[0].clone()
                : existing.getIcon();

        Kit updated = new Kit(key, existing.getDisplayName(), icon, main, armor, offhand, existing.getPotionEffects(), existing.getRules());
        registerKit(updated);
        saveKitToConfig(updated);
        return true;
    }

    public boolean deleteKitFromConfig(@NotNull String kitId) {
        String key = kitId.toLowerCase();
        if (!kits.containsKey(key)) {
            return false;
        }
        kits.remove(key);

        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("kits." + key, null);
            try {
                config.save(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove kit " + key + " from kits.yml", e);
            }
        }
        return true;
    }

    public void saveKitToConfig(@NotNull Kit kit) {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "kits." + kit.getId();

        config.set(path + ".display-name", kit.getDisplayName());
        config.set(path + ".icon", kit.getIcon().getType().name());

        KitRules rules = kit.getRules();
        config.set(path + ".rules.allow-build", rules.allowBuild());
        config.set(path + ".rules.allow-hunger", rules.allowHunger());
        config.set(path + ".rules.combo-mode", rules.comboMode());
        config.set(path + ".rules.sumo-mode", rules.sumoMode());

        ItemStack[] armor = kit.getArmorContents();
        config.set(path + ".armor.helmet", serializeItem(armor.length > 3 ? armor[3] : null));
        config.set(path + ".armor.chestplate", serializeItem(armor.length > 2 ? armor[2] : null));
        config.set(path + ".armor.leggings", serializeItem(armor.length > 1 ? armor[1] : null));
        config.set(path + ".armor.boots", serializeItem(armor.length > 0 ? armor[0] : null));

        config.set(path + ".offhand", serializeItem(kit.getOffHand()));

        config.set(path + ".main-items", null); // Clear old slots
        ItemStack[] main = kit.getMainContents();
        for (int i = 0; i < main.length; i++) {
            String itemStr = serializeItem(main[i]);
            if (itemStr != null) {
                config.set(path + ".main-items." + i, itemStr);
            }
        }

        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save kit " + kit.getId() + " to kits.yml", e);
        }
    }

    private static boolean isAir(@Nullable ItemStack item) {
        if (item == null) return true;
        Material mat = item.getType();
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    @Nullable
    public static String serializeItem(@Nullable ItemStack item) {
        if (isAir(item)) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(item.getType().name());

        int amount = item.getAmount();
        List<String> metaTokens = new ArrayList<>();

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta pm) {
                PotionType pt = pm.getBasePotionType();
                if (pt != null && pt != PotionType.WATER) {
                    metaTokens.add("POTION=" + pt.name());
                }
            }

            if (meta != null && meta.hasEnchants()) {
                Map<Enchantment, Integer> enchants = meta.getEnchants();
                List<String> enchEntries = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    String enchKey;
                    try {
                        enchKey = entry.getKey().getKey().getKey();
                    } catch (Throwable t) {
                        enchKey = entry.getKey().getName().toLowerCase();
                    }
                    enchEntries.add(enchKey + "=" + entry.getValue());
                }
                if (!enchEntries.isEmpty()) {
                    metaTokens.add("ENCHANTS=" + String.join(",", enchEntries));
                }
            }
        } catch (Throwable ignored) {
        }

        if (!metaTokens.isEmpty()) {
            sb.append(":").append(amount).append(":").append(String.join(";", metaTokens));
        } else if (amount > 1) {
            sb.append(":").append(amount);
        }

        return sb.toString();
    }
}
