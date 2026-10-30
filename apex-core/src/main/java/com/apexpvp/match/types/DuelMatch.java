package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
import com.apexpvp.profile.Rank;
import com.apexpvp.util.EloCalculator;
import com.apexpvp.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 1v1 Player Duel with optional currency stake escrow payout.
 */
public class DuelMatch extends Match {

    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private final double stakeAmount;

    public DuelMatch(@NotNull ApexPvPPlugin plugin,
                     @NotNull ArenaInstance arena,
                     @NotNull Kit kit,
                     @NotNull Player player1,
                     @NotNull Player player2,
                     double stakeAmount) {
        super(plugin, arena, kit);
        this.player1Uuid = player1.getUniqueId();
        this.player2Uuid = player2.getUniqueId();
        this.stakeAmount = Math.max(0.0, stakeAmount);

        addParticipant(player1, 0); // Team 0
        addParticipant(player2, 1); // Team 1
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        UUID winnerUuid = (winningTeamIndex == 0) ? player1Uuid : (winningTeamIndex == 1 ? player2Uuid : null);
        UUID loserUuid = (winningTeamIndex == 0) ? player2Uuid : (winningTeamIndex == 1 ? player1Uuid : null);

        if (winnerUuid == null || loserUuid == null) {
            broadcast("<yellow>The duel ended in a draw! Any staked funds have been refunded.");
            refundStakes();
            return;
        }

        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);

        PlayerProfile winnerProfile = plugin.getProfileManager().getProfile(winnerUuid);
        PlayerProfile loserProfile = plugin.getProfileManager().getProfile(loserUuid);

        String winnerName = (winner != null) ? winner.getName() : (winnerProfile != null ? winnerProfile.getUsername() : "Winner");
        String loserName = (loser != null) ? loser.getName() : (loserProfile != null ? loserProfile.getUsername() : "Loser");

        int eloDelta = 0;
        boolean eloEnabled = plugin.getConfig().getBoolean("duels.elo.enabled", false);
        if (eloEnabled && winnerProfile != null && loserProfile != null) {
            int winnerOldElo = winnerProfile.getElo();
            int loserOldElo = loserProfile.getElo();

            int kFactor = plugin.getConfig().getInt("ranked.k-factor", 32);
            boolean dynamicK = plugin.getConfig().getBoolean("ranked.dynamic-k-factor", true);
            int totalWinnerMatches = winnerProfile.getAllStats().values().stream()
                    .mapToInt(s -> s.getWins() + s.getLosses()).sum();

            eloDelta = (kFactor > 0 && !dynamicK)
                    ? EloCalculator.calculateDelta(winnerOldElo, loserOldElo, kFactor)
                    : EloCalculator.calculateDynamicDelta(winnerOldElo, loserOldElo, totalWinnerMatches);

            int winnerNewElo = winnerProfile.modifyElo(eloDelta);
            int loserNewElo = loserProfile.modifyElo(-eloDelta);

            // Check Rank Progression
            Rank newRank = Rank.fromElo(winnerNewElo);
            if (newRank.ordinal() > winnerProfile.getRank().ordinal()) {
                boolean requireRankupFee = plugin.getConfig().getBoolean("ranked.economy.require-rankup-fee", false);
                if (!requireRankupFee) {
                    winnerProfile.setRank(newRank);
                    broadcast("<gradient:#00FFAA:#00AAFF><bold>RANK UP!</bold></gradient> <gold>" + winnerProfile.getUsername() + "</gold> reached " + newRank.getMiniMessageDisplay() + "!");
                } else if (winner != null) {
                    winner.sendMessage(TextUtil.parse(plugin.getConfig().getString("rank.threshold-reached", "<green>You unlocked rank threshold for " + newRank.getDisplayName() + "! Use /rankup.")));
                }
            }

            if (winner != null) {
                TextUtil.sendPrefixed(winner, "<green>+" + eloDelta + " ELO <gray>(Rating: " + winnerNewElo + ")");
            }
            if (loser != null) {
                TextUtil.sendPrefixed(loser, "<red>-" + eloDelta + " ELO <gray>(Rating: " + loserNewElo + ")");
            }
        }

        String winnerDisplay = "<gold>" + winnerName + "</gold>" + (eloDelta > 0 ? " (+<green>" + eloDelta + "</green> ELO)" : "");
        String loserDisplay = "<red>" + loserName + "</red>" + (eloDelta > 0 ? " (-<red>" + eloDelta + "</red> ELO)" : "");
        broadcast("<gradient:#00FFAA:#00AAFF><bold>DUEL ENDED!</bold></gradient> " + winnerDisplay + " defeated " + loserDisplay + "!");

        // Update statistics and streaks
        if (winnerProfile != null) {
            PlayerStats stats = winnerProfile.getOrCreateStats(kit.getId());
            stats.incrementWins();
            stats.incrementKills();
            int streak = winnerProfile.incrementStreak();
            winnerProfile.resetLossStreak();
            if (streak >= 3) {
                broadcast("<gold>" + winnerProfile.getUsername() + "</gold> is on a <gradient:#FFAA00:#FF5555><bold>" + streak + "-MATCH WIN STREAK!</bold></gradient>");
            }
            winnerProfile.markDirty();
            plugin.getProfileManager().saveProfile(winnerProfile);
        }

        if (loserProfile != null) {
            PlayerStats stats = loserProfile.getOrCreateStats(kit.getId());
            stats.incrementLosses();
            stats.incrementDeaths();
            loserProfile.resetStreak();
            loserProfile.incrementLossStreak();
            loserProfile.markDirty();
            plugin.getProfileManager().saveProfile(loserProfile);
        }

        // Process Escrow Stake Payout
        if (stakeAmount > 0) {
            double taxPercent = plugin.getConfig().getDouble("duels.betting.server-tax-percent", 5.0);
            plugin.getEscrowManager().distributePayout(matchId, Bukkit.getOfflinePlayer(winnerUuid), taxPercent);
            double totalPot = stakeAmount * 2.0;
            double taxDeduction = totalPot * (taxPercent / 100.0);
            double netWinnings = totalPot - taxDeduction;
            broadcast("<gold>" + winnerName + "</gold> won the wager of <yellow>" + netWinnings + "</yellow> coins! (Tax: " + taxDeduction + ")");
        }

        // Record to audit database with accurate eloDelta
        int durationSec = (int) ((endTime - startTime) / 1000);
        plugin.getDatabaseManager().executeUpdateAsync(
            "INSERT INTO apex_match_history (match_id, mode, kit_id, arena_id, winner_uuid, loser_uuid, elo_delta, stake_amount, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            matchId.toString(), "DUEL", kit.getId(), arena.getTemplate().getId(),
            winnerUuid.toString(), loserUuid.toString(), eloDelta, stakeAmount, durationSec
        );
    }

    private void refundStakes() {
        if (stakeAmount <= 0) return;
        plugin.getEscrowManager().refund(matchId);
    }

    @Override
    public void cleanup() {
        // Guarantee escrow refund if match is cleaned up without payout
        refundStakes();
        super.cleanup();
    }
}
