package com.apexpvp.economy;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.match.MatchState;
import com.apexpvp.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages spectator wagering and pari-mutuel dividend distribution for active PvP duels.
 */
public class BettingManager {

    public static class MatchBettingPool {
        private final UUID matchId;
        // Participant UUID -> (Bettor UUID -> Total Bet Amount)
        private final Map<UUID, Map<UUID, Double>> bets = new ConcurrentHashMap<>();

        public MatchBettingPool(UUID matchId) {
            this.matchId = matchId;
        }

        public UUID getMatchId() {
            return matchId;
        }

        public void addBet(UUID targetParticipantUuid, UUID bettorUuid, double amount) {
            bets.computeIfAbsent(targetParticipantUuid, k -> new ConcurrentHashMap<>())
                .merge(bettorUuid, amount, Double::sum);
        }

        public double getTotalPot() {
            double total = 0.0;
            for (Map<UUID, Double> bettorMap : bets.values()) {
                for (double amount : bettorMap.values()) {
                    total += amount;
                }
            }
            return total;
        }

        public double getTotalBetsFor(UUID participantUuid) {
            Map<UUID, Double> bettorMap = bets.get(participantUuid);
            if (bettorMap == null) return 0.0;
            double sum = 0.0;
            for (double val : bettorMap.values()) {
                sum += val;
            }
            return sum;
        }

        @NotNull
        public Map<UUID, Double> getBetsFor(UUID participantUuid) {
            Map<UUID, Double> bettorMap = bets.get(participantUuid);
            return (bettorMap != null) ? Collections.unmodifiableMap(bettorMap) : Collections.emptyMap();
        }

        @NotNull
        public Map<UUID, Map<UUID, Double>> getAllBets() {
            return Collections.unmodifiableMap(bets);
        }
    }

    private final ApexPvPPlugin plugin;
    private final Map<UUID, MatchBettingPool> activePools = new ConcurrentHashMap<>();

    public BettingManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Places a spectator wager on a match participant during PREPARING or COUNTDOWN phase.
     */
    public synchronized boolean placeBet(@NotNull Player bettor,
                                         @NotNull Match match,
                                         @NotNull UUID targetParticipantUuid,
                                         double amount) {
        if (amount <= 0.0) {
            TextUtil.sendPrefixed(bettor, "<red>Bet amount must be greater than zero.");
            return false;
        }

        double minBet = plugin.getConfig().getDouble("duels.betting.min-bet", 10.0);
        if (amount < minBet) {
            TextUtil.sendPrefixed(bettor, "<red>The minimum bet amount is <gold>" + minBet + "</gold> coins.");
            return false;
        }

        if (match.getState() != MatchState.PREPARING && match.getState() != MatchState.COUNTDOWN) {
            TextUtil.sendPrefixed(bettor, "<red>Betting is closed! Bets can only be placed before combat starts.");
            return false;
        }

        if (match.isParticipant(bettor.getUniqueId())) {
            TextUtil.sendPrefixed(bettor, "<red>You cannot bet while participating in this match!");
            return false;
        }

        if (!match.isParticipant(targetParticipantUuid)) {
            TextUtil.sendPrefixed(bettor, "<red>Target player is not a combatant in this match.");
            return false;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            TextUtil.sendPrefixed(bettor, "<red>Economy system is currently unavailable.");
            return false;
        }

        if (!economy.has(bettor, amount)) {
            TextUtil.sendPrefixed(bettor, "<red>You do not have enough coins! Balance: <gold>" + economy.getBalance(bettor) + "</gold>.");
            return false;
        }

        var res = economy.withdrawPlayer(bettor, amount);
        if (!res.transactionSuccess()) {
            TextUtil.sendPrefixed(bettor, "<red>Failed to process bet transaction.");
            return false;
        }

        MatchBettingPool pool = activePools.computeIfAbsent(match.getMatchId(), MatchBettingPool::new);
        pool.addBet(targetParticipantUuid, bettor.getUniqueId(), amount);

        MatchParticipant targetPart = match.getParticipant(targetParticipantUuid);
        String targetName = (targetPart != null) ? targetPart.getName() : "Unknown";

        TextUtil.sendPrefixed(bettor, "<green>You placed a bet of <gold>" + amount + " coins</gold> on <yellow>" + targetName + "</yellow>!");
        match.broadcast("<gray>[Betting] <gold>" + bettor.getName() + "</gold> wagered <yellow>" + amount + " coins</yellow> on <aqua>" + targetName + "</aqua>.");
        return true;
    }

