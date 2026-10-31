package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Visual Party Management GUI (/party).
 * Allows players to create parties, inspect member rosters, invite friends,
 * initiate party scrimmage duels, or disband/leave.
 */
public class PartyGUI implements Listener {

    public static final class PartyGUIHolder implements InventoryHolder {
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

    public PartyGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player player) {
        if (Bukkit.getServer() == null) return;

        Party party = plugin.getPartyManager().getParty(player);
        PartyGUIHolder holder = new PartyGUIHolder();

        if (party == null) {
            // Not in party menu
            Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>Party » Hub"));
            holder.setInventory(inv);

            fillBackground(inv);

            // Slot 11: Create Party
            ItemStack createItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta cMeta = createItem.getItemMeta();
            if (cMeta != null) {
                cMeta.displayName(TextUtil.parse("<green><bold>CREATE PARTY</bold></green>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Form a new party to duel friends,</gray>"));
                lore.add(TextUtil.parse("<gray>enter 2v2/3v3 team queues, or play</gray>"));
                lore.add(TextUtil.parse("<gray>internal party scrimmage matches!</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>» Click to create party</yellow>"));
                cMeta.lore(lore);
                createItem.setItemMeta(cMeta);
            }
            inv.setItem(11, createItem);

            // Slot 13: Party Info
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta iMeta = infoItem.getItemMeta();
            if (iMeta != null) {
                iMeta.displayName(TextUtil.parse("<gold><bold>Party Information</bold></gold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Parties allow groups of players to</gray>"));
                lore.add(TextUtil.parse("<gray>stick together across match queues.</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Max Members:</gray> <gold>16 players</gold>"));
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Modes:</gray> <yellow>2v2 - 4v4 Queues, Party Duels, Party Split (2v2 - 4v4)</yellow>"));
                iMeta.lore(lore);
                infoItem.setItemMeta(iMeta);
            }
            inv.setItem(13, infoItem);

            // Slot 15: Close
            ItemStack closeItem = new ItemStack(Material.BARRIER);
            ItemMeta clMeta = closeItem.getItemMeta();
            if (clMeta != null) {
                clMeta.displayName(TextUtil.parse("<red><bold>Close Menu</bold></red>"));
                closeItem.setItemMeta(clMeta);
            }
            inv.setItem(15, closeItem);

            player.openInventory(inv);
        } else {
            // In party menu
            Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>Party » Roster (" + party.getSize() + "/" + party.getMaxMembers() + ")"));
            holder.setInventory(inv);

            fillBackground(inv);

            // Slot 4: Party Info Header
            ItemStack banner = new ItemStack(Material.BEACON);
            ItemMeta bMeta = banner.getItemMeta();
            if (bMeta != null) {
                Player leader = Bukkit.getPlayer(party.getLeader());
                String leaderName = leader != null ? leader.getName() : "Unknown";
                bMeta.displayName(TextUtil.parse("<gold><bold>Party of " + leaderName + "</bold></gold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Leader:</gray> <yellow>" + leaderName + "</yellow>"));
                lore.add(TextUtil.parse("<gray>Members:</gray> <green>" + party.getSize() + "</green><gray>/" + party.getMaxMembers() + "</gray>"));
                bMeta.lore(lore);
                banner.setItemMeta(bMeta);
            }
            inv.setItem(4, banner);

            // Member slots: 10, 11, 12, 13, 14, 15, 16
            int[] memberSlots = {10, 11, 12, 13, 14, 15, 16};
            List<UUID> memberList = new ArrayList<>(party.getMembers());

            for (int i = 0; i < memberSlots.length; i++) {
                int slot = memberSlots[i];
                if (i < memberList.size()) {
                    UUID memberId = memberList.get(i);
                    Player member = Bukkit.getPlayer(memberId);
                    String name = member != null ? member.getName() : "Member";

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sMeta = (SkullMeta) skull.getItemMeta();
                    if (sMeta != null) {
                        if (member != null) {
                            sMeta.setOwningPlayer(member);
                        }
                        boolean isLeader = party.isLeader(memberId);
                        sMeta.displayName(TextUtil.parse((isLeader ? "<gold>★ <yellow>" : "<white>") + name + "</yellow>"));
                        List<Component> lore = new ArrayList<>();
                        lore.add(TextUtil.parse("<gray>Role:</gray> " + (isLeader ? "<gold><bold>Party Leader</bold></gold>" : "<gray>Member</gray>")));
                        lore.add(TextUtil.parse("<gray>Status:</gray> " + (member != null && member.isOnline() ? "<green>Online</green>" : "<red>Offline</red>")));
                        if (party.isLeader(player.getUniqueId()) && !isLeader) {
                            lore.add(Component.empty());
                            lore.add(TextUtil.parse("<red>» Click to kick from party</red>"));
                        }
                        sMeta.lore(lore);
                        skull.setItemMeta(sMeta);
                    }
                    inv.setItem(slot, skull);
                } else if (i < party.getMaxMembers()) {
                    // Empty slot
                    ItemStack empty = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                    ItemMeta eMeta = empty.getItemMeta();
                    if (eMeta != null) {
                        eMeta.displayName(TextUtil.parse("<green>Empty Party Slot</green>"));
                        List<Component> lore = new ArrayList<>();
                        lore.add(TextUtil.parse("<gray>Click 'Invite Player' below or use</gray>"));
                        lore.add(TextUtil.parse("<yellow>/party invite <player></yellow> <gray>to fill.</gray>"));
                        eMeta.lore(lore);
                        empty.setItemMeta(eMeta);
                    }
                    inv.setItem(slot, empty);
                }
            }

            // Slot 20: Party Split Scrimmage
            ItemStack duelItem = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta dMeta = duelItem.getItemMeta();
            if (dMeta != null) {
                dMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>PARTY SPLIT SCRIMMAGE (2v2 - 4v4)</bold></gradient>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Split all party members into 2 equal teams</gray>"));
                lore.add(TextUtil.parse("<gray>and battle in a private arena!</gray>"));
                lore.add(TextUtil.parse("<gray>Supports 2v2 (4p), 3v3 (6p), and 4v4 (8p).</gray>"));
                lore.add(Component.empty());
                if (party.getSize() < 2) {
                    lore.add(TextUtil.parse("<red>Requires at least 2 members.</red>"));
                } else if (!party.isLeader(player.getUniqueId())) {
                    lore.add(TextUtil.parse("<red>Only the Party Leader can start.</red>"));
                } else {
                    lore.add(TextUtil.parse("<green>» Click to start party scrimmage</green>"));
                }
                dMeta.lore(lore);
                duelItem.setItemMeta(dMeta);
            }
            inv.setItem(20, duelItem);

            // Slot 21: Party Free-For-All (FFA)
            ItemStack ffaItem = new ItemStack(Material.NETHERITE_AXE);
            ItemMeta ffaMeta = ffaItem.getItemMeta();
            if (ffaMeta != null) {
                ffaMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>PARTY FREE-FOR-ALL (FFA)</bold></gradient>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Enter an all-out deathmatch where every</gray>"));
                lore.add(TextUtil.parse("<gray>party member fights for themselves!</gray>"));
                lore.add(TextUtil.parse("<gray>Supports 2 to 16 players (last survivor wins).</gray>"));
                lore.add(Component.empty());
                if (party.getSize() < 2) {
                    lore.add(TextUtil.parse("<red>Requires at least 2 members.</red>"));
                } else if (!party.isLeader(player.getUniqueId())) {
                    lore.add(TextUtil.parse("<red>Only the Party Leader can start.</red>"));
                } else {
                    lore.add(TextUtil.parse("<green>» Click to start Party FFA</green>"));
                }
                ffaMeta.lore(lore);
                ffaItem.setItemMeta(ffaMeta);
            }
            inv.setItem(21, ffaItem);

            // Slot 22: Invite Player
            ItemStack inviteItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta inMeta = inviteItem.getItemMeta();
            if (inMeta != null) {
                inMeta.displayName(TextUtil.parse("<yellow><bold>INVITE PLAYER</bold></yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Click to browse online players and</gray>"));
                lore.add(TextUtil.parse("<gray>send instant party invitations!</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>» Click to open invite menu</yellow>"));
                inMeta.lore(lore);
                inviteItem.setItemMeta(inMeta);
            }
            inv.setItem(22, inviteItem);

            // Slot 24: Leave or Disband
            boolean isLeader = party.isLeader(player.getUniqueId());
            ItemStack actionItem = new ItemStack(isLeader ? Material.TNT : Material.OAK_DOOR);
            ItemMeta aMeta = actionItem.getItemMeta();
            if (aMeta != null) {
                aMeta.displayName(TextUtil.parse(isLeader ? "<red><bold>DISBAND PARTY</bold></red>" : "<red><bold>LEAVE PARTY</bold></red>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>" + (isLeader ? "Disband the party and remove all members." : "Leave the current party.") + "</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<red>» Click to confirm</red>"));
                aMeta.lore(lore);
                actionItem.setItemMeta(aMeta);
            }
            inv.setItem(24, actionItem);

            player.openInventory(inv);
        }
    }

