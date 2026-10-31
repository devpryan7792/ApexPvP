package com.apexpvp.queue;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.RankedMatch;
import com.apexpvp.match.types.RankedTeamMatch;
import com.apexpvp.party.Party;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-performance matchmaking queue manager with expanding ELO window ticker.
 */
public class QueueManager implements Runnable {

    private final ApexPvPPlugin plugin;
    private final Map<String, List<QueueTicket>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, QueueTicket> playerTickets = new ConcurrentHashMap<>();

    private final int initialSearchRange;
    private final int expansionStep;
    private final long expansionIntervalMillis;

    private BukkitTask tickerTask;

    public QueueManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.initialSearchRange = plugin.getConfig().getInt("ranked.queue.initial-search-range", 50);
        this.expansionStep = plugin.getConfig().getInt("ranked.queue.range-expansion-step", 25);
        this.expansionIntervalMillis = plugin.getConfig().getInt("ranked.queue.expansion-interval-seconds", 5) * 1000L;
    }

    public void start() {
        // Run matchmaker ticker every second (20 ticks)
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    public void stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        queues.clear();
        playerTickets.clear();
    }

    public boolean queuePlayer(@NotNull Player player, @NotNull Kit kit) {
        UUID uuid = player.getUniqueId();

        // Prevent queueing if already in a match or queue
        if (plugin.getMatchManager().isInMatch(player)) {
            TextUtil.sendPrefixed(player, "<red>You cannot join matchmaking while inside an active match!");
            return false;
        }

        if (playerTickets.containsKey(uuid)) {
            TextUtil.sendPrefixed(player, "<red>You are already in a matchmaking queue!");
            return false;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) {
            TextUtil.sendPrefixed(player, "<red>Profile data is still loading. Please try again shortly.");
            return false;
        }

        // Deduct Ranked entry fee via Vault if configured
        boolean economyEnabled = plugin.getConfig().getBoolean("ranked.economy.enabled", true);
        double entryFee = plugin.getConfig().getDouble("ranked.economy.entry-fee", 25.0);

        if (economyEnabled && entryFee > 0.0) {
            Economy economy = plugin.getEconomy();
            if (economy != null) {
                if (!economy.has(player, entryFee)) {
                    TextUtil.sendPrefixed(player, "<red>You need at least <gold><amount></gold> coins to queue for Ranked!", Placeholder.parsed("amount", String.valueOf(entryFee)));
                    return false;
                }
                var res = economy.withdrawPlayer(player, entryFee);
                if (!res.transactionSuccess()) {
                    TextUtil.sendPrefixed(player, "<red>Failed to deduct entry fee.");
                    return false;
                }
            }
        }

        QueueTicket ticket = new QueueTicket(uuid, kit.getId(), profile.getElo(), initialSearchRange);
        queues.computeIfAbsent(kit.getId(), k -> new CopyOnWriteArrayList<>()).add(ticket);
        playerTickets.put(uuid, ticket);

        TextUtil.sendPrefixed(player, "<green>Joined <gold><kit></gold> ranked queue. ELO: <yellow><elo></yellow>. Searching...",
                Placeholder.parsed("kit", kit.getDisplayName()),
                Placeholder.parsed("elo", String.valueOf(profile.getElo())));

        return true;
    }

    public boolean queueParty(@NotNull Player leader, @NotNull Kit kit) {
        Party party = plugin.getPartyManager().getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can queue for Team Ranked!");
            return false;
        }

        int teamSize = party.getSize();
        if (teamSize < 2 || teamSize > 4) {
            TextUtil.sendPrefixed(leader, "<red>Team Ranked matchmaking supports teams of 2 to 4 players (min 2v2, max 4v4)! Your party has " + teamSize + " members.");
            return false;
        }

        List<Player> members = new ArrayList<>();
        List<UUID> memberUuids = new ArrayList<>();
        List<PlayerProfile> profiles = new ArrayList<>();

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                TextUtil.sendPrefixed(leader, "<red>All party members must be online to queue for Team Ranked!");
                return false;
            }
            if (plugin.getMatchManager().isInMatch(p)) {
                TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " is already in an active match!");
                return false;
            }
            if (playerTickets.containsKey(uuid)) {
                TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " is already in a matchmaking queue!");
                return false;
            }
            PlayerProfile prof = plugin.getProfileManager().getProfile(uuid);
            if (prof == null) {
                TextUtil.sendPrefixed(leader, "<red>Profile data for " + p.getName() + " is still loading.");
                return false;
            }
            members.add(p);
            memberUuids.add(uuid);
            profiles.add(prof);
        }

        boolean economyEnabled = plugin.getConfig().getBoolean("ranked.economy.enabled", true);
        double entryFee = plugin.getConfig().getDouble("ranked.economy.entry-fee", 25.0);
        Economy economy = plugin.getEconomy();

        if (economyEnabled && entryFee > 0.0 && economy != null) {
            for (Player p : members) {
                if (!economy.has(p, entryFee)) {
                    TextUtil.sendPrefixed(leader, "<red>" + p.getName() + " does not have enough coins (" + entryFee + ") for the entry fee!");
                    return false;
                }
            }
            for (Player p : members) {
                economy.withdrawPlayer(p, entryFee);
            }
        }

        int avgElo = (int) profiles.stream().mapToInt(PlayerProfile::getElo).average().orElse(1000.0);
        QueueTicket ticket = new QueueTicket(leader.getUniqueId(), memberUuids, teamSize, kit.getId(), avgElo, initialSearchRange);

        queues.computeIfAbsent(kit.getId(), k -> new CopyOnWriteArrayList<>()).add(ticket);
        for (UUID u : memberUuids) {
            playerTickets.put(u, ticket);
        }

        String modeTag = teamSize + "v" + teamSize;
        party.broadcast("<green>Party entered <gold>" + modeTag + " " + kit.getDisplayName() + "</gold> Ranked Queue! Team Avg ELO: <yellow>" + avgElo + "</yellow>. Searching...");
        return true;
    }

    public boolean leaveQueue(@NotNull Player player) {
        QueueTicket ticket = playerTickets.get(player.getUniqueId());
        if (ticket == null) {
            TextUtil.sendPrefixed(player, "<red>You are not in a matchmaking queue.");
            return false;
        }

        removeTicket(ticket);

        boolean economyEnabled = plugin.getConfig().getBoolean("ranked.economy.enabled", true);
        double entryFee = plugin.getConfig().getDouble("ranked.economy.entry-fee", 25.0);
        Economy economy = plugin.getEconomy();

        if (ticket.isTeamQueue()) {
            if (economyEnabled && entryFee > 0.0 && economy != null) {
                for (UUID u : ticket.getPartyMembers()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.isOnline()) {
                        economy.depositPlayer(p, entryFee);
                    }
                }
            }
            for (UUID u : ticket.getPartyMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    TextUtil.sendPrefixed(p, "<yellow>Your party left the " + ticket.getTeamSize() + "v" + ticket.getTeamSize() + " matchmaking queue (" + player.getName() + " cancelled).");
                }
            }
        } else {
            if (economyEnabled && entryFee > 0.0 && economy != null) {
                economy.depositPlayer(player, entryFee);
            }
            TextUtil.sendPrefixed(player, "<yellow>You left the matchmaking queue. Entry fee refunded.");
        }

        return true;
    }

    public boolean isInQueue(@NotNull UUID uuid) {
        return playerTickets.containsKey(uuid);
    }

    @org.jetbrains.annotations.Nullable
    public QueueTicket getTicket(@NotNull UUID uuid) {
        return playerTickets.get(uuid);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, List<QueueTicket>> entry : queues.entrySet()) {
            String kitId = entry.getKey();
            List<QueueTicket> ticketList = entry.getValue();

            // 1. Expand search radius every 5 seconds for waiting players
            for (QueueTicket ticket : ticketList) {
                if (now - ticket.getLastExpansionTimestamp() >= expansionIntervalMillis) {
                    ticket.expandRange(expansionStep);
                    Player p = Bukkit.getPlayer(ticket.getPlayerId());
                    if (p != null) {
                        TextUtil.sendPrefixed(p, "<dark_gray>Queue search expanded to <gold>±<range></gold> ELO...",
                                Placeholder.parsed("range", String.valueOf(ticket.getCurrentSearchRange())));
                    }
                }
            }

            // 2. Scan for compatible candidates in pairs
            while (ticketList.size() >= 2) {
                QueueTicket candidate1 = null;
                QueueTicket candidate2 = null;

                for (int i = 0; i < ticketList.size(); i++) {
                    QueueTicket t1 = ticketList.get(i);
                    for (int j = i + 1; j < ticketList.size(); j++) {
                        QueueTicket t2 = ticketList.get(j);
                        if (t1.matches(t2)) {
                            candidate1 = t1;
                            candidate2 = t2;
                            break;
                        }
                    }
                    if (candidate1 != null) break;
                }

                if (candidate1 == null || candidate2 == null) {
                    break; // No more matching pairs for this kit in this tick
                }

                Player p1 = Bukkit.getPlayer(candidate1.getPlayerId());
                Player p2 = Bukkit.getPlayer(candidate2.getPlayerId());

                // Validate all members are online for both candidate tickets
                boolean invalid1 = candidate1.getPartyMembers().stream().anyMatch(u -> {
                    Player p = Bukkit.getPlayer(u);
                    return p == null || !p.isOnline();
                });
                boolean invalid2 = candidate2.getPartyMembers().stream().anyMatch(u -> {
                    Player p = Bukkit.getPlayer(u);
                    return p == null || !p.isOnline();
                });

                if (invalid1 || invalid2) {
                    if (invalid1) removeTicket(candidate1);
                    if (invalid2) removeTicket(candidate2);
                    continue;
                }

                Kit kit = plugin.getKitManager().getKit(kitId);
                if (kit == null) {
                    removeTicket(candidate1);
                    removeTicket(candidate2);
                    break;
                }

                // Find available arena instance
                ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
                if (template == null) {
                    break;
                }

                ArenaInstance arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
                if (arenaInstance == null) {
                    // Signal auto-scaler to spawn a new instance and halt queue draining for this kit
                    plugin.getArenaManager().requestScaling(template.getId());
                    break;
                }

                // Match found! Remove tickets
                removeTicket(candidate1);
                removeTicket(candidate2);

                if (candidate1.isTeamQueue()) {
                    List<Player> team0 = new ArrayList<>();
                    for (UUID u : candidate1.getPartyMembers()) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) team0.add(p);
                    }
                    List<Player> team1 = new ArrayList<>();
                    for (UUID u : candidate2.getPartyMembers()) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) team1.add(p);
                    }

                    int modeSize = candidate1.getTeamSize();
                    String tag = modeSize + "v" + modeSize;
                    String matchHeader = "<gradient:#00FF88:#00AAFF><bold>" + tag + " MATCH FOUND!</bold></gradient>";

                    for (Player p : team0) {
                        TextUtil.sendPrefixed(p, matchHeader + " Opponent Team: <gold>Team " + p2.getName() + "</gold> (<yellow>" + candidate2.getInitialElo() + "</yellow> Avg ELO).");
                    }
                    for (Player p : team1) {
                        TextUtil.sendPrefixed(p, matchHeader + " Opponent Team: <gold>Team " + p1.getName() + "</gold> (<yellow>" + candidate1.getInitialElo() + "</yellow> Avg ELO).");
                    }

                    RankedTeamMatch match = new RankedTeamMatch(plugin, arenaInstance, kit, team0, team1, "Team " + p1.getName(), "Team " + p2.getName());
                    plugin.getMatchManager().registerMatch(match);
                    match.start();
                } else {
                    TextUtil.sendPrefixed(p1, "<gradient:#00FF88:#00AAFF><bold>MATCH FOUND!</bold></gradient> Opponent: <gold><opponent></gold> (<yellow><opponent_elo></yellow> ELO).",
                            Placeholder.parsed("opponent", p2.getName()),
                            Placeholder.parsed("opponent_elo", String.valueOf(candidate2.getInitialElo())));

                    TextUtil.sendPrefixed(p2, "<gradient:#00FF88:#00AAFF><bold>MATCH FOUND!</bold></gradient> Opponent: <gold><opponent></gold> (<yellow><opponent_elo></yellow> ELO).",
                            Placeholder.parsed("opponent", p1.getName()),
                            Placeholder.parsed("opponent_elo", String.valueOf(candidate1.getInitialElo())));

                    RankedMatch match = new RankedMatch(plugin, arenaInstance, kit, p1, p2);
                    plugin.getMatchManager().registerMatch(match);
                    match.start();
                }
            }
        }
    }

    private void removeTicket(@NotNull QueueTicket ticket) {
        for (UUID u : ticket.getPartyMembers()) {
            playerTickets.remove(u);
        }
        List<QueueTicket> list = queues.get(ticket.getKitId());
        if (list != null) {
            list.remove(ticket);
        }
    }
}
