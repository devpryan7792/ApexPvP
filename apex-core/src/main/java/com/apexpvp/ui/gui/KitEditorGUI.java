package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.HotbarLayoutValidator;
import com.apexpvp.kit.HotbarSerializer;
import com.apexpvp.kit.Kit;
import com.apexpvp.profile.PlayerProfile;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Interactive 54-slot chest GUI allowing players to visually rearrange items
 * between backpack slots (0-26) and hotbar slots (36-44) for any combat kit.
 */
public class KitEditorGUI implements Listener {

    public static final class KitEditorGUIHolder implements InventoryHolder {
        private final Kit kit;
        private Inventory inventory;

        public KitEditorGUIHolder(@NotNull Kit kit) {
            this.kit = Objects.requireNonNull(kit, "kit cannot be null");
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
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey sourceSlotKey;

    public KitEditorGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.sourceSlotKey = new NamespacedKey(plugin, "kit_source_slot");
    }

    /**
     * Opens the visual kit layout editor for a player and target kit.
     */
    public void open(@NotNull Player player, @NotNull Kit kit) {
        KitEditorGUIHolder holder = new KitEditorGUIHolder(kit);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.parse("<dark_gray>Edit Layout: </dark_gray>" + kit.getDisplayName()));
        holder.setInventory(inv);

        // Fetch existing custom hotbar layout if configured
        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        String customLayout = profile != null ? profile.getCustomHotbar(kit.getId()) : null;
        Map<Integer, Integer> mapping = customLayout != null ? HotbarSerializer.deserialize(customLayout) : null;

        ItemStack[] main = kit.getMainContents();
        ItemStack[] tagged = new ItemStack[36];

        for (int i = 0; i < 36; i++) {
            if (!isAir(main[i])) {
                ItemStack item = main[i].clone();
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(sourceSlotKey, PersistentDataType.INTEGER, i);
                    item.setItemMeta(meta);
                }
                tagged[i] = item;
            }
        }

        // Apply custom hotbar mapping if present and valid
        ItemStack[] arranged = new ItemStack[36];
        System.arraycopy(tagged, 0, arranged, 0, 36);

