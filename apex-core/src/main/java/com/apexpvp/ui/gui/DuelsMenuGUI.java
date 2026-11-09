package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.party.Party;
import com.apexpvp.profile.PlayerProfile;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main Player Dueling Hub GUI (/duels).
 * Provides one-click access to matchmaking (Ranked/Unranked), custom duels,
 * kit layout editing, leaderboards, stats, and duel request privacy controls.
 */
public class DuelsMenuGUI implements Listener {

    public static final class DuelsMenuHolder implements InventoryHolder {
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

    public DuelsMenuGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) {
            TextUtil.sendPrefixed(player, "<red>Profile data is loading. Please try again in a moment.");
            return;
        }

        if (Bukkit.getServer() == null) {
            return;
        }

        DuelsMenuHolder holder = new DuelsMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>ApexPvP » Dueling Hub"));
        holder.setInventory(inv);

        // Fill background with dark gray panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 10: Free-For-All (FFA) Deathmatch
        ItemStack ffaItem = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta ffaMeta = ffaItem.getItemMeta();
        if (ffaMeta != null) {
            ffaMeta.displayName(TextUtil.parse("<gradient:#FF5555:#FFAA00><bold>Free-For-All (FFA)</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>All-out deathmatch with no teams!</gray>"));
            lore.add(TextUtil.parse("<gray>Every fighter is your enemy.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Join 4-Player FFA Queue</white>"));
            lore.add(TextUtil.parse("<gold>▶ Right-Click:</gold> <white>Party FFA Scrimmage (2-16p)</white>"));
            ffaMeta.lore(lore);
            ffaItem.setItemMeta(ffaMeta);
        }
        inv.setItem(10, ffaItem);

        // Slot 11: Play Matchmaking (Netherite Sword)
        ItemStack matchmakingItem = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta mmMeta = matchmakingItem.getItemMeta();
        if (mmMeta != null) {
            mmMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Play Matchmaking</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Enter the arena to battle other players.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Unranked Quick Match</white>"));
            lore.add(TextUtil.parse("<gold>▶ Right-Click:</gold> <white>Ranked Competitive League</white>"));
            mmMeta.lore(lore);
            matchmakingItem.setItemMeta(mmMeta);
        }
        inv.setItem(11, matchmakingItem);

