package com.apexpvp.queue;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.FfaMatch;
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
    private final Map<String, List<UUID>> ffaQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> ffaQueuePlayers = new ConcurrentHashMap<>();
    /** Exact amount each queued player was charged, so refunds happen once at the paid price. */
    private final Map<UUID, Double> paidEntryFees = new ConcurrentHashMap<>();

    private volatile int initialSearchRange;
    private volatile int expansionStep;
    private volatile long expansionIntervalMillis;

    private BukkitTask tickerTask;

    public QueueManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        reloadConfig();
    }

    /**
     * Re-reads queue tuning values. Existing tickets keep their current range and
     * pick up the new expansion step/interval on their next tick.
     */
    public void reloadConfig() {
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
        // Refund everyone still waiting so a restart does not eat entry fees.
        for (List<QueueTicket> tickets : queues.values()) {
            for (QueueTicket ticket : tickets) {
                refundTicketFees(ticket);
            }
        }
        for (UUID uuid : new ArrayList<>(ffaQueuePlayers.keySet())) {
            refundFee(uuid);
        }
        queues.clear();
        playerTickets.clear();
        ffaQueues.clear();
        ffaQueuePlayers.clear();
        paidEntryFees.clear();
    }

    public boolean queuePlayer(@NotNull Player player, @NotNull Kit kit) {
        UUID uuid = player.getUniqueId();

        // Prevent queueing if already in a match or queue
        if (plugin.getMatchManager().isInMatch(player)) {
            TextUtil.sendPrefixed(player, "<red>You cannot join matchmaking while inside an active match!");
            return false;
        }

        if (playerTickets.containsKey(uuid) || ffaQueuePlayers.containsKey(uuid)) {
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
                recordFee(uuid, entryFee);
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
            if (playerTickets.containsKey(uuid) || ffaQueuePlayers.containsKey(uuid)) {
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
            // Withdraw as a transaction: if any member fails, refund everyone already charged.
            List<Player> charged = new ArrayList<>();
            for (Player p : members) {
                var res = economy.withdrawPlayer(p, entryFee);
                if (res == null || !res.transactionSuccess()) {
                    for (Player refunded : charged) {
                        economy.depositPlayer(refunded, entryFee);
                    }
                    TextUtil.sendPrefixed(leader, "<red>Failed to deduct the entry fee from " + p.getName() + ". No one was charged.");
                    return false;
                }
                charged.add(p);
                recordFee(p.getUniqueId(), entryFee);
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
            if (ffaQueuePlayers.containsKey(player.getUniqueId())) {
                return leaveFfaQueue(player);
            }
            TextUtil.sendPrefixed(player, "<red>You are not in a matchmaking queue.");
            return false;
        }

        removeTicket(ticket);

        if (ticket.isTeamQueue()) {
            refundTicketFees(ticket);
            for (UUID u : ticket.getPartyMembers()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    TextUtil.sendPrefixed(p, "<yellow>Your party left the " + ticket.getTeamSize() + "v" + ticket.getTeamSize() + " matchmaking queue (" + player.getName() + " cancelled).");
                }
            }
        } else {
            refundFee(player);
            TextUtil.sendPrefixed(player, "<yellow>You left the matchmaking queue. Entry fee refunded.");
        }

        return true;
    }

    public boolean queueFfaPlayer(@NotNull Player player, @NotNull Kit kit) {
        UUID uuid = player.getUniqueId();

        if (plugin.getMatchManager().isInMatch(player)) {
            TextUtil.sendPrefixed(player, "<red>You cannot join matchmaking while inside an active match!");
            return false;
        }

        if (playerTickets.containsKey(uuid) || ffaQueuePlayers.containsKey(uuid)) {
            TextUtil.sendPrefixed(player, "<red>You are already in a matchmaking queue!");
            return false;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) {
            TextUtil.sendPrefixed(player, "<red>Profile data is still loading. Please try again shortly.");
            return false;
        }

        boolean economyEnabled = plugin.getConfig().getBoolean("ranked.economy.enabled", true);
        double entryFee = plugin.getConfig().getDouble("ffa.entry-fee", 10.0);
        Economy economy = plugin.getEconomy();

        if (economyEnabled && entryFee > 0.0 && economy != null) {
            if (!economy.has(player, entryFee)) {
                TextUtil.sendPrefixed(player, "<red>You do not have enough coins (" + entryFee + ") for the FFA entry fee!");
                return false;
            }
            var res = economy.withdrawPlayer(player, entryFee);
            if (res == null || !res.transactionSuccess()) {
                TextUtil.sendPrefixed(player, "<red>Failed to deduct the FFA entry fee.");
                return false;
            }
            recordFee(uuid, entryFee);
        }

        List<UUID> q = ffaQueues.computeIfAbsent(kit.getId(), k -> new CopyOnWriteArrayList<>());
        q.add(uuid);
        ffaQueuePlayers.put(uuid, kit.getId());

        int targetSize = plugin.getConfig().getInt("ffa.queue-size", 4);
        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>FFA QUEUE JOINED!</bold></gradient> You joined the <yellow>" + kit.getDisplayName() + "</yellow> Free-For-All queue (<gold>" + q.size() + "/" + targetSize + "</gold> players).");
        return true;
    }

    public boolean leaveFfaQueue(@NotNull Player player) {
        String kitId = ffaQueuePlayers.remove(player.getUniqueId());
        if (kitId == null) {
            return false;
        }

        List<UUID> q = ffaQueues.get(kitId);
        if (q != null) {
            q.remove(player.getUniqueId());
        }

        refundFee(player);

        TextUtil.sendPrefixed(player, "<yellow>You left the FFA matchmaking queue. Entry fee refunded.");
        return true;
    }

    public boolean isInFfaQueue(@NotNull UUID uuid) {
        return ffaQueuePlayers.containsKey(uuid);
    }

    public int getFfaQueueCount(@NotNull String kitId) {
        List<UUID> q = ffaQueues.get(kitId);
        return q != null ? q.size() : 0;
    }

    public boolean isInQueue(@NotNull UUID uuid) {
        return playerTickets.containsKey(uuid) || ffaQueuePlayers.containsKey(uuid);
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
                    if (invalid1) {
                        removeTicket(candidate1);
                        refundTicketFees(candidate1);
                    }
                    if (invalid2) {
                        removeTicket(candidate2);
                        refundTicketFees(candidate2);
                    }
                    continue;
                }

                Kit kit = plugin.getKitManager().getKit(kitId);
                if (kit == null) {
                    removeTicket(candidate1);
                    refundTicketFees(candidate1);
                    removeTicket(candidate2);
                    refundTicketFees(candidate2);
                    break;
                }

                // Find available arena instance that supports this kit
                ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kitId);
                if (arenaInstance == null) {
                    break;
                }

                // Match found! Remove tickets, then create the match. If creation
                // fails, refund both tickets so players are not charged for nothing.
                removeTicket(candidate1);
                removeTicket(candidate2);

                try {
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
                        consumeTicketFees(candidate1);
                        consumeTicketFees(candidate2);
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
                        consumeTicketFees(candidate1);
                        consumeTicketFees(candidate2);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Failed to start ranked match: " + ex.getMessage());
                    refundTicketFees(candidate1);
                    refundTicketFees(candidate2);
                }
            }
        }

        // 3. Scan for FFA queue candidates
        int targetFfaSize = plugin.getConfig().getInt("ffa.queue-size", 4);
        for (Map.Entry<String, List<UUID>> entry : ffaQueues.entrySet()) {
            String kitId = entry.getKey();
            List<UUID> list = entry.getValue();

            list.removeIf(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || plugin.getMatchManager().isInMatch(p)) {
                    ffaQueuePlayers.remove(uuid);
                    refundFee(uuid);
                    return true;
                }
                return false;
            });

            while (list.size() >= targetFfaSize) {
                Kit kit = plugin.getKitManager().getKit(kitId);
                if (kit == null) {
                    for (UUID uuid : list) {
                        refundFee(uuid);
                    }
                    list.clear();
                    ffaQueuePlayers.clear();
                    break;
                }

                // Find available arena instance that supports this kit
                ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kitId);
                if (arenaInstance == null) {
                    break;
                }

                List<Player> combatants = new ArrayList<>();
                List<UUID> pulled = new ArrayList<>();
                for (int i = 0; i < targetFfaSize; i++) {
                    UUID u = list.remove(0);
                    pulled.add(u);
                    ffaQueuePlayers.remove(u);
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) combatants.add(p);
                }

                try {
                    for (Player p : combatants) {
                        TextUtil.sendPrefixed(p, "<gradient:#00FFAA:#00AAFF><bold>FFA MATCH FOUND!</bold></gradient> Entering arena with <gold>" + combatants.size() + " combatants</gold>!");
                    }

                    FfaMatch match = new FfaMatch(plugin, arenaInstance, kit, combatants);
                    plugin.getMatchManager().registerMatch(match);
                    match.start();
                    for (UUID uuid : pulled) {
                        paidEntryFees.remove(uuid);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Failed to start FFA match: " + ex.getMessage());
                    for (UUID uuid : pulled) {
                        refundFee(uuid);
                    }
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

    private void recordFee(@NotNull UUID uuid, double amount) {
        if (amount > 0.0) {
            paidEntryFees.put(uuid, amount);
        }
    }

    /** Refunds the exact fee recorded for this player, at most once. */
    private void refundFee(@NotNull Player player) {
        refundFee(player.getUniqueId());
    }

    private void refundFee(@NotNull UUID uuid) {
        Double paid = paidEntryFees.remove(uuid);
        if (paid == null || paid <= 0.0) {
            return;
        }
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            economy.depositPlayer(online, paid);
        } else if (plugin.getServer() != null) {
            economy.depositPlayer(plugin.getServer().getOfflinePlayer(uuid), paid);
        }
    }

    private void refundTicketFees(@NotNull QueueTicket ticket) {
        for (UUID u : ticket.getPartyMembers()) {
            refundFee(u);
        }
    }

    /** Marks a ticket's entry fees as consumed once its match has actually started. */
    private void consumeTicketFees(@NotNull QueueTicket ticket) {
        for (UUID u : ticket.getPartyMembers()) {
            paidEntryFees.remove(u);
        }
    }
}