        if (mapping != null && HotbarLayoutValidator.isValid(mapping)) {
            for (int src : mapping.values()) {
                if (src < arranged.length) arranged[src] = null;
            }
            for (int target : mapping.keySet()) {
                if (target < 9) arranged[target] = null;
            }
            for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                int target = entry.getKey();
                int src = entry.getValue();
                if (src < tagged.length && tagged[src] != null) {
                    arranged[target] = tagged[src].clone();
                }
            }
        }

        // 1. Fill backpack slots (inventory 9-35 -> GUI slots 0-26)
        for (int i = 9; i < 36; i++) {
            inv.setItem(i - 9, arranged[i]);
        }

        // 2. Control & Divider panel (GUI slots 27-35)
        ItemStack borderPane = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int i = 27; i <= 35; i++) {
            inv.setItem(i, borderPane);
        }

        // Slot 30: Save Button
        ItemStack saveBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta saveMeta = saveBtn.getItemMeta();
        if (saveMeta != null) {
            saveMeta.displayName(TextUtil.parse("<green><bold>SAVE LAYOUT</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Click to save your customized</gray>"));
            lore.add(TextUtil.parse("<gray>hotbar layout for " + kit.getDisplayName() + "<gray>.</gray>"));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<yellow>Automatically loaded in matches!</yellow>"));
            saveMeta.lore(lore);
            saveBtn.setItemMeta(saveMeta);
        }
        inv.setItem(30, saveBtn);

        // Slot 31: Help Book
        ItemStack helpBook = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpBook.getItemMeta();
        if (helpMeta != null) {
            helpMeta.displayName(TextUtil.parse("<aqua><bold>Editor Guide</bold></aqua>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>• Drag & drop items freely.</gray>"));
            lore.add(TextUtil.parse("<gray>• Row 5 is your active <yellow>Hotbar</yellow>.</gray>"));
            lore.add(TextUtil.parse("<gray>• Rows 1-3 are your <yellow>Backpack</yellow>.</gray>"));
            lore.add(TextUtil.parse("<gray>• Click <green>Save</green> when satisfied.</gray>"));
            helpMeta.lore(lore);
            helpBook.setItemMeta(helpMeta);
        }
        inv.setItem(31, helpBook);

        // Slot 32: Reset Button
        ItemStack resetBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta resetMeta = resetBtn.getItemMeta();
        if (resetMeta != null) {
            resetMeta.displayName(TextUtil.parse("<red><bold>RESET DEFAULT</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Click to discard custom layout</gray>"));
            lore.add(TextUtil.parse("<gray>and revert to kit default.</gray>"));
            resetMeta.lore(lore);
            resetBtn.setItemMeta(resetMeta);
        }
        inv.setItem(32, resetBtn);

        // 3. Hotbar row (inventory 0-8 -> GUI slots 36-44)
        for (int i = 0; i < 9; i++) {
            inv.setItem(36 + i, arranged[i]);
        }

        // 4. Hotbar indicator row (GUI slots 45-53)
        for (int i = 0; i < 9; i++) {
            ItemStack indicator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta indMeta = indicator.getItemMeta();
            if (indMeta != null) {
                indMeta.displayName(TextUtil.parse("<yellow>Hotbar Slot " + (i + 1) + "</yellow>"));
                List<Component> indLore = new ArrayList<>();
                indLore.add(TextUtil.parse("<dark_gray>Key: [" + (i + 1) + "]</dark_gray>"));
                indMeta.lore(indLore);
                indicator.setItemMeta(indMeta);
            }
            inv.setItem(45 + i, indicator);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorGUIHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Cancel interactions with the player's personal inventory to prevent theft
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            event.setCancelled(true);
            return;
        }

        // Cancel shift-clicks to prevent items flying into bottom inventory
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            event.setCancelled(true);
            return;
        }

        // Non-interactable control or indicator slots
        if ((slot >= 27 && slot <= 35) || (slot >= 45 && slot <= 53)) {
            event.setCancelled(true);

            if (slot == 30) {
                // Save layout
                handleSave(player, holder.getKit(), event.getInventory());
            } else if (slot == 32) {
                // Reset layout
                handleReset(player, holder.getKit());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorGUIHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            // Cancel drag if touching bottom inventory or control/indicator rows
            if (rawSlot >= 54 || (rawSlot >= 27 && rawSlot <= 35) || (rawSlot >= 45 && rawSlot <= 53)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorGUIHolder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            player.setItemOnCursor(null);
        }
    }

    private void handleSave(@NotNull Player player, @NotNull Kit kit, @NotNull Inventory inv) {
        Map<Integer, Integer> mapping = new HashMap<>();

        for (int targetHotbar = 0; targetHotbar < 9; targetHotbar++) {
            int guiSlot = 36 + targetHotbar;
            ItemStack item = inv.getItem(guiSlot);
            if (!isAir(item) && item.hasItemMeta()) {
                Integer sourceSlot = item.getItemMeta().getPersistentDataContainer().get(sourceSlotKey, PersistentDataType.INTEGER);
                if (sourceSlot != null && sourceSlot >= 0 && sourceSlot < 36) {
                    mapping.put(targetHotbar, sourceSlot);
                }
            }
        }

        if (!HotbarLayoutValidator.isValid(mapping)) {
            TextUtil.sendPrefixed(player, "<red>Invalid layout: duplicate items detected in hotbar.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String serialized = HotbarSerializer.serialize(mapping);
        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile != null) {
            profile.setCustomHotbar(kit.getId(), serialized);
            plugin.getProfileManager().saveProfile(profile);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        TextUtil.sendPrefixed(player, "<green>Custom hotbar layout saved for " + kit.getDisplayName() + "<green>!");
        player.closeInventory();
    }

    private void handleReset(@NotNull Player player, @NotNull Kit kit) {
        plugin.getProfileManager().resetCustomHotbar(player.getUniqueId(), kit.getId());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        TextUtil.sendPrefixed(player, "<yellow>Reset hotbar layout for " + kit.getDisplayName() + "<yellow> to default.");
        player.closeInventory();
    }

    private static boolean isAir(@Nullable ItemStack item) {
        if (item == null) return true;
        Material mat = item.getType();
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    @NotNull
    private static ItemStack createGlassPane(@NotNull Material material, @NotNull Component name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