        // Slot 12: Custom Games & Private Duels (Diamond Sword)
        ItemStack customItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta cMeta = customItem.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Custom Games & Duels</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Challenge friends, pick custom arenas,</gray>"));
            lore.add(TextUtil.parse("<gray>and wager coins or items in escrow.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Command:</yellow> <gold>/duel <player></gold>"));
            lore.add(TextUtil.parse("<yellow>▶ Click to see how to duel</yellow>"));
            cMeta.lore(lore);
            customItem.setItemMeta(cMeta);
        }
        inv.setItem(12, customItem);

        // Slot 13: Kit Layout Editor (Anvil)
        ItemStack editorItem = new ItemStack(Material.ANVIL);
        ItemMeta edMeta = editorItem.getItemMeta();
        if (edMeta != null) {
            edMeta.displayName(TextUtil.parse("<gradient:#AA00FF:#FF55FF><bold>Kit Layout Editor</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Rearrange hotbar and inventory items</gray>"));
            lore.add(TextUtil.parse("<gray>to your exact personal combat layout.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to customize your kit layouts</yellow>"));
            edMeta.lore(lore);
            editorItem.setItemMeta(edMeta);
        }
        inv.setItem(13, editorItem);

        // Slot 14: Leaderboards (Gold Block)
        ItemStack topItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta topMeta = topItem.getItemMeta();
        if (topMeta != null) {
            topMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FFFF55><bold>Competitive Leaderboards</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>View top players across ELO ratings,</gray>"));
            lore.add(TextUtil.parse("<gray>total wins, and active win streaks.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to view Top 10 rankings</yellow>"));
            topMeta.lore(lore);
            topItem.setItemMeta(topMeta);
        }
        inv.setItem(14, topItem);

        // Slot 15: Personal Stats Profile (Player Head)
        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sMeta = (SkullMeta) headItem.getItemMeta();
        if (sMeta != null) {
            sMeta.setOwningPlayer(player);
            sMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>" + player.getName() + "'s Profile</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Rank Tier: " + profile.getRank().getMiniMessageDisplay()));
            lore.add(TextUtil.parse("<gray>ELO Rating: <gold>" + profile.getElo() + "</gold>"));
            lore.add(TextUtil.parse("<gray>Win Streak: <green>" + profile.getCurrentStreak() + "</green> <dark_gray>(Best: " + profile.getBestStreak() + ")</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Loss Streak: <red>" + profile.getCurrentLossStreak() + "</red>"));
            lore.add(TextUtil.parse("<gray>Coins: <yellow>" + String.format("%.2f", profile.getCoins()) + "</yellow>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to view full statistics card</yellow>"));
            sMeta.lore(lore);
            headItem.setItemMeta(sMeta);
        }
        inv.setItem(15, headItem);

        // Slot 22: Duel Request Privacy Settings (Lime Dye if enabled, Red Dye if muted)
        boolean allowed = profile.isAllowDuelRequests();
        ItemStack privacyItem = new ItemStack(allowed ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta pMeta = privacyItem.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse(allowed
                    ? "<green><bold>Duel Requests: ACCEPTING</bold></green>"
                    : "<red><bold>Duel Requests: MUTED</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Toggle whether other players can send</gray>"));
            lore.add(TextUtil.parse("<gray>you direct 1v1 duel challenge requests.</gray>"));
            lore.add(TextUtil.parse("<dark_gray>(Ideal for survival/SMP players)</dark_gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse(allowed
                    ? "<yellow>▶ Click to <red>MUTE</red> duel requests</yellow>"
                    : "<yellow>▶ Click to <green>ENABLE</green> duel requests</yellow>"));
            pMeta.lore(lore);
            privacyItem.setItemMeta(pMeta);
        }
        inv.setItem(22, privacyItem);

        // Slot 26: Team Hub (Beacon)
        ItemStack partyItem = new ItemStack(Material.BEACON);
        ItemMeta partyMeta = partyItem.getItemMeta();
        if (partyMeta != null) {
            partyMeta.displayName(TextUtil.parse("<gradient:#55FFFF:#00AAAA><bold>Team Management</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Create or manage your competitive team.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to open Team Menu</yellow>"));
            partyMeta.lore(lore);
            partyItem.setItemMeta(partyMeta);
        }
        inv.setItem(26, partyItem);

        player.openInventory(inv);
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DuelsMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) return;

        switch (slot) {
            case 10 -> {
                // Free-For-All (FFA)
                player.closeInventory();
                if (event.getClick() == ClickType.RIGHT) {
                    Party party = plugin.getPartyManager().getParty(player);
                    if (party != null && party.isLeader(player.getUniqueId()) && party.getSize() >= 2) {
                        Kit kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                        if (kit != null) {
                            plugin.getPartyManager().startPartyFfa(player, kit);
                            return;
                        }
                    }
                    plugin.getPartyGUI().open(player);
                } else {
                    // Left-click: queue for default kit FFA or dispatch /ffa
                    Kit kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                    if (kit != null) {
                        plugin.getQueueManager().queueFfaPlayer(player, kit);
                    } else {
                        plugin.getServer().dispatchCommand(player, "ffa");
                    }
                }
            }
            case 11 -> {
                // Play Matchmaking
                if (event.getClick() == ClickType.RIGHT) {
                    plugin.getRankedMenuGUI().open(player);
                } else {
                    // Left click: Open Ranked/Unranked selection or kits
                    plugin.getRankedMenuGUI().open(player);
                }
            }
            case 12 -> {
                // Custom Games info
                player.closeInventory();
                TextUtil.sendPrefixed(player, "<gradient:#FFAA00:#FF5555><bold>Custom Duels & Wagers</bold></gradient>\n" +
                        "<yellow>/duel <player></yellow> <gray>- Challenge a player & pick kit/arena in GUI</gray>\n" +
                        "<yellow>/duel <player> <kit> [stake]</yellow> <gray>- Quick challenge with coin wager</gray>\n" +
                        "<yellow>/duel preview <player></yellow> <gray>- Preview pending challenge gear & map</gray>");
            }
            case 13 -> {
                // Kit Layout Editor
                player.closeInventory();
                plugin.getKitGUI().open(player, true);
            }
            case 14 -> {
                // Leaderboards
                player.closeInventory();
                plugin.getServer().dispatchCommand(player, "top");
            }
            case 15 -> {
                // Personal Stats
                player.closeInventory();
                plugin.getServer().dispatchCommand(player, "stats");
            }
            case 22 -> {
                // Toggle Duel Privacy
                boolean current = profile.isAllowDuelRequests();
                profile.setAllowDuelRequests(!current);
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Throwable ignored) {}
                TextUtil.sendPrefixed(player, !current
                        ? "<green>Incoming duel requests are now <bold>ENABLED</bold>."
                        : "<yellow>Incoming duel requests are now <bold>MUTED</bold>.");
                open(player); // Re-render updated state
            }
            case 26 -> {
                // Party Management
                player.closeInventory();
                plugin.getServer().dispatchCommand(player, "party");
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DuelsMenuHolder) {
            event.setCancelled(true);
        }
    }
}
