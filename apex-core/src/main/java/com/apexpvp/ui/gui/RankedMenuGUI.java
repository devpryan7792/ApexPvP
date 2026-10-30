package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.party.Party;
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
 * Competitive Matchmaking Hub GUI (/ranked or /rmatch).
 * Allows players to view competitive status and enter Solo (1v1) or Team (2v2) Ranked matchmaking.
 */
public class RankedMenuGUI implements Listener {

    public static final class RankedMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static final class RankedKitSelectHolder implements InventoryHolder {
        private final boolean isTeamRanked;
        private Inventory inventory;

        public RankedKitSelectHolder(boolean isTeamRanked) {
            this.isTeamRanked = isTeamRanked;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public boolean isTeamRanked() {
            return isTeamRanked;
        }
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey kitKey;

    public RankedMenuGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.kitKey = new NamespacedKey(plugin, "ranked_kit_id");
    }

    public void open(@NotNull Player player) {
        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) {
            TextUtil.sendPrefixed(player, "<red>Profile data is still loading. Please try again shortly.");
            return;
        }

        RankedMenuHolder holder = new RankedMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>Competitive Ranked Matchmaking"));
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

        // Slot 4: Player Profile Card
        ItemStack profileCard = new ItemStack(Material.NETHER_STAR);
        ItemMeta pMeta = profileCard.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Competitive Profile</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Rank Tier: " + profile.getRank().getMiniMessageDisplay()));
            lore.add(TextUtil.parse("<gray>Rating: <yellow>" + profile.getElo() + " ELO</yellow>"));
            lore.add(TextUtil.parse("<gray>Win Streak: <green>" + profile.getCurrentStreak() + " wins</green>"));
            int totalWins = profile.getAllStats().values().stream().mapToInt(com.apexpvp.profile.PlayerStats::getWins).sum();
            int totalLosses = profile.getAllStats().values().stream().mapToInt(com.apexpvp.profile.PlayerStats::getLosses).sum();
            int totalMatches = totalWins + totalLosses;
            double winRate = (totalMatches > 0) ? (totalWins * 100.0 / totalMatches) : 0.0;
            lore.add(TextUtil.parse("<gray>Record: <green>" + totalWins + "W</green> <gray>/</gray> <red>" + totalLosses + "L</red> <white>(" + String.format("%.1f%%", winRate) + ")</white>"));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            pMeta.lore(lore);
            profileCard.setItemMeta(pMeta);
        }
        inv.setItem(4, profileCard);

        // Slot 11: Solo Ranked (1v1)
        ItemStack soloBtn = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta soloMeta = soloBtn.getItemMeta();
        if (soloMeta != null) {
            soloMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Solo Ranked (1v1)</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Duel solo gladiators matched by</gray>"));
            lore.add(TextUtil.parse("<gray>dynamic ELO matchmaking.</gray>"));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<gray>Rating Effect: <green>+ELO / -ELO</green>"));
            lore.add(TextUtil.parse("<gray>Queue Pool: <aqua>1v1 Competitive</aqua>"));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Select Kit & Enter Solo Queue</yellow>"));
            soloMeta.lore(lore);
            soloBtn.setItemMeta(soloMeta);
        }
        inv.setItem(11, soloBtn);

        // Slot 15: Team Ranked (2v2)
        ItemStack teamBtn = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta teamMeta = teamBtn.getItemMeta();
        if (teamMeta != null) {
            teamMeta.displayName(TextUtil.parse("<gradient:#00FF88:#00AAFF><bold>Team Ranked (2v2)</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Enter competitive 2v2 party combat.</gray>"));
            lore.add(TextUtil.parse("<gray>Coordinate with your teammate to win!</gray>"));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<gray>Requires: <yellow>Party of 2+ players</yellow>"));
            lore.add(TextUtil.parse("<gray>Rating Effect: <green>Team Average ELO</green>"));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Select Kit & Enter Team Match</yellow>"));
            teamMeta.lore(lore);
            teamBtn.setItemMeta(teamMeta);
        }
        inv.setItem(15, teamBtn);

        // Slot 22: Leave Queue (if currently queued)
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            ItemStack leaveBtn = new ItemStack(Material.BARRIER);
            ItemMeta leaveMeta = leaveBtn.getItemMeta();
            if (leaveMeta != null) {
                leaveMeta.displayName(TextUtil.parse("<red><bold>Leave Queue</bold></red>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>You are currently waiting in a matchmaking queue.</gray>"));
                lore.add(TextUtil.parse("<red><bold>CLICK</bold></red> <gray>» Cancel search and refund entry fee</gray>"));
                leaveMeta.lore(lore);
                leaveBtn.setItemMeta(leaveMeta);
            }
            inv.setItem(22, leaveBtn);
        }

        player.openInventory(inv);
    }

    public void openKitSelector(@NotNull Player player, boolean isTeamRanked) {
        Collection<Kit> allKits = plugin.getKitManager().getAllKits();
        int size = allKits.size() > 7 ? 36 : 27;

        RankedKitSelectHolder holder = new RankedKitSelectHolder(isTeamRanked);
        String title = isTeamRanked ? "<dark_gray>Team Ranked » Select Kit" : "<dark_gray>Solo Ranked » Select Kit";
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parse(title));
        holder.setInventory(inv);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        int[] displaySlots = (allKits.size() <= 7)
                ? new int[]{10, 11, 12, 13, 14, 15, 16}
                : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

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
                lore.add(TextUtil.parse("<gray>Mode: <yellow>" + (kit.getRules().sumoMode() ? "Sumo" : (kit.getRules().comboMode() ? "Combo" : "Standard")) + "</yellow>"));
                lore.add(TextUtil.parse("<gray>Building: " + (kit.getRules().allowBuild() ? "<green>Yes</green>" : "<red>No</red>")));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Queue for this Kit</yellow>"));
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
        if (event.getInventory().getHolder() instanceof RankedMenuHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int slot = event.getRawSlot();
            if (slot == 11) { // Solo Ranked
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openKitSelector(player, false);
            } else if (slot == 15) { // Team Ranked
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                Party party = plugin.getPartyManager().getParty(player);
                if (party == null) {
                    player.closeInventory();
                    TextUtil.sendPrefixed(player, "<red>You need to be in a party to play Team Ranked! Create one with <yellow>/party create</yellow>.");
                    return;
                }
                if (!party.isLeader(player.getUniqueId())) {
                    player.closeInventory();
                    TextUtil.sendPrefixed(player, "<red>Only the party leader can queue for Team Ranked!");
                    return;
                }
                if (party.getSize() < 2) {
                    player.closeInventory();
                    TextUtil.sendPrefixed(player, "<red>You need at least 2 party members to play Team Ranked!");
                    return;
                }
                openKitSelector(player, true);
            } else if (slot == 22) { // Leave Queue
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                plugin.getQueueManager().leaveQueue(player);
            }
        } else if (event.getInventory().getHolder() instanceof RankedKitSelectHolder kitHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;

            String kitId = meta.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
            if (kitId == null) return;

            Kit kit = plugin.getKitManager().getKit(kitId);
            if (kit == null) return;

            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

            if (kitHolder.isTeamRanked()) {
                plugin.getPartyManager().startRankedPartySplit(player, kit);
            } else {
                plugin.getQueueManager().queuePlayer(player, kit);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RankedMenuHolder ||
            event.getInventory().getHolder() instanceof RankedKitSelectHolder) {
            event.setCancelled(true);
        }
    }
}