    private void fillBackground(@NotNull Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PartyGUIHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        Party party = plugin.getPartyManager().getParty(player);

        if (party == null) {
            // Not in party clicks
            if (slot == 11) {
                // Create party
                player.closeInventory();
                plugin.getPartyManager().createParty(player);
                open(player);
            } else if (slot == 15) {
                player.closeInventory();
            }
        } else {
            // In party clicks
            boolean isLeader = party.isLeader(player.getUniqueId());

            if (slot == 20) {
                // Party Split Scrimmage
                if (!isLeader) {
                    TextUtil.sendPrefixed(player, "<red>Only the party leader can initiate party duels.");
                    return;
                }
                if (party.getSize() < 2) {
                    TextUtil.sendPrefixed(player, "<red>You need at least 2 party members to start a scrimmage.");
                    return;
                }
                player.closeInventory();
                Kit kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                if (kit != null) {
                    plugin.getPartyManager().splitParty(player, kit);
                }
            } else if (slot == 21) {
                // Party Free-For-All (FFA)
                if (!isLeader) {
                    TextUtil.sendPrefixed(player, "<red>Only the party leader can initiate party duels.");
                    return;
                }
                if (party.getSize() < 2) {
                    TextUtil.sendPrefixed(player, "<red>You need at least 2 party members to start an FFA deathmatch.");
                    return;
                }
                player.closeInventory();
                Kit kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                if (kit != null) {
                    plugin.getPartyManager().startPartyFfa(player, kit);
                }
            } else if (slot == 22) {
                // Open invite menu
                plugin.getPartyInviteGUI().open(player);
            } else if (slot == 24) {
                // Leave / Disband
                player.closeInventory();
                if (isLeader) {
                    plugin.getPartyManager().disbandParty(player);
                } else {
                    plugin.getPartyManager().leaveParty(player);
                }
            } else if (slot >= 10 && slot <= 16 && isLeader) {
                // Clicking a member head to kick
                int index = slot - 10;
                List<UUID> memberList = new ArrayList<>(party.getMembers());
                if (index < memberList.size()) {
                    UUID targetId = memberList.get(index);
                    if (!party.isLeader(targetId)) {
                        Player target = Bukkit.getPlayer(targetId);
                        party.removeMember(targetId);
                        plugin.getPartyManager().unregisterPlayerFromParty(targetId);
                        party.broadcast("<yellow>" + (target != null ? target.getName() : "A member") + " was kicked from the party by the leader.</yellow>");
                        if (target != null && target.isOnline()) {
                            TextUtil.sendPrefixed(target, "<red>You were removed from the party by the leader.");
                        }
                        open(player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PartyGUIHolder) {
            event.setCancelled(true);
        }
    }
}
