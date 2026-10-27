package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
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

        String winnerName = (winner != null) ? winner.getName() : "Winner";
        String loserName = (loser != null) ? loser.getName() : "Loser";

        broadcast("<gradient:#00FFAA:#00AAFF><bold>DUEL ENDED!</bold></gradient> <gold>" + winnerName + "</gold> defeated <red>" + loserName + "</red>!");

        // Update statistics and streaks
        PlayerProfile winnerProfile = plugin.getProfileManager().getProfile(winnerUuid);
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
        }

        PlayerProfile loserProfile = plugin.getProfileManager().getProfile(loserUuid);
        if (loserProfile != null) {
            PlayerStats stats = loserProfile.getOrCreateStats(kit.getId());
            stats.incrementLosses();
            stats.incrementDeaths();
            loserProfile.resetStreak();
            loserProfile.incrementLossStreak();
            loserProfile.markDirty();
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

        // Record to audit database
        int durationSec = (int) ((endTime - startTime) / 1000);
        plugin.getDatabaseManager().executeUpdateAsync(
            "INSERT INTO apex_match_history (match_id, mode, kit_id, arena_id, winner_uuid, loser_uuid, elo_delta, stake_amount, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            matchId.toString(), "DUEL", kit.getId(), arena.getTemplate().getId(),
            winnerUuid.toString(), loserUuid.toString(), 0, stakeAmount, durationSec
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
