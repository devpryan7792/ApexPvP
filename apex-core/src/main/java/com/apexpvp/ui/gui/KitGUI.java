package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Responsive chest GUI displaying all competitive kits with mode/rule breakdowns
 * and one-click actions for ranked queuing and practice bot matches.
 */
public class KitGUI implements Listener {

    public static final class KitGUIHolder implements InventoryHolder {
        private final boolean editorMode;
        private Inventory inventory;

        public KitGUIHolder() {
            this(false);
        }

        public KitGUIHolder(boolean editorMode) {
            this.editorMode = editorMode;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public boolean isEditorMode() {
            return editorMode;
        }
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey kitKey;

    public KitGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.kitKey = new NamespacedKey(plugin, "kit_id");
    }

    public void open(@NotNull Player player) {
        open(player, false);
    }

    public void open(@NotNull Player player, boolean editorMode) {
        Collection<Kit> allKits = plugin.getKitManager().getAllKits();
        int size = allKits.size() > 7 ? 36 : 27;

        KitGUIHolder holder = new KitGUIHolder(editorMode);
        String title = editorMode ? "<gradient:#AA00FF:#FF55FF><bold>Select Kit to Edit Hotbar</bold></gradient>" : "<dark_gray>Select a Combat Kit";
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parse(title));
        holder.setInventory(inv);

        // Fill background with subtle dark gray stained glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        // Layout kits in centered or sequential slots
        int[] displaySlots;
        if (allKits.size() <= 5) {
            displaySlots = new int[]{11, 12, 13, 14, 15};
        } else if (allKits.size() <= 7) {
            displaySlots = new int[]{10, 11, 12, 13, 14, 15, 16};
        } else {
            displaySlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        }

        int slotIdx = 0;
        for (Kit kit : allKits) {
            if (slotIdx >= displaySlots.length) break;
            int targetSlot = displaySlots[slotIdx++];

            ItemStack item = kit.getIcon().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse(kit.getDisplayName()));

                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));

                KitRules rules = kit.getRules();
                String modeName = rules.sumoMode() ? "<gradient:#FF55FF:#FFAAFF>Sumo Mode</gradient>"
                        : (rules.comboMode() ? "<gradient:#00FFAA:#00AAFF>Combo (1.8 No-Delay)</gradient>"
                        : "<yellow>Standard PvP</yellow>");
                lore.add(TextUtil.parse("<gray>Combat Style: " + modeName));

                String buildStatus = rules.allowBuild() ? "<green>Enabled</green>" : "<red>Disabled</red>";
                lore.add(TextUtil.parse("<gray>Block Placement: " + buildStatus));

                String hungerStatus = rules.allowHunger() ? "<green>Active</green>" : "<red>Disabled</red>";
                lore.add(TextUtil.parse("<gray>Hunger Depletion: " + hungerStatus));

                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                if (editorMode) {
                    lore.add(TextUtil.parse("<gold><bold>CLICK</bold></gold> <yellow>» Open Hotbar Layout Editor</yellow>"));
                } else {
                    lore.add(TextUtil.parse("<gold><bold>LEFT-CLICK</bold></gold> <yellow>» Customize Hotbar Layout</yellow>"));
                    lore.add(TextUtil.parse("<aqua><bold>RIGHT-CLICK</bold></aqua> <yellow>» Solo Bot Practice</yellow>"));
                    lore.add(TextUtil.parse("<yellow><bold>SHIFT-CLICK</bold></yellow> <gray>» Queue Ranked Match</gray>"));
                }

                if (player.hasPermission("apexpvp.kit.admin")) {
                    lore.add(TextUtil.parse("<dark_purple><bold>MIDDLE-CLICK / SHIFT-RIGHT</bold></dark_purple> <light_purple>» Set Icon to Held Item</light_purple>"));
                }

                meta.lore(lore);
                meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.getId());
                item.setItemMeta(meta);
            }

            inv.setItem(targetSlot, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof KitGUIHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.CAVE_AIR || clicked.getType() == Material.VOID_AIR) {
                return;
            }

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) {
                return;
            }

            String kitId = meta.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
            if (kitId == null) {
                return;
            }

            Kit kit = plugin.getKitManager().getKit(kitId);
            if (kit == null) {
                return;
            }

            // Admin Quick Icon Customization from Held Item
            if (player.hasPermission("apexpvp.kit.admin") && (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE || (event.isShiftClick() && event.isRightClick()))) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.AIR) {
                    plugin.getKitManager().setKitIcon(kit.getId(), hand.clone());
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                    TextUtil.sendPrefixed(player, "<green>Customized icon of kit <yellow>" + kit.getId() + "</yellow> to held item <gold>" + hand.getType().name() + "</gold>!");
                    open(player, holder.isEditorMode());
                    return;
                } else {
                    TextUtil.sendPrefixed(player, "<red>Hold the item you want as the icon in your hand, then Middle-Click or Shift-Right-Click!");
                    return;
                }
            }

            if (event.isRightClick()) {
                player.closeInventory();
                player.performCommand("practice " + kit.getId());
                return;
            }

            if (event.isShiftClick()) {
                player.closeInventory();
                player.performCommand("queue " + kit.getId());
                return;
            }

            // Normal Left-Click:
            player.closeInventory();
            if (holder.isEditorMode() && player.hasPermission("apexpvp.kit.admin")) {
                plugin.getAdminKitEditorGUI().open(player, kit);
            } else {
                plugin.getKitEditorGUI().open(player, kit);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitGUIHolder) {
            event.setCancelled(true);
        }
    }
}
