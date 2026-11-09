package com.apexpvp.ui.gui.admin;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Visual Administrative Kit Content & Default Layout Editor.
 * Unlike the player profile hotbar editor, this GUI modifies the kit definition itself,
 * establishing the global default layout and items for all players (saved directly into kits.yml).
 *
 * Full two-way inventory manipulation is permitted: admins can transfer items freely
 * between their inventory and the kit, drag items outside to delete them, snapshot their
 * entire inventory in one click, and save global changes.
 */
public class AdminKitEditorGUI implements Listener {

    public static final class AdminKitEditorHolder implements InventoryHolder {
        private final Kit kit;
        private ItemStack kitIcon;
        private Inventory inventory;

        public AdminKitEditorHolder(@NotNull Kit kit) {
            this.kit = Objects.requireNonNull(kit, "kit cannot be null");
            this.kitIcon = kit.getIcon() != null ? kit.getIcon().clone() : new ItemStack(Material.DIAMOND_SWORD);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        public Kit getKit() {
            return kit;
        }

        @NotNull
        public ItemStack getKitIcon() {
            return kitIcon;
        }

        public void setKitIcon(@NotNull ItemStack kitIcon) {
            this.kitIcon = kitIcon;
        }
    }

    private static final Set<Integer> CONTROL_SLOTS = Set.of(4, 6, 7, 8, 45, 47, 49, 51, 53);

    private final ApexPvPPlugin plugin;

    public AdminKitEditorGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Opens the administrative kit content editor for a kit.
     */
    public void open(@NotNull Player player, @NotNull Kit kit) {
        if (!player.hasPermission("apexpvp.kit.admin")) {
            TextUtil.sendPrefixed(player, "<red>You lack administrative permissions to configure global kits.");
            return;
        }

        AdminKitEditorHolder holder = new AdminKitEditorHolder(kit);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.parse("<dark_gray>Admin Kit Editor » </dark_gray>" + kit.getDisplayName()));
        holder.setInventory(inv);

        // 1. Armor row (slots 0-3: Helmet, Chestplate, Leggings, Boots)
        ItemStack[] armor = kit.getArmorContents(); // boots=0, legs=1, chest=2, helm=3
        inv.setItem(0, (armor.length > 3 && !isAir(armor[3])) ? armor[3].clone() : null);
        inv.setItem(1, (armor.length > 2 && !isAir(armor[2])) ? armor[2].clone() : null);
        inv.setItem(2, (armor.length > 1 && !isAir(armor[1])) ? armor[1].clone() : null);
        inv.setItem(3, (armor.length > 0 && !isAir(armor[0])) ? armor[0].clone() : null);

        // Slot 4: Divider
        inv.setItem(4, createDivider("<gray>« Armor | Offhand »</gray>"));

        // Slot 5: Offhand
        ItemStack offhand = kit.getOffHand();
        inv.setItem(5, (!isAir(offhand)) ? offhand.clone() : null);

        // Slot 6: Divider
        inv.setItem(6, createDivider("<gray>« Controls »</gray>"));

        // Slot 7: Quick Snapshot Inventory
        ItemStack snapItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta snapMeta = snapItem.getItemMeta();
        if (snapMeta != null) {
            snapMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Snapshot My Inventory</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Replaces all items in this editor with your</gray>"));
            lore.add(TextUtil.parse("<gray>current player armor, offhand, and inventory.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to snapshot now</yellow>"));
            snapMeta.lore(lore);
            snapItem.setItemMeta(snapMeta);
        }
        inv.setItem(7, snapItem);

        // Slot 8: Set Kit Icon
        updateIconSlot(inv, holder.getKitIcon());

        // 2. Backpack items (Kit main 9-35 -> GUI slots 9-35)
        ItemStack[] main = kit.getMainContents();
        for (int i = 9; i < 36; i++) {
            if (i < main.length && !isAir(main[i])) {
                inv.setItem(i, main[i].clone());
            }
        }

        // 3. Hotbar items (Kit main 0-8 -> GUI slots 36-44)
        for (int i = 0; i < 9; i++) {
            if (i < main.length && !isAir(main[i])) {
                inv.setItem(36 + i, main[i].clone());
            }
        }

        // 4. Control buttons (Row 5: 45-53)
        // Slot 45: Save as Global Default (Emerald Block)
        ItemStack saveItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = saveItem.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<green><bold>SAVE AS GLOBAL DEFAULT</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Saves this exact layout and item loadout</gray>"));
            lore.add(TextUtil.parse("<gray>directly to <yellow>kits.yml</yellow> as the universal</gray>"));
            lore.add(TextUtil.parse("<gray>default for all players who play this kit.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to commit & save global default</yellow>"));
            sMeta.lore(lore);
            saveItem.setItemMeta(sMeta);
        }
        inv.setItem(45, saveItem);

        // Slot 46: Divider
        inv.setItem(46, createDivider("<gray>───────</gray>"));