    /**
     * Resolves pari-mutuel dividend payouts upon match victory.
     */
    public synchronized void onMatchEnd(@NotNull Match match, int winningTeamIndex) {
        MatchBettingPool pool = activePools.remove(match.getMatchId());
        if (pool == null) return;

        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        if (winningTeamIndex < 0) {
            // Draw - refund all bettors
            refundPool(pool);
            match.broadcast("<yellow>[Betting] Match ended in a draw! All spectator bets have been refunded.");
            return;
        }

        // Collect every participant on the winning team. Team matches have more
        // than one winner, so considering only the first member would strand the
        // bets placed on the others in the pool.
        Set<UUID> winningUuids = new LinkedHashSet<>();
        List<String> winnerNames = new ArrayList<>();
        for (MatchParticipant part : match.getParticipants()) {
            if (part.getTeamIndex() == winningTeamIndex) {
                winningUuids.add(part.getUuid());
                winnerNames.add(part.getName());
            }
        }

        if (winningUuids.isEmpty()) {
            refundPool(pool);
            return;
        }

        String winnerName = String.join(" & ", winnerNames);

        double totalPool = pool.getTotalPot();
        double totalWinnerBets = 0.0;
        for (UUID uuid : winningUuids) {
            totalWinnerBets += pool.getTotalBetsFor(uuid);
        }

        if (totalWinnerBets <= 0.0) {
            // Nobody bet on any member of the winning team: refund everyone
            refundPool(pool);
            match.broadcast("<yellow>[Betting] No bets were placed on <gold>" + winnerName + "</gold>. All bets refunded.");
            return;
        }

        // Aggregate each bettor's stakes across every winning-team participant so
        // a bettor who backed two winners is paid on the combined stake.
        Map<UUID, Double> winnerBets = new LinkedHashMap<>();
        for (UUID uuid : winningUuids) {
            for (Map.Entry<UUID, Double> entry : pool.getBetsFor(uuid).entrySet()) {
                winnerBets.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        double taxPercent = plugin.getConfig().getDouble("duels.betting.server-tax-percent", 5.0);
        double netPool = totalPool * (1.0 - (Math.max(0.0, taxPercent) / 100.0));

        for (Map.Entry<UUID, Double> entry : winnerBets.entrySet()) {
            UUID bettorUuid = entry.getKey();
            double betAmount = entry.getValue();
            double share = betAmount / totalWinnerBets;
            double payout = netPool * share;

            economy.depositPlayer(plugin.getServer().getOfflinePlayer(bettorUuid), payout);

            Player bettorPlayer = plugin.getServer().getPlayer(bettorUuid);
            if (bettorPlayer != null && bettorPlayer.isOnline()) {
                double profit = payout - betAmount;
                TextUtil.sendPrefixed(bettorPlayer, "<gradient:#00FFAA:#00AAFF><bold>BET WON!</bold></gradient> You won <gold>"
                        + String.format("%.2f", payout) + " coins</gold> on <yellow>" + winnerName
                        + "</yellow>! (Profit: <green>+" + String.format("%.2f", profit) + "</green>)");
            }
        }

        match.broadcast("<gradient:#00FFAA:#FFFF55>[Betting]</gradient> Paid out <gold>" + String.format("%.2f", netPool)
                + " coins</gold> from total pool of <yellow>" + String.format("%.2f", totalPool) + "</yellow> to winning bettors!");
    }

    /**
     * Refunds all bets placed on a match without tax deductions.
     */
    public synchronized void refundMatch(@NotNull UUID matchId) {
        MatchBettingPool pool = activePools.remove(matchId);
        if (pool != null) {
            refundPool(pool);
        }
    }

    private void refundPool(@NotNull MatchBettingPool pool) {
        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        for (Map<UUID, Double> bettorMap : pool.getAllBets().values()) {
            for (Map.Entry<UUID, Double> entry : bettorMap.entrySet()) {
                UUID bettorUuid = entry.getKey();
                double amount = entry.getValue();
                economy.depositPlayer(plugin.getServer().getOfflinePlayer(bettorUuid), amount);

                Player bettor = plugin.getServer().getPlayer(bettorUuid);
                if (bettor != null && bettor.isOnline()) {
                    TextUtil.sendPrefixed(bettor, "<yellow>Your bet of <gold>" + amount + " coins</gold> has been refunded.");
                }
            }
        }
    }

    @Nullable
    public MatchBettingPool getPool(@NotNull UUID matchId) {
        return activePools.get(matchId);
    }

    public void shutdown() {
        if (activePools.isEmpty()) return;
        plugin.getLogger().info("Refunding " + activePools.size() + " active spectator betting pools on shutdown...");
        for (UUID matchId : new java.util.ArrayList<>(activePools.keySet())) {
            try {
                refundMatch(matchId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refund betting pool for match " + matchId, e);
            }
        }
        activePools.clear();
    }
}
