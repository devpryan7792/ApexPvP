package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.RankedTeamMatch;
import com.apexpvp.match.types.TeamMatch;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages party creation, member invitations, party disbanding, and party scrimmages.
 */
public class PartyManager {

    private final ApexPvPPlugin plugin;
    private final int maxPartyMembers;

    private final Map<UUID, Party> partiesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>(); // targetUuid -> leaderUuid

    public PartyManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.maxPartyMembers = plugin.getConfig().getInt("party.max-members", 16);
    }

    @Nullable
    public Party getParty(@NotNull Player player) {
        return partiesByPlayer.get(player.getUniqueId());
    }

    @Nullable
    public Party getParty(@NotNull UUID uuid) {
        return partiesByPlayer.get(uuid);
    }

    public boolean createParty(@NotNull Player leader) {
        if (partiesByPlayer.containsKey(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>You are already in a party!");
            return false;
        }

        Party party = new Party(leader, maxPartyMembers);
        partiesByPlayer.put(leader.getUniqueId(), party);
        TextUtil.sendPrefixed(leader, "<green>Party created successfully! Invite players using <yellow>/party invite <player></yellow>.");
        return true;
    }

    public boolean invitePlayer(@NotNull Player leader, @NotNull Player target) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can invite players.");
            return false;
        }

        if (partiesByPlayer.containsKey(target.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red><target> is already in a party.", Placeholder.parsed("target", target.getName()));
            return false;
        }

        pendingInvites.put(target.getUniqueId(), leader.getUniqueId());
        TextUtil.sendPrefixed(leader, "<green>Invited <gold><target></gold> to your party.", Placeholder.parsed("target", target.getName()));

        TextUtil.sendPrefixed(target, "<gradient:#FFAA00:#FF5555><bold>PARTY INVITE!</bold></gradient> <gold>" + leader.getName() +
                "</gold> invited you to join their party!\n" +
                "<click:run_command:'/party accept " + leader.getName() + "'><hover:show_text:'<green>Click to accept party invite'><green><bold>[ACCEPT]</bold></green></hover></click>");
        return true;
    }

    public boolean acceptInvite(@NotNull Player player, @NotNull Player leader) {
        UUID expectedLeader = pendingInvites.get(player.getUniqueId());
        if (expectedLeader == null || !expectedLeader.equals(leader.getUniqueId())) {
            TextUtil.sendPrefixed(player, "<red>You do not have a pending party invite from <gold><leader></gold>.", Placeholder.parsed("leader", leader.getName()));
            return false;
        }

        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null) {
            TextUtil.sendPrefixed(player, "<red>That party no longer exists.");
            pendingInvites.remove(player.getUniqueId());
            return false;
        }

        if (party.addMember(player.getUniqueId())) {
            pendingInvites.remove(player.getUniqueId());
            partiesByPlayer.put(player.getUniqueId(), party);
            party.broadcast("<gold>" + player.getName() + "</gold> <green>joined the party!");
            return true;
        } else {
            TextUtil.sendPrefixed(player, "<red>That party is currently full.");
            return false;
        }
    }

    public boolean leaveParty(@NotNull Player player) {
        Party party = partiesByPlayer.remove(player.getUniqueId());
        if (party == null) {
            TextUtil.sendPrefixed(player, "<red>You are not in a party.");
            return false;
        }

        party.removeMember(player.getUniqueId());
        TextUtil.sendPrefixed(player, "<yellow>You left the party.");

        if (party.isLeader(player.getUniqueId())) {
            if (party.getSize() > 0) {
                // Pass leadership to next member
                UUID nextLeader = party.getMembers().iterator().next();
                party.setLeader(nextLeader);
                Player newL = Bukkit.getPlayer(nextLeader);
                if (newL != null) {
                    party.broadcast("<gold>" + newL.getName() + "</gold> is now the party leader.");
                }
            }
        } else {
            party.broadcast("<gold>" + player.getName() + "</gold> left the party.");
        }
        return true;
    }

    public void unregisterPlayerFromParty(@NotNull UUID uuid) {
        partiesByPlayer.remove(uuid);
    }

    public boolean disbandParty(@NotNull Player leader) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can disband the party.");
            return false;
        }

        party.broadcast("<red>The party has been disbanded by the leader.");
        for (UUID member : party.getMembers()) {
            partiesByPlayer.remove(member);
        }
        return true;
    }

    /**
     * Splits party roster into two balanced teams and launches an internal scrimmage TeamMatch.
     */
    public boolean splitParty(@NotNull Player leader, @NotNull Kit kit) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can start a Party Split.");
            return false;
        }

        List<Player> onlineMembers = new ArrayList<>();
        for (UUID memberUuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                if (plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " is already in a match!");
                    return false;
                }
                onlineMembers.add(p);
            }
        }

        if (onlineMembers.size() < 2) {
            TextUtil.sendPrefixed(leader, "<red>You need at least 2 online party members to start a split!");
            return false;
        }

        ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
        if (template == null) {
            TextUtil.sendPrefixed(leader, "<red>No arenas are configured.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(leader, "<yellow>No arenas are currently available. Auto-scaling instance, please try in a moment...");
            plugin.getArenaManager().requestScaling(template.getId());
            return false;
        }

        Collections.shuffle(onlineMembers);

        List<Player> teamRed = new ArrayList<>();
        List<Player> teamBlue = new ArrayList<>();

        for (int i = 0; i < onlineMembers.size(); i++) {
            if (i % 2 == 0) teamRed.add(onlineMembers.get(i));
            else teamBlue.add(onlineMembers.get(i));
        }

        TeamMatch match = new TeamMatch(plugin, arenaInstance, kit, teamRed, teamBlue, "Team Red", "Team Blue");
        plugin.getMatchManager().registerMatch(match);
        match.start();

        party.broadcast("<gradient:#00FFAA:#00AAFF><bold>PARTY SPLIT STARTED!</bold></gradient> Red Team vs Blue Team!");
        return true;
    }

    public boolean startRankedPartySplit(@NotNull Player leader, @NotNull Kit kit) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can start a Ranked Party Match.");
            return false;
        }

        List<Player> onlineMembers = new ArrayList<>();
        for (UUID memberUuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                if (plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " is already in a match!");
                    return false;
                }
                onlineMembers.add(p);
            }
        }

        if (onlineMembers.size() < 2) {
            TextUtil.sendPrefixed(leader, "<red>You need at least 2 online party members for Team Ranked!");
            return false;
        }

        ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
        if (template == null) {
            TextUtil.sendPrefixed(leader, "<red>No arenas are configured.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(leader, "<yellow>No arenas are currently available. Auto-scaling instance, please try in a moment...");
            plugin.getArenaManager().requestScaling(template.getId());
            return false;
        }

        Collections.shuffle(onlineMembers);

        List<Player> teamRed = new ArrayList<>();
        List<Player> teamBlue = new ArrayList<>();

        for (int i = 0; i < onlineMembers.size(); i++) {
            if (i % 2 == 0) teamRed.add(onlineMembers.get(i));
            else teamBlue.add(onlineMembers.get(i));
        }

        RankedTeamMatch match = new RankedTeamMatch(plugin, arenaInstance, kit, teamRed, teamBlue, "Team Red", "Team Blue");
        plugin.getMatchManager().registerMatch(match);
        match.start();

        party.broadcast("<gradient:#00FFAA:#00AAFF><bold>RANKED TEAM MATCH STARTED!</bold></gradient> Competitive ELO is on the line!");
        return true;
    }
}
