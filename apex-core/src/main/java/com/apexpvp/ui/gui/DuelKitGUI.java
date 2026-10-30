package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Interactive chest GUI presented when initiating a /duel <target> challenge,
 * allowing the player to visually select which kit to duel with.
 */
public class DuelKitGUI implements Listener {

    public static final class DuelKitGUIHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private final double stake;
        private Inventory inventory;

        public DuelKitGUIHolder(@NotNull UUID targetUuid, @NotNull String targetName, double stake) {
            this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
            this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
            this.stake = stake;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        public UUID getTargetUuid() {
            return targetUuid;
        }

        @NotNull
        public String getTargetName() {
            return targetName;
        }

        public double getStake() {
            return stake;
        }
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey kitKey;

    public DuelKitGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.kitKey = new NamespacedKey(plugin, "duel_kit_id");
    }

    public void open(@NotNull Player challenger, @NotNull Player target) {
        open(challenger, target, 0.0);
    }

    public void open(@NotNull Player challenger, @NotNull Player target, double stake) {
        Collection<Kit> allKits = plugin.getKitManager().getAllKits();
        int size = allKits.size() > 7 ? 36 : 27;

        DuelKitGUIHolder holder = new DuelKitGUIHolder(target.getUniqueId(), target.getName(), stake);
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parse("<dark_gray>Duel <gold>" + target.getName() + "</gold> » Select Kit"));
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
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Challenge with this Kit</yellow>"));

                meta.lore(lore);
                meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.getId());
                item.setItemMeta(meta);
            }

            inv.setItem(targetSlot, item);
        }

        challenger.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DuelKitGUIHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player challenger)) {
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

            challenger.closeInventory();

            Player target = Bukkit.getPlayer(holder.getTargetUuid());
            if (target == null || !target.isOnline()) {
                TextUtil.sendPrefixed(challenger, "<red>Player is no longer online.");
                return;
            }

            challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getDuelArenaGUI().open(challenger, target, kit, holder.getStake());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DuelKitGUIHolder) {
            event.setCancelled(true);
        }
    }
}
