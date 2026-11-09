package com.apexpvp.ui.gui.admin;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Admin Arenas List GUI.
 * Shows all arenas with visual status (Enabled vs Maintenance), allowing admins to pick an arena to edit.
 */
public class AdminArenaListGUI implements Listener {

    public static final class ArenaListHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey arenaKey;

    public AdminArenaListGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaKey = new NamespacedKey(plugin, "admin_arena_id");
    }

    public void open(@NotNull Player player) {
        if (!player.hasPermission("apexpvp.arena.admin")) {
            TextUtil.sendPrefixed(player, "<red>You lack permissions to configure arenas.");
            return;
        }

        if (Bukkit.getServer() == null) return;

        ArenaListHolder holder = new ArenaListHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.parse("<dark_gray>Admin Editor » Manage Arenas"));
        holder.setInventory(inv);

        // Fill border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(Component.empty());
            border.setItemMeta(bMeta);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        int slot = 0;
        for (ArenaTemplate template : plugin.getArenaManager().getAllTemplates()) {
            if (slot >= 45) break;

            boolean enabled = template.isEnabled();
            Material mat = null;
            if (template.getIconMaterial() != null) {
                mat = Material.matchMaterial(template.getIconMaterial());
            }
            if (mat == null) {
                mat = getArenaIcon(template.getId());
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + template.getDisplayName() + "</bold></gold> <dark_gray>(" + template.getId() + ")</dark_gray>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                String typeBadge = template.isPrebuilt()
                        ? "<gradient:#FFAA00:#FF5555>[Prebuilt Arena]</gradient>"
                        : "<gradient:#00FFAA:#00AAFF>[Custom Map]</gradient>";
                lore.add(TextUtil.parse("<gray>Type: " + typeBadge));
                lore.add(TextUtil.parse("<gray>Status: " + (enabled ? "<green><bold>✔ ENABLED</bold></green>" : "<red><bold>✖ MAINTENANCE</bold></red>")));
                int width = template.getRelativeBounds().getMaxX() - template.getRelativeBounds().getMinX();
                int length = template.getRelativeBounds().getMaxZ() - template.getRelativeBounds().getMinZ();
                lore.add(TextUtil.parse("<gray>Dimensions: <white>" + width + "x" + length + " blocks</white>"));
                lore.add(TextUtil.parse("<gray>Regeneration: " + (template.isRegeneration() ? "<green>ACTIVE</green>" : "<red>DISABLED</red>")));
                String modes = template.getAllowedModes().isEmpty() ? "ALL MODES" : String.join(", ", template.getAllowedModes());
                lore.add(TextUtil.parse("<gray>Allowed Modes: <yellow>" + modes + "</yellow>"));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Open Detailed Editor</white>"));
                lore.add(TextUtil.parse("<gold>▶ Right-Click:</gold> <yellow>Quick-Toggle Active/Maintenance</yellow>"));
                meta.lore(lore);

                if (enabled) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }

                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, template.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Slot 45: Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(TextUtil.parse("<yellow>« Back to Admin Menu</yellow>"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        // Slot 47: Toggle Prebuilt Arenas
        boolean prebuiltActive = plugin.getArenaManager().arePrebuiltArenasEnabled();
        ItemStack prebuiltToggle = new ItemStack(prebuiltActive ? Material.LEVER : Material.REDSTONE_TORCH);
        ItemMeta pMeta = prebuiltToggle.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Toggle Prebuilt Arenas</bold></gradient>"));
            List<Component> pLore = new ArrayList<>();
            pLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            pLore.add(TextUtil.parse("<gray>Prebuilt Maps: " + (prebuiltActive ? "<green><bold>ENABLED</bold></green>" : "<red><bold>DISABLED</bold></red>")));
            pLore.add(TextUtil.parse("<gray>Quickly enable or disable all built-in</gray>"));
            pLore.add(TextUtil.parse("<gray>plugin arenas with one click.</gray>"));
            pLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            pLore.add(TextUtil.parse("<yellow><bold>CLICK</bold></yellow> <white>» Toggle all prebuilt arenas</white>"));
            pMeta.lore(pLore);
            prebuiltToggle.setItemMeta(pMeta);
        }
        inv.setItem(47, prebuiltToggle);

        // Slot 49: Create Arena
        ItemStack create = new ItemStack(Material.NETHER_STAR);
        ItemMeta cMeta = create.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>+ Create / Import New Arena</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Drop your .schem file in plugins/ApexPvP/maps/</gray>"));
            lore.add(TextUtil.parse("<gray>and run: <gold>/arena import <file> <id> [name]</gold></gray>"));
            lore.add(TextUtil.parse("<gray>or select region and run: <gold>/arena create <id></gold></gray>"));
            cMeta.lore(lore);
            create.setItemMeta(cMeta);
        }
        inv.setItem(49, create);

        // Slot 51: Reload Arenas
        ItemStack reload = new ItemStack(Material.REDSTONE);
        ItemMeta rMeta = reload.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<green><bold>Reload Arenas Configuration</bold></green>"));
            List<Component> rLore = new ArrayList<>();
            rLore.add(TextUtil.parse("<gray>Reloads arenas.yml from disk and</gray>"));
            rLore.add(TextUtil.parse("<gray>refreshes active template instances.</gray>"));
            rLore.add(TextUtil.parse("<yellow><bold>CLICK</bold></yellow> <white>» Reload configuration</white>"));
            rMeta.lore(rLore);
            reload.setItemMeta(rMeta);
        }
        inv.setItem(51, reload);

        player.openInventory(inv);
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        } catch (Throwable ignored) {}
    }

    private Material getArenaIcon(@NotNull String id) {
        return switch (id.toLowerCase()) {
            case "colosseum" -> Material.CRACKED_STONE_BRICKS;
            case "temple" -> Material.QUARTZ_PILLAR;
            case "netherforge" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "cybervoid" -> Material.CRYING_OBSIDIAN;
            case "desertdune" -> Material.SMOOTH_SANDSTONE;
            case "frostpeak" -> Material.BLUE_ICE;
            case "sumo" -> Material.SLIME_BLOCK;
            case "celestial" -> Material.GOLD_BLOCK;
            case "glaciem" -> Material.PACKED_ICE;
            case "spiritual" -> Material.AMETHYST_BLOCK;
            default -> Material.STONE_BRICKS;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ArenaListHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot == 45) {
            // Back to Admin Hub
            plugin.getAdminEditorGUI().open(player);
            return;
        }

        if (rawSlot == 47) {
            // Toggle Prebuilt Arenas
            boolean newState = plugin.getArenaManager().togglePrebuiltArenas();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            TextUtil.sendPrefixed(player, newState
                    ? "<green>All prebuilt plugin arenas have been <bold>ENABLED</bold>."
                    : "<yellow>All prebuilt plugin arenas have been <bold>DISABLED (MAINTENANCE)</bold>.");
            open(player);
            return;
        }

        if (rawSlot == 49) {
            player.closeInventory();
            TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>Create New Arena Guide</bold></gradient>\n" +
                    "<yellow>1. Via Schematic:</yellow> <gray>Place file into <gold>maps/</gold> folder, then type: <gold>/arena import <file> <id> [name]</gold>\n" +
                    "<yellow>2. Via WorldEdit:</yellow> <gray>Select arena region with wand, then type: <gold>/arena create <id> [name]</gold>");
            return;
        }

        if (rawSlot == 51) {
            plugin.getArenaManager().reloadTemplates();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            TextUtil.sendPrefixed(player, "<green>Reloaded arena templates from arenas.yml!");
            open(player);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING)) {
            String arenaId = meta.getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
            if (arenaId != null) {
                if (event.isRightClick()) {
                    boolean next = plugin.getArenaManager().toggleArenaEnabled(arenaId);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    TextUtil.sendPrefixed(player, next
                            ? "<green>Arena '<gold>" + arenaId + "</gold>' is now <bold>ENABLED</bold>."
                            : "<yellow>Arena '<gold>" + arenaId + "</gold>' is now in <bold>MAINTENANCE</bold>.");
                    open(player);
                } else {
                    plugin.getArenaDetailEditorGUI().open(player, arenaId);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ArenaListHolder) {
            event.setCancelled(true);
        }
    }
}