        // Slot 47: Clear All (TNT)
        ItemStack clearItem = new ItemStack(Material.TNT);
        ItemMeta clMeta = clearItem.getItemMeta();
        if (clMeta != null) {
            clMeta.displayName(TextUtil.parse("<red><bold>Clear All Kit Items</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Empties all armor, offhand, backpack,</gray>"));
            lore.add(TextUtil.parse("<gray>and hotbar slots in this editor.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_red>▶ Click to clear contents</dark_red>"));
            clMeta.lore(lore);
            clearItem.setItemMeta(clMeta);
        }
        inv.setItem(47, clearItem);

        // Slot 48: Divider
        inv.setItem(48, createDivider("<gray>───────</gray>"));

        // Slot 49: Help / Info (Book)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta iMeta = infoItem.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(TextUtil.parse("<gold><bold>Admin Kit Editor Guide</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>• Freely drag items between your inventory & the kit</gray>"));
            lore.add(TextUtil.parse("<gray>• Drop items out or press Q to delete them from kit</gray>"));
            lore.add(TextUtil.parse("<gray>• Row 0: Helmet, Chest, Legs, Boots (0-3) & Offhand (5)</gray>"));
            lore.add(TextUtil.parse("<gray>• Rows 1-3: Backpack items (slots 9-35)</gray>"));
            lore.add(TextUtil.parse("<gray>• Row 4: Hotbar items (slots 0-8)</gray>"));
            lore.add(TextUtil.parse("<gray>• Click <green>Emerald Block</green> to save for ALL players</gray>"));
            iMeta.lore(lore);
            infoItem.setItemMeta(iMeta);
        }
        inv.setItem(49, infoItem);

        // Slot 50: Divider
        inv.setItem(50, createDivider("<gray>───────</gray>"));

        // Slot 51: Reload from Config (Milk Bucket)
        ItemStack reloadItem = new ItemStack(Material.MILK_BUCKET);
        ItemMeta rMeta = reloadItem.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<yellow><bold>Revert / Reload From Disk</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Discards unsaved edits and reloads</gray>"));
            lore.add(TextUtil.parse("<gray>the kit definition from <yellow>kits.yml</yellow>.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to revert changes</yellow>"));
            rMeta.lore(lore);
            reloadItem.setItemMeta(rMeta);
        }
        inv.setItem(51, reloadItem);

        // Slot 52: Divider
        inv.setItem(52, createDivider("<gray>───────</gray>"));

        // Slot 53: Close / Exit (Barrier)
        ItemStack exitItem = new ItemStack(Material.BARRIER);
        ItemMeta eMeta = exitItem.getItemMeta();
        if (eMeta != null) {
            eMeta.displayName(TextUtil.parse("<red><bold>Close Editor</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Return to Kit Selection menu.</gray>"));
            eMeta.lore(lore);
            exitItem.setItemMeta(eMeta);
        }
        inv.setItem(53, exitItem);

        player.openInventory(inv);
    }

    private void updateIconSlot(@NotNull Inventory inv, @NotNull ItemStack currentIcon) {
        ItemStack display = currentIcon.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Kit Display Icon</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current Material: <yellow>" + currentIcon.getType().name() + "</yellow></gray>"));
            lore.add(TextUtil.parse("<gray>This icon is displayed in all duel, queue,</gray>"));
            lore.add(TextUtil.parse("<gray>and party kit selection menus.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to set icon from item held in main hand</yellow>"));
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(8, display);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminKitEditorHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        // Clicking outside drops/deletes item if dragged from top
        if (rawSlot == -999) {
            return; // Allow standard drop/deletion
        }

        // Bottom inventory clicks (player's inventory) are fully allowed so they can move items
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }

        // Control slot clicks in top inventory
        if (CONTROL_SLOTS.contains(rawSlot)) {
            event.setCancelled(true);

            switch (rawSlot) {
                case 7 -> {
                    // Snapshot player inventory into editor
                    handleSnapshot(player, event.getInventory());
                }
                case 8 -> {
                    // Set kit icon from held item
                    handleSetIcon(player, holder, event.getInventory());
                }
                case 45 -> {
                    // Save as global default
                    handleSave(player, holder, event.getInventory());
                }
                case 47 -> {
                    // Clear all items
                    handleClear(player, event.getInventory());
                }
                case 49 -> {
                    // Guide info
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    } catch (Throwable ignored) {}
                }
                case 51 -> {
                    // Reload from disk
                    player.closeInventory();
                    plugin.getKitManager().reload();
                    Kit fresh = plugin.getKitManager().getKit(holder.getKit().getId());
                    if (fresh != null) {
                        try {
                            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                        } catch (Throwable ignored) {}
                        TextUtil.sendPrefixed(player, "<yellow>Reloaded kit definition from <gold>kits.yml</gold>.");
                        open(player, fresh);
                    }
                }
                case 53 -> {
                    // Close editor and open kit list
                    player.closeInventory();
                    plugin.getKitGUI().open(player, true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminKitEditorHolder)) {
            return;
        }

        // Cancel drag only if it touches control slots
        for (int slot : event.getRawSlots()) {
            if (CONTROL_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleSnapshot(@NotNull Player player, @NotNull Inventory inv) {
        ItemStack[] pArmor = player.getInventory().getArmorContents();
        ItemStack pOffhand = player.getInventory().getItemInOffHand();
        ItemStack[] pContents = player.getInventory().getContents();

        // 1. Armor (0-3)
        inv.setItem(0, (pArmor.length > 3 && !isAir(pArmor[3])) ? pArmor[3].clone() : null);
        inv.setItem(1, (pArmor.length > 2 && !isAir(pArmor[2])) ? pArmor[2].clone() : null);
        inv.setItem(2, (pArmor.length > 1 && !isAir(pArmor[1])) ? pArmor[1].clone() : null);
        inv.setItem(3, (pArmor.length > 0 && !isAir(pArmor[0])) ? pArmor[0].clone() : null);

        // 2. Offhand (5)
        inv.setItem(5, (!isAir(pOffhand)) ? pOffhand.clone() : null);

        // 3. Backpack (9-35)
        for (int i = 9; i < 36; i++) {
            inv.setItem(i, (i < pContents.length && !isAir(pContents[i])) ? pContents[i].clone() : null);
        }

        // 4. Hotbar (36-44)
        for (int i = 0; i < 9; i++) {
            inv.setItem(36 + i, (i < pContents.length && !isAir(pContents[i])) ? pContents[i].clone() : null);
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } catch (Throwable ignored) {}
        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>SNAPSHOT COMPLETE!</bold></gradient> Copied your personal inventory into the editor. Click <green>Save</green> to commit!");
    }

    private void handleSetIcon(@NotNull Player player, @NotNull AdminKitEditorHolder holder, @NotNull Inventory inv) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isAir(hand)) {
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
            TextUtil.sendPrefixed(player, "<red>Hold the item in your main hand that you want as the kit icon, then click this button!");
            return;
        }

        ItemStack newIcon = hand.clone();
        holder.setKitIcon(newIcon);
        updateIconSlot(inv, newIcon);

        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
        } catch (Throwable ignored) {}
        TextUtil.sendPrefixed(player, "<green>Kit icon preview updated to held item <gold>" + newIcon.getType().name() + "</gold>!");
    }

    private void handleClear(@NotNull Player player, @NotNull Inventory inv) {
        for (int i = 0; i < 4; i++) inv.setItem(i, null);
        inv.setItem(5, null);
        for (int i = 9; i < 45; i++) inv.setItem(i, null);

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
        TextUtil.sendPrefixed(player, "<yellow>Cleared all kit items from editor. Click <green>Save</green> when ready.");
    }

    private void handleSave(@NotNull Player player, @NotNull AdminKitEditorHolder holder, @NotNull Inventory inv) {
        Kit kit = holder.getKit();

        // 1. Armor array (boots=0, legs=1, chest=2, helm=3)
        ItemStack[] armor = new ItemStack[4];
        armor[3] = cloneOrAir(inv.getItem(0));
        armor[2] = cloneOrAir(inv.getItem(1));
        armor[1] = cloneOrAir(inv.getItem(2));
        armor[0] = cloneOrAir(inv.getItem(3));

        // 2. Offhand
        ItemStack offhand = cloneOrAir(inv.getItem(5));

        // 3. Main contents (0-8: Hotbar from GUI 36-44, 9-35: Backpack from GUI 9-35)
        ItemStack[] main = new ItemStack[36];
        for (int i = 0; i < 9; i++) {
            main[i] = cloneOrAir(inv.getItem(36 + i));
        }
        for (int i = 9; i < 36; i++) {
            main[i] = cloneOrAir(inv.getItem(i));
        }

        // 4. Icon
        ItemStack icon = holder.getKitIcon().clone();

        Kit updated = new Kit(kit.getId(), kit.getDisplayName(), icon, main, armor, offhand, kit.getPotionEffects(), kit.getRules());

        plugin.getKitManager().registerKit(updated);
        plugin.getKitManager().saveKitToConfig(updated);

        try {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        } catch (Throwable ignored) {}

        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>GLOBAL DEFAULT SAVED!</bold></gradient>\n" +
                "<gray>Kit <gold>" + kit.getId() + "</gold> contents & default hotbar layout have been updated in <yellow>kits.yml</yellow> for all players!</gray>");
    }

    @NotNull
    private static ItemStack createDivider(@NotNull String name) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static boolean isAir(@Nullable ItemStack item) {
        if (item == null) return true;
        Material mat = item.getType();
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    @NotNull
    private static ItemStack cloneOrAir(@Nullable ItemStack item) {
        return (item != null && !item.getType().isAir()) ? item.clone() : new ItemStack(Material.AIR);
    }
}
