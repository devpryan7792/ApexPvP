package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.party.Party;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Visual Player Invitation GUI for Parties.
 * Renders online players on the server with one-click invite mechanics.
 */
public class PartyInviteGUI implements Listener {

    public static final class PartyInviteHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, UUID> slotPlayerMap = new HashMap<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public Map<Integer, UUID> getSlotPlayerMap() {
            return slotPlayerMap;
        }
    }

    private final ApexPvPPlugin plugin;

    public PartyInviteGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player player) {
        if (Bukkit.getServer() == null) return;

        Party party = plugin.getPartyManager().getParty(player);
        if (party == null || !party.isLeader(player.getUniqueId())) {
            TextUtil.sendPrefixed(player, "<red>Only the party leader can invite players.");
            return;
        }

        PartyInviteHolder holder = new PartyInviteHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.parse("<dark_gray>Party » Invite Online Players"));
        holder.setInventory(inv);

        // Fill border
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(Component.empty());
            pane.setItemMeta(pMeta);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<yellow>« Back to Party</yellow>"));
            back.setItemMeta(bMeta);
        }
        inv.setItem(49, back);

        // Populate online players not in party
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            if (target.getUniqueId().equals(player.getUniqueId())) continue;
            if (party.contains(target.getUniqueId())) continue;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sMeta = (SkullMeta) skull.getItemMeta();
            if (sMeta != null) {
                sMeta.setOwningPlayer(target);
                sMeta.displayName(TextUtil.parse("<green>" + target.getName() + "</green>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Click to send instant party invitation!</gray>"));
                sMeta.lore(lore);
                skull.setItemMeta(sMeta);
            }
            inv.setItem(slot, skull);
            holder.getSlotPlayerMap().put(slot, target.getUniqueId());
            slot++;
        }

        if (slot == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nMeta = none.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(TextUtil.parse("<red>No other eligible players online</red>"));
                none.setItemMeta(nMeta);
            }
            inv.setItem(22, none);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PartyInviteHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            plugin.getPartyGUI().open(player);
            return;
        }

        UUID targetUuid = holder.getSlotPlayerMap().get(slot);
        if (targetUuid != null) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                plugin.getPartyManager().invitePlayer(player, target);
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Throwable ignored) {}
                plugin.getPartyGUI().open(player);
            } else {
                TextUtil.sendPrefixed(player, "<red>Player is no longer online.");
                open(player);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PartyInviteHolder) {
            event.setCancelled(true);
        }
    }
}
