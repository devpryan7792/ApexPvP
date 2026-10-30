package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
import com.apexpvp.profile.Rank;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Competitive Ranked 1v1 match with ELO rating calculations and rank progression.
 */
public class RankedMatch extends Match {

    private final UUID player1Uuid;
    private final UUID player2Uuid;

    public RankedMatch(@NotNull ApexPvPPlugin plugin,
                       @NotNull ArenaInstance arena,
                       @NotNull Kit kit,
                       @NotNull Player player1,
                       @NotNull Player player2) {
        super(plugin, arena, kit);
        this.player1Uuid = player1.getUniqueId();
        this.player2Uuid = player2.getUniqueId();

        addParticipant(player1, 0);
        addParticipant(player2, 1);
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        UUID winnerUuid = (winningTeamIndex == 0) ? player1Uuid : (winningTeamIndex == 1 ? player2Uuid : null);
        UUID loserUuid = (winningTeamIndex == 0) ? player2Uuid : (winningTeamIndex == 1 ? player1Uuid : null);

        if (winnerUuid == null || loserUuid == null) {
            broadcast("<yellow>Ranked match ended in a draw. No ELO adjustments made.");
            return;
        }

        Player winner = Bukkit.getPlayer(winnerUuid);
        Player loser = Bukkit.getPlayer(loserUuid);

        PlayerProfile winnerProfile = plugin.getProfileManager().getProfile(winnerUuid);
        PlayerProfile loserProfile = plugin.getProfileManager().getProfile(loserUuid);

        if (winnerProfile == null || loserProfile == null) return;

        int winnerOldElo = winnerProfile.getElo();
        int loserOldElo = loserProfile.getElo();

        // Calculate ELO delta using dynamic EloCalculator
        int kFactor = plugin.getConfig().getInt("ranked.k-factor", 0);
        int totalWinnerMatches = winnerProfile.getAllStats().values().stream()
                .mapToInt(s -> s.getWins() + s.getLosses()).sum();
        int eloDelta = (kFactor > 0)
                ? com.apexpvp.util.EloCalculator.calculateDelta(winnerOldElo, loserOldElo, kFactor)
                : com.apexpvp.util.EloCalculator.calculateDynamicDelta(winnerOldElo, loserOldElo, totalWinnerMatches);

        int winnerNewElo = winnerProfile.modifyElo(eloDelta);
        int loserNewElo = loserProfile.modifyElo(-eloDelta);

        // Update stats
        PlayerStats wStats = winnerProfile.getOrCreateStats(kit.getId());
        wStats.incrementWins();
        wStats.incrementKills();

        PlayerStats lStats = loserProfile.getOrCreateStats(kit.getId());
        lStats.incrementLosses();
        lStats.incrementDeaths();

        // Automatic Rank Progression
        plugin.getProfileManager().checkAndApplyAutoRankup(winnerProfile, winner);

        // Update streaks
        int winStreak = winnerProfile.incrementStreak();
        winnerProfile.resetLossStreak();
        loserProfile.resetStreak();
        loserProfile.incrementLossStreak();

        if (winStreak >= 3) {
            broadcast("<gold>" + winnerProfile.getUsername() + "</gold> is on a <gradient:#FFAA00:#FF5555><bold>" + winStreak + "-MATCH WIN STREAK!</bold></gradient>");
        }

        // Economy win reward with streak multiplier
        double baseWinReward = plugin.getConfig().getDouble("ranked.economy.win-reward", 50.0);
        double winReward = baseWinReward * winnerProfile.getStreakMultiplier();
        Economy economy = plugin.getEconomy();
        if (economy != null && winReward > 0) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(winnerUuid), winReward);
        }

        // Broadcast results
        String wName = winner != null ? winner.getName() : winnerProfile.getUsername();
        String lName = loser != null ? loser.getName() : loserProfile.getUsername();
        broadcast("<gradient:#00FF88:#00AAFF><bold>RANKED VICTORY!</bold></gradient> <gold>" + wName + "</gold> (+<green>" + eloDelta + "</green> ELO) defeated <red>" + lName + "</red> (-<red>" + eloDelta + "</red> ELO).");

        // Record to audit database
        int durationSec = (int) ((endTime - startTime) / 1000);
        plugin.getDatabaseManager().executeUpdateAsync(
            "INSERT INTO apex_match_history (match_id, mode, kit_id, arena_id, winner_uuid, loser_uuid, elo_delta, stake_amount, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            matchId.toString(), "RANKED", kit.getId(), arena.getTemplate().getId(),
            winnerUuid.toString(), loserUuid.toString(), eloDelta, 0.0, durationSec
        );
    }
}
