package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.party.Party;
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

/**
 * Interactive chest GUI allowing party leaders to visually select which combat kit
 * to use when launching a Party Split scrimmage or Party Free-For-All deathmatch.
 */
public class PartyKitSelectGUI implements Listener {

    public enum PartyMatchType {
        SPLIT("Party Split", "team scrimmage"),
        FFA("Party FFA", "all-out free-for-all deathmatch");

        private final String displayName;
        private final String description;

        PartyMatchType(@NotNull String displayName, @NotNull String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class PartyKitSelectHolder implements InventoryHolder {
        private final PartyMatchType matchType;
        private Inventory inventory;

        public PartyKitSelectHolder(@NotNull PartyMatchType matchType) {
            this.matchType = Objects.requireNonNull(matchType, "matchType cannot be null");
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        public PartyMatchType getMatchType() {
            return matchType;
        }
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey kitKey;

    public PartyKitSelectGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.kitKey = new NamespacedKey(plugin, "party_kit_id");
    }

    public void open(@NotNull Player leader, @NotNull PartyMatchType matchType) {
        Party party = plugin.getPartyManager().getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can configure match settings.");
            return;
        }

        Collection<Kit> allKits = plugin.getKitManager().getAllKits();
        int size = allKits.size() > 7 ? 36 : 27;

        PartyKitSelectHolder holder = new PartyKitSelectHolder(matchType);
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parse("<dark_gray>" + matchType.getDisplayName() + " » Select Kit"));
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

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

                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Choose Kit & Proceed to Map Selection</yellow>"));

                meta.lore(lore);
                meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.getId());
                item.setItemMeta(meta);
            }

            inv.setItem(targetSlot, item);
        }

        leader.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PartyKitSelectHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player leader)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
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

        leader.closeInventory();
        try {
            leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } catch (Throwable ignored) {}

        // Open Step 2: Map / Arena Selection
        plugin.getPartyArenaSelectGUI().open(leader, holder.getMatchType(), kit);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PartyKitSelectHolder) {
            event.setCancelled(true);
        }
    }
}
