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
            ItemStack item = new ItemStack(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + template.getDisplayName() + "</bold></gold> <dark_gray>(" + template.getId() + ")</dark_gray>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Status: " + (enabled ? "<green><bold>ENABLED</bold></green>" : "<red><bold>MAINTENANCE</bold></red>")));
                lore.add(TextUtil.parse("<gray>Regeneration: " + (template.isRegeneration() ? "<green>ACTIVE</green>" : "<red>DISABLED</red>")));
                lore.add(TextUtil.parse("<gray>Post-Match Return: " + (template.isTeleportToLobby() ? "<aqua>Lobby</aqua>" : "<yellow>Pre-Match Location</yellow>")));
                String modes = template.getAllowedModes().isEmpty() ? "ALL" : String.join(", ", template.getAllowedModes());
                lore.add(TextUtil.parse("<gray>Allowed Modes: <yellow>" + modes + "</yellow>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>▶ Click to edit arena settings</yellow>"));
                meta.lore(lore);

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

        player.openInventory(inv);
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        } catch (Throwable ignored) {}
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

        if (rawSlot == 49) {
            player.closeInventory();
            TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>Create New Arena Guide</bold></gradient>\n" +
                    "<yellow>1. Via Schematic:</yellow> <gray>Place file into <gold>maps/</gold> folder, then type: <gold>/arena import <file> <id> [name]</gold>\n" +
                    "<yellow>2. Via WorldEdit:</yellow> <gray>Select arena region with wand, then type: <gold>/arena create <id> [name]</gold>");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING)) {
            String arenaId = meta.getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
            if (arenaId != null) {
                plugin.getArenaDetailEditorGUI().open(player, arenaId);
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
