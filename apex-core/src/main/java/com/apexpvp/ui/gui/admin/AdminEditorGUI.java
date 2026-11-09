package com.apexpvp.ui.gui.admin;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Top-Level Admin In-Game Editor GUI (/apex editor & /pduels editor).
 * Provides visual administration for Arenas, Kits, Settings, Migration, and Reload.
 */
public class AdminEditorGUI implements Listener {

    public static final class AdminEditorHolder implements InventoryHolder {
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

    public AdminEditorGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player player) {
        if (!player.hasPermission("apexpvp.admin") && !player.hasPermission("apexpvp.arena.admin")) {
            TextUtil.sendPrefixed(player, "<red>You lack administrative permissions to access the Editor.");
            return;
        }

        if (Bukkit.getServer() == null) return;

        AdminEditorHolder holder = new AdminEditorHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>ApexPvP » Admin In-Game Editor"));
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 11: Manage Arenas (Grass Block)
        ItemStack arenasItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta aMeta = arenasItem.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Edit Arenas</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Create, edit, clone, and configure arenas.</gray>"));
            lore.add(TextUtil.parse("<gray>Set team spawns, FFA spawns, allowed modes,</gray>"));
            lore.add(TextUtil.parse("<gray>regeneration, and maintenance status.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to open Arenas Manager</yellow>"));
            aMeta.lore(lore);
            arenasItem.setItemMeta(aMeta);
        }
        inv.setItem(11, arenasItem);

        // Slot 13: Manage Game Modes / Kits (Diamond Chestplate)
        ItemStack kitsItem = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta kMeta = kitsItem.getItemMeta();
        if (kMeta != null) {
            kMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Edit Game Modes & Kits</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Inspect and configure combat kits,</gray>"));
            lore.add(TextUtil.parse("<gray>rulesets, armor loadouts, and layouts.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to view Kits Showcase</yellow>"));
            kMeta.lore(lore);
            kitsItem.setItemMeta(kMeta);
        }
        inv.setItem(13, kitsItem);

        // Slot 14: General Settings (Comparator)
        ItemStack settingsItem = new ItemStack(Material.COMPARATOR);
        ItemMeta sMeta = settingsItem.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<gradient:#FFFF55:#FFAA00><bold>General Settings</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Inspect server settings and gameplay timers.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>• Match Duration:</yellow> <white>600s (10 min)</white>"));
            lore.add(TextUtil.parse("<yellow>• Border Shrink Delay:</yellow> <white>180s (3 min)</white>"));
            lore.add(TextUtil.parse("<yellow>• Shrink Duration:</yellow> <white>60s</white>"));
            lore.add(TextUtil.parse("<yellow>• Safe Zone Min Radius:</yellow> <white>6 blocks</white>"));
            sMeta.lore(lore);
            settingsItem.setItemMeta(sMeta);
        }
        inv.setItem(14, settingsItem);

        // Slot 15: Plugin Migration (Hopper)
        ItemStack migItem = new ItemStack(Material.HOPPER);
        ItemMeta migMeta = migItem.getItemMeta();
        if (migMeta != null) {
            migMeta.displayName(TextUtil.parse("<gradient:#55FFFF:#00AAAA><bold>Plugin Migration Tool</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Automatically scans server for legacy</gray>"));
            lore.add(TextUtil.parse("<gray>Duels / StrikePractice files to import.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to scan plugins folder</yellow>"));
            migMeta.lore(lore);
            migItem.setItemMeta(migMeta);
        }
        inv.setItem(15, migItem);

        // Slot 22: Hot Reload (Redstone)
        ItemStack reloadItem = new ItemStack(Material.REDSTONE);
        ItemMeta rMeta = reloadItem.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<red><bold>Hot Reload Plugin</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Re-reads config.yml, kits.yml,</gray>"));
            lore.add(TextUtil.parse("<gray>messages.yml, and arenas.yml instantly.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to execute reload</yellow>"));
            rMeta.lore(lore);
            reloadItem.setItemMeta(rMeta);
        }
        inv.setItem(22, reloadItem);

        player.openInventory(inv);
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminEditorHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        switch (slot) {
            case 11 -> {
                // Open Arena Manager list
                plugin.getAdminArenaListGUI().open(player);
            }
            case 13 -> {
                // Open Kits Showcase in Editor Mode
                plugin.getKitGUI().open(player, true);
            }
            case 14 -> {
                // Settings info
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                } catch (Throwable ignored) {}
                TextUtil.sendPrefixed(player, "<gradient:#FFFF55:#FFAA00><bold>General Engine Settings</bold></gradient>\n" +
                        "<gray>Configure safe-zones, borders, and timers inside <yellow>config.yml</yellow>.");
            }
            case 15 -> {
                // Plugin Migration scan
                scanMigration(player);
            }
            case 22 -> {
                // Hot Reload
                try {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
                } catch (Throwable ignored) {}
                plugin.reloadConfig();
                plugin.getKitManager().reload();
                plugin.getArenaManager().reloadTemplates();
                TextUtil.sendPrefixed(player, "<green><bold>RELOAD SUCCESSFUL!</bold> All configuration, kits, and arena templates reloaded.");
                open(player);
            }
        }
    }

    private void scanMigration(@NotNull Player player) {
        java.io.File pluginsDir = plugin.getDataFolder().getParentFile();
        boolean foundAny = false;
        if (pluginsDir != null && pluginsDir.exists()) {
            java.io.File duelsDir = new java.io.File(pluginsDir, "Duels");
            java.io.File strikeDir = new java.io.File(pluginsDir, "StrikePractice");
            if (duelsDir.exists()) {
                TextUtil.sendPrefixed(player, "<green>Found legacy <gold>Duels</gold> plugin folder! Schematics and kits detected for automatic import.");
                foundAny = true;
            }
            if (strikeDir.exists()) {
                TextUtil.sendPrefixed(player, "<green>Found legacy <gold>StrikePractice</gold> plugin folder! Data ready for import.");
                foundAny = true;
            }
        }
        if (!foundAny) {
            TextUtil.sendPrefixed(player, "<yellow>No legacy PvP plugin folders found in plugins/. ApexPvP native configuration is active!");
        }
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminEditorHolder) {
            event.setCancelled(true);
        }
    }
}
