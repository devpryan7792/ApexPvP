package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.FfaMatch;
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

    public record PartyChallenge(@NotNull UUID senderLeader,
                                 @NotNull UUID targetLeader,
                                 @NotNull Kit kit,
                                 int teamSize,
                                 long timestamp) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60_000L;
        }
    }

    private final Map<UUID, Party> partiesByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>(); // targetUuid -> leaderUuid
    private final Map<UUID, PartyChallenge> pendingPartyChallenges = new ConcurrentHashMap<>(); // targetLeaderUuid -> challenge

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
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            plugin.getQueueManager().leaveQueue(player);
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

        if (plugin.getQueueManager().isInQueue(leader.getUniqueId())) {
            plugin.getQueueManager().leaveQueue(leader);
        }

        party.broadcast("<red>The party has been disbanded by the leader.");
        for (UUID member : party.getMembers()) {
            partiesByPlayer.remove(member);
        }
        return true;
    }

    /**
     * Splits party roster into two balanced teams (1v1, 2v2, 3v3, or 4v4) and launches an internal scrimmage.
     */
    public boolean splitParty(@NotNull Player leader, @NotNull Kit kit) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can start a Party Split.");
            return false;
        }

        List<Player> onlineMembers = new ArrayList<>();
        if (Bukkit.getServer() != null) {
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
        } else {
            for (UUID memberUuid : party.getMembers()) {
                onlineMembers.add(leader);
            }
        }

        if (onlineMembers.size() < 2) {
            TextUtil.sendPrefixed(leader, "<red>You need at least 2 online party members to start a split!");
            return false;
        }

        if (onlineMembers.size() > 8) {
            TextUtil.sendPrefixed(leader, "<red>Party split supports up to 8 members (max 4v4 scrimmage). Your party has " + onlineMembers.size() + " members.");
            return false;
        }

        if (onlineMembers.size() % 2 != 0) {
            TextUtil.sendPrefixed(leader, "<red>Party split requires an even number of members (2 for 1v1, 4 for 2v2, 6 for 3v3, 8 for 4v4). Your party currently has " + onlineMembers.size() + " members.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kit.getId());
        if (arenaInstance == null) {
            ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
            if (template != null) {
                arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
            }
        }
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(leader, "<yellow>No arenas are currently available for this kit. Auto-scaling instance, please try in a moment...");
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

        int teamSize = onlineMembers.size() / 2;
        String modeTag = teamSize + "v" + teamSize;
        party.broadcast("<gradient:#00FFAA:#00AAFF><bold>" + modeTag + " PARTY SPLIT STARTED!</bold></gradient> Red Team vs Blue Team!");
        return true;
    }

    public boolean startPartyFfa(@NotNull Player leader, @NotNull Kit kit) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can start a Party Free-For-All!");
            return false;
        }

        List<Player> onlineMembers = new ArrayList<>();
        if (Bukkit.getServer() != null) {
            for (UUID memberUuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(memberUuid);
                if (p != null && p.isOnline()) {
                    if (plugin.getMatchManager().isInMatch(p)) {
                        TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " is already in an active match!");
                        return false;
                    }
                    onlineMembers.add(p);
                }
            }
        } else {
            for (UUID memberUuid : party.getMembers()) {
                onlineMembers.add(leader);
            }
        }

        if (onlineMembers.size() < 2) {
            TextUtil.sendPrefixed(leader, "<red>You need at least 2 online party members to start an FFA deathmatch!");
            return false;
        }

        if (onlineMembers.size() > 16) {
            TextUtil.sendPrefixed(leader, "<red>Party FFA supports up to 16 players. Your party has " + onlineMembers.size() + " members.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kit.getId());
        if (arenaInstance == null) {
            ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
            if (template != null) {
                arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
            }
        }
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(leader, "<yellow>No arenas are currently available for this kit. Auto-scaling instance, please try in a moment...");
            return false;
        }

        Collections.shuffle(onlineMembers);

        FfaMatch match = new FfaMatch(plugin, arenaInstance, kit, onlineMembers);
        plugin.getMatchManager().registerMatch(match);
        match.start();

        party.broadcast("<gradient:#00FFAA:#00AAFF><bold>PARTY FREE-FOR-ALL STARTED!</bold></gradient> <gold>" + onlineMembers.size() + " players</gold> in all-out combat! Last player standing wins!");
        return true;
    }

    public boolean startRankedPartySplit(@NotNull Player leader, @NotNull Kit kit) {
        Party party = partiesByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can start a Ranked Party Match.");
            return false;
        }

        List<Player> onlineMembers = new ArrayList<>();
        if (Bukkit.getServer() != null) {
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
        } else {
            for (UUID memberUuid : party.getMembers()) {
                onlineMembers.add(leader);
            }
        }

        if (onlineMembers.size() < 2) {
            TextUtil.sendPrefixed(leader, "<red>You need at least 2 online party members for Team Ranked!");
            return false;
        }

        if (onlineMembers.size() > 8) {
            TextUtil.sendPrefixed(leader, "<red>Ranked party split supports up to 8 members (max 4v4). Your party has " + onlineMembers.size() + " members.");
            return false;
        }

        if (onlineMembers.size() % 2 != 0) {
            TextUtil.sendPrefixed(leader, "<red>Ranked party split requires an even number of members (2 for 1v1, 4 for 2v2, 6 for 3v3, 8 for 4v4). Your party currently has " + onlineMembers.size() + " members.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kit.getId());
        if (arenaInstance == null) {
            ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
            if (template != null) {
                arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
            }
        }
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(leader, "<yellow>No arenas are currently available for this kit. Auto-scaling instance, please try in a moment...");
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

        int teamSize = onlineMembers.size() / 2;
        String modeTag = teamSize + "v" + teamSize;
        party.broadcast("<gradient:#00FFAA:#00AAFF><bold>" + modeTag + " RANKED TEAM MATCH STARTED!</bold></gradient> Competitive ELO is on the line!");
        return true;
    }

    public boolean challengeParty(@NotNull Player senderLeader, @NotNull Player targetLeader, @NotNull Kit kit) {
        Party senderParty = partiesByPlayer.get(senderLeader.getUniqueId());
        if (senderParty == null || !senderParty.isLeader(senderLeader.getUniqueId())) {
            TextUtil.sendPrefixed(senderLeader, "<red>Only the party leader can initiate party duels.");
            return false;
        }

        Party targetParty = partiesByPlayer.get(targetLeader.getUniqueId());
        if (targetParty == null || !targetParty.isLeader(targetLeader.getUniqueId())) {
            TextUtil.sendPrefixed(senderLeader, "<red>" + targetLeader.getName() + " is not leading an active party.");
            return false;
        }

        if (senderLeader.getUniqueId().equals(targetLeader.getUniqueId())) {
            TextUtil.sendPrefixed(senderLeader, "<red>You cannot duel your own party! Use <yellow>/party split</yellow> to scrimmage.");
            return false;
        }

        int senderSize = senderParty.getSize();
        int targetSize = targetParty.getSize();

        if (senderSize < 2 || senderSize > 4) {
            TextUtil.sendPrefixed(senderLeader, "<red>Party duels support teams of 2 to 4 players (min 2v2, max 4v4)! Your party has " + senderSize + " members.");
            return false;
        }

        if (targetSize < 2 || targetSize > 4) {
            TextUtil.sendPrefixed(senderLeader, "<red>Target party must have between 2 and 4 players (min 2v2, max 4v4)! They have " + targetSize + " members.");
            return false;
        }

        if (senderSize != targetSize) {
            TextUtil.sendPrefixed(senderLeader, "<red>Team size mismatch! Your party has " + senderSize + " members, but Team " + targetLeader.getName() + " has " + targetSize + " members. Both parties must have the same size (2v2, 3v3, or 4v4)!");
            return false;
        }

        if (Bukkit.getServer() != null) {
            for (UUID u : senderParty.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline() || plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(senderLeader, "<red>All members of your party must be online and out of combat.");
                    return false;
                }
            }
            for (UUID u : targetParty.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline() || plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(senderLeader, "<red>All members of target party must be online and out of combat.");
                    return false;
                }
            }
        }

        pendingPartyChallenges.put(targetLeader.getUniqueId(), new PartyChallenge(senderLeader.getUniqueId(), targetLeader.getUniqueId(), kit, senderSize, System.currentTimeMillis()));

        String modeTag = senderSize + "v" + senderSize;
        TextUtil.sendPrefixed(senderLeader, "<green>Sent a <gold>" + modeTag + " " + kit.getDisplayName() + "</gold> duel challenge to Team <yellow>" + targetLeader.getName() + "</yellow>!");

        TextUtil.sendPrefixed(targetLeader, "<gradient:#FFAA00:#FF5555><bold>PARTY DUEL CHALLENGE!</bold></gradient> Team <gold>" + senderLeader.getName() +
                "</gold> challenged your party to a <yellow>" + modeTag + " " + kit.getDisplayName() + "</yellow> duel!\n" +
                "<click:run_command:'/party accept-duel " + senderLeader.getName() + "'><hover:show_text:'<green>Click to accept party duel'><green><bold>[ACCEPT DUEL]</bold></green></hover></click> " +
                "<click:run_command:'/party decline-duel " + senderLeader.getName() + "'><hover:show_text:'<red>Click to decline party duel'><red><bold>[DECLINE]</bold></red></hover></click>");

        return true;
    }

    public boolean acceptPartyDuel(@NotNull Player targetLeader, @NotNull Player senderLeader) {
        PartyChallenge challenge = pendingPartyChallenges.get(targetLeader.getUniqueId());
        if (challenge == null || !challenge.senderLeader().equals(senderLeader.getUniqueId())) {
            TextUtil.sendPrefixed(targetLeader, "<red>You do not have a pending party duel challenge from Team " + senderLeader.getName() + ".");
            return false;
        }

        if (challenge.isExpired()) {
            pendingPartyChallenges.remove(targetLeader.getUniqueId());
            TextUtil.sendPrefixed(targetLeader, "<red>The party duel challenge has expired.");
            return false;
        }

        Party senderParty = partiesByPlayer.get(senderLeader.getUniqueId());
        Party targetParty = partiesByPlayer.get(targetLeader.getUniqueId());

        if (senderParty == null || targetParty == null) {
            pendingPartyChallenges.remove(targetLeader.getUniqueId());
            TextUtil.sendPrefixed(targetLeader, "<red>One of the parties no longer exists.");
            return false;
        }

        int size = challenge.teamSize();
        if (senderParty.getSize() != size || targetParty.getSize() != size) {
            pendingPartyChallenges.remove(targetLeader.getUniqueId());
            TextUtil.sendPrefixed(targetLeader, "<red>Party rosters have changed since the challenge was sent. Both parties must have " + size + " members.");
            return false;
        }

        List<Player> team0 = new ArrayList<>();
        List<Player> team1 = new ArrayList<>();

        if (Bukkit.getServer() != null) {
            for (UUID u : senderParty.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline() || plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(targetLeader, "<red>Not all members of Team " + senderLeader.getName() + " are available.");
                    return false;
                }
                team0.add(p);
            }

            for (UUID u : targetParty.getMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline() || plugin.getMatchManager().isInMatch(p)) {
                    TextUtil.sendPrefixed(targetLeader, "<red>Not all members of your party are available.");
                    return false;
                }
                team1.add(p);
            }
        } else {
            for (UUID u : senderParty.getMembers()) team0.add(senderLeader);
            for (UUID u : targetParty.getMembers()) team1.add(targetLeader);
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(challenge.kit().getId());
        if (arenaInstance == null) {
            ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
            if (template != null) {
                arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
            }
        }
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(targetLeader, "<yellow>No arenas are currently available for this kit. Auto-scaling, please try again in a moment.");
            return false;
        }

        pendingPartyChallenges.remove(targetLeader.getUniqueId());

        TeamMatch match = new TeamMatch(plugin, arenaInstance, challenge.kit(), team0, team1, "Team " + senderLeader.getName(), "Team " + targetLeader.getName());
        plugin.getMatchManager().registerMatch(match);
        match.start();

        String tag = size + "v" + size;
        senderParty.broadcast("<gradient:#00FFAA:#00AAFF><bold>" + tag + " PARTY DUEL STARTED!</bold></gradient> vs Team " + targetLeader.getName() + "!");
        targetParty.broadcast("<gradient:#00FFAA:#00AAFF><bold>" + tag + " PARTY DUEL STARTED!</bold></gradient> vs Team " + senderLeader.getName() + "!");
        return true;
    }

    public boolean declinePartyDuel(@NotNull Player targetLeader, @NotNull Player senderLeader) {
        PartyChallenge challenge = pendingPartyChallenges.remove(targetLeader.getUniqueId());
        if (challenge != null) {
            TextUtil.sendPrefixed(targetLeader, "<yellow>Declined party duel challenge from Team " + senderLeader.getName() + ".");
            TextUtil.sendPrefixed(senderLeader, "<red>Team " + targetLeader.getName() + " declined your party duel challenge.");
            return true;
        }
        TextUtil.sendPrefixed(targetLeader, "<red>No pending party duel to decline.");
        return false;
    }
}
