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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Competitive Team Ranked match (e.g. 2v2 party battles) with team-averaged ELO calculations and rank advancement.
 */
public class RankedTeamMatch extends Match {

    private final String team0Name;
    private final String team1Name;
    private final List<UUID> team0Uuids = new ArrayList<>();
    private final List<UUID> team1Uuids = new ArrayList<>();

    public RankedTeamMatch(@NotNull ApexPvPPlugin plugin,
                           @NotNull ArenaInstance arena,
                           @NotNull Kit kit,
                           @NotNull List<Player> team0,
                           @NotNull List<Player> team1,
                           @NotNull String team0Name,
                           @NotNull String team1Name) {
        super(plugin, arena, kit);
        this.team0Name = team0Name;
        this.team1Name = team1Name;

        for (Player p : team0) {
            team0Uuids.add(p.getUniqueId());
            addParticipant(p, 0);
        }
        for (Player p : team1) {
            team1Uuids.add(p.getUniqueId());
            addParticipant(p, 1);
        }
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        if (winningTeamIndex < 0) {
            broadcast("<yellow>The ranked team match ended in a draw! No ELO adjustments made.");
            return;
        }

        String winnerTeam = (winningTeamIndex == 0) ? team0Name : team1Name;
        String loserTeam = (winningTeamIndex == 0) ? team1Name : team0Name;

        List<UUID> winnerUuids = (winningTeamIndex == 0) ? team0Uuids : team1Uuids;
        List<UUID> loserUuids = (winningTeamIndex == 0) ? team1Uuids : team0Uuids;

        List<PlayerProfile> winnerProfiles = new ArrayList<>();
        List<PlayerProfile> loserProfiles = new ArrayList<>();

        for (UUID u : winnerUuids) {
            PlayerProfile prof = plugin.getProfileManager().getProfile(u);
            if (prof != null) winnerProfiles.add(prof);
        }
        for (UUID u : loserUuids) {
            PlayerProfile prof = plugin.getProfileManager().getProfile(u);
            if (prof != null) loserProfiles.add(prof);
        }

        // Calculate average ELO for both teams
        int winnerAvgElo = winnerProfiles.isEmpty() ? 800 :
                (int) winnerProfiles.stream().mapToInt(PlayerProfile::getElo).average().orElse(800.0);
        int loserAvgElo = loserProfiles.isEmpty() ? 800 :
                (int) loserProfiles.stream().mapToInt(PlayerProfile::getElo).average().orElse(800.0);

        int kFactor = plugin.getConfig().getInt("ranked.k-factor", 32);
        boolean dynamicK = plugin.getConfig().getBoolean("ranked.dynamic-k-factor", true);
        int totalWinnerMatches = (int) winnerProfiles.stream()
                .flatMap(p -> p.getAllStats().values().stream())
                .mapToInt(s -> s.getWins() + s.getLosses())
                .average().orElse(0.0);

        int eloDelta = (kFactor > 0 && !dynamicK)
                ? EloCalculator.calculateDelta(winnerAvgElo, loserAvgElo, kFactor)
                : EloCalculator.calculateDynamicDelta(winnerAvgElo, loserAvgElo, totalWinnerMatches);

        boolean requireRankupFee = plugin.getConfig().getBoolean("ranked.economy.require-rankup-fee", false);

        // Process winners
        for (PlayerProfile prof : winnerProfiles) {
            int newElo = prof.modifyElo(eloDelta);
            PlayerStats stats = prof.getOrCreateStats(kit.getId());
            stats.incrementWins();
            stats.incrementKills();

            int winStreak = prof.incrementStreak();
            prof.resetLossStreak();

            // Automatic Rank Progression
            Player p = Bukkit.getPlayer(prof.getUuid());
            plugin.getProfileManager().checkAndApplyAutoRankup(prof, p);

            if (p != null) {
                TextUtil.sendPrefixed(p, "<green>+" + eloDelta + " ELO <gray>(Rating: " + newElo + ")");
            }

            // Economy win reward with streak multiplier
            double baseWinReward = plugin.getConfig().getDouble("ranked.economy.win-reward", 50.0);
            double winReward = baseWinReward * prof.getStreakMultiplier();
            Economy economy = plugin.getEconomy();
            if (economy != null && winReward > 0) {
                economy.depositPlayer(Bukkit.getOfflinePlayer(prof.getUuid()), winReward);
            }

            prof.markDirty();
            plugin.getProfileManager().saveProfile(prof);
        }

        // Process losers
        for (PlayerProfile prof : loserProfiles) {
            int newElo = prof.modifyElo(-eloDelta);
            PlayerStats stats = prof.getOrCreateStats(kit.getId());
            stats.incrementLosses();
            stats.incrementDeaths();

            prof.resetStreak();
            prof.incrementLossStreak();

            Player p = Bukkit.getPlayer(prof.getUuid());
            if (p != null) {
                TextUtil.sendPrefixed(p, "<red>-" + eloDelta + " ELO <gray>(Rating: " + newElo + ")");
            }

            prof.markDirty();
            plugin.getProfileManager().saveProfile(prof);
        }

        broadcast("<gradient:#00FF88:#00AAFF><bold>TEAM RANKED VICTORY!</bold></gradient> <gold>" + winnerTeam + "</gold> (+<green>" + eloDelta + "</green> ELO) defeated <red>" + loserTeam + "</red> (-<red>" + eloDelta + "</red> ELO)!");

        // Record to audit database
        int durationSec = (int) ((endTime - startTime) / 1000);
        UUID leaderWinner = winnerUuids.isEmpty() ? null : winnerUuids.get(0);
        UUID leaderLoser = loserUuids.isEmpty() ? null : loserUuids.get(0);
        if (leaderWinner != null && leaderLoser != null) {
            plugin.getDatabaseManager().executeUpdateAsync(
                    "INSERT INTO apex_match_history (match_id, mode, kit_id, arena_id, winner_uuid, loser_uuid, elo_delta, stake_amount, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    matchId.toString(), "TEAM_RANKED", kit.getId(), arena.getTemplate().getId(),
                    leaderWinner.toString(), leaderLoser.toString(), eloDelta, 0.0, durationSec
            );
        }
    }
}
