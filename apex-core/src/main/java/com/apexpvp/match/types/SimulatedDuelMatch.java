package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.bot.BotAIController;
import com.apexpvp.bot.BotDifficulty;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
import com.apexpvp.profile.Rank;
import com.apexpvp.util.EloCalculator;
import com.apexpvp.util.TextUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 2-Player Simulated Competitive Duel against an autonomous Citizens combat bot.
 * Exercises authentic duel flow, ELO rating updates, win streaks, escrow staking, and arena rollback.
 */
public class SimulatedDuelMatch extends Match {

    private final UUID playerUuid;
    private final BotDifficulty difficulty;
    private final double stakeAmount;
    private NPC practiceBot;
    private BotAIController botAI;

    public SimulatedDuelMatch(@NotNull ApexPvPPlugin plugin,
                              @NotNull ArenaInstance arena,
                              @NotNull Kit kit,
                              @NotNull Player player,
                              @NotNull BotDifficulty difficulty,
                              double stakeAmount) {
        super(plugin, arena, kit);
        this.playerUuid = player.getUniqueId();
        this.difficulty = difficulty;
        this.stakeAmount = Math.max(0.0, stakeAmount);

        addParticipant(player, 0); // Player on Team 0
    }

    @Override
    public void start() {
        // Spawn Citizens Practice Bot on Team 1 (Spawn B)
        if (plugin.isCitizensAvailable()) {
            this.practiceBot = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Sim_" + difficulty.getDisplayName());
            practiceBot.setProtected(false);
            practiceBot.spawn(arena.getSpawnB());

            if (practiceBot.getEntity() instanceof Player botPlayer) {
                botPlayer.setGameMode(GameMode.SURVIVAL);
                kit.apply(botPlayer, null);
                addParticipant(botPlayer, 1);
                plugin.getMatchManager().registerParticipant(botPlayer.getUniqueId(), this);
                plugin.getMatchManager().registerParticipant(practiceBot.getUniqueId(), this);
            }
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (stakeAmount > 0 && player != null && practiceBot != null) {
            boolean locked = plugin.getEscrowManager().lockSimulatedStake(matchId, player, practiceBot.getUniqueId(), stakeAmount);
            if (!locked) {
                broadcast("<red>Failed to lock stake into escrow. Match aborted.");
                cleanup();
                return;
            }
        }

        super.start();
    }

    @Override
    protected void enterActive() {
        super.enterActive();

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && practiceBot != null && practiceBot.isSpawned()) {
            this.botAI = new BotAIController(plugin, this, practiceBot, player, difficulty);
            this.botAI.start();
        }
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        Player player = Bukkit.getPlayer(playerUuid);
        PlayerProfile profile = plugin.getProfileManager().getProfile(playerUuid);

        int botElo = switch (difficulty) {
            case EASY -> 800;
            case MEDIUM -> 1200;
            case HARD -> 1600;
            case HACKER -> 2200;
        };

        if (winningTeamIndex == 0) {
            // Player won!
            broadcast("<gradient:#00FFAA:#00AAFF><bold>SIMULATED DUEL WON!</bold></gradient> You defeated <gold>Sim_"
                    + difficulty.getDisplayName() + "</gold>!");

            if (profile != null) {
                int oldElo = profile.getElo();
                int totalMatches = profile.getAllStats().values().stream()
                        .mapToInt(s -> s.getWins() + s.getLosses()).sum();
                int eloDelta = EloCalculator.calculateDynamicDelta(oldElo, botElo, totalMatches);
                int newElo = profile.modifyElo(eloDelta);

                PlayerStats stats = profile.getOrCreateStats(kit.getId());
                stats.incrementWins();
                stats.incrementKills();

                int streak = profile.incrementStreak();
                if (streak >= 3) {
                    broadcast("<gold>" + profile.getUsername() + "</gold> is on a <gradient:#FFAA00:#FF5555><bold>"
                            + streak + "-MATCH WIN STREAK!</bold></gradient>");
                }

                Rank newRank = Rank.fromElo(newElo);
                if (newRank.ordinal() > profile.getRank().ordinal()) {
                    boolean requireRankupFee = plugin.getConfig().getBoolean("ranked.economy.require-rankup-fee", true);
                    if (!requireRankupFee) {
                        profile.setRank(newRank);
                        broadcast("<gradient:#00FFAA:#00AAFF><bold>RANK UP!</bold></gradient> Reached "
                                + newRank.getMiniMessageDisplay() + "!");
                    } else if (player != null) {
                        player.sendMessage(TextUtil.parse("<green>You unlocked the threshold for " + newRank.getDisplayName() + "! Use /rankup."));
                    }
                }

                profile.markDirty();

                if (player != null) {
                    TextUtil.sendPrefixed(player, "<green>+" + eloDelta + " ELO <gray>(Rating: " + newElo + ")");
                }
            }

            // Distribute escrow stake payout if wagered
            if (stakeAmount > 0 && player != null) {
                double taxPercent = plugin.getConfig().getDouble("duels.betting.server-tax-percent", 5.0);
                plugin.getEscrowManager().distributePayout(matchId, player, taxPercent);
                double totalPot = stakeAmount * 2.0;
                double taxDeduction = totalPot * (taxPercent / 100.0);
                double netWinnings = totalPot - taxDeduction;
                broadcast("<gold>" + player.getName() + "</gold> won the wager of <yellow>" + netWinnings + "</yellow> coins!");
            }

        } else if (winningTeamIndex == 1) {
            // Bot won!
            broadcast("<gradient:#FF5555:#AA0000><bold>SIMULATED DUEL LOST!</bold></gradient> Defeated by <gold>Sim_"
                    + difficulty.getDisplayName() + "</gold>.");

            if (profile != null) {
                int oldElo = profile.getElo();
                int totalMatches = profile.getAllStats().values().stream()
                        .mapToInt(s -> s.getWins() + s.getLosses()).sum();
                int eloDelta = EloCalculator.calculateDynamicDelta(oldElo, botElo, totalMatches);
                int newElo = profile.modifyElo(-eloDelta);

                PlayerStats stats = profile.getOrCreateStats(kit.getId());
                stats.incrementLosses();
                stats.incrementDeaths();

                profile.resetStreak();
                profile.markDirty();

                if (player != null) {
                    TextUtil.sendPrefixed(player, "<red>-" + eloDelta + " ELO <gray>(Rating: " + newElo + ")");
                }
            }

            if (stakeAmount > 0) {
                plugin.getEscrowManager().consumeEscrow(matchId);
            }
        } else {
            // Draw
            broadcast("<yellow>The simulated duel ended in a draw! Any staked funds have been refunded.");
            if (stakeAmount > 0) {
                plugin.getEscrowManager().refund(matchId);
            }
        }

        // Record to audit database
        int durationSec = (int) ((endTime - startTime) / 1000);
        plugin.getDatabaseManager().executeUpdateAsync(
            "INSERT INTO apex_match_history (match_id, mode, kit_id, arena_id, winner_uuid, loser_uuid, elo_delta, stake_amount, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            matchId.toString(), "SIM_DUEL", kit.getId(), arena.getTemplate().getId(),
            (winningTeamIndex == 0 ? playerUuid.toString() : (practiceBot != null ? practiceBot.getUniqueId().toString() : "BOT")),
            (winningTeamIndex == 1 ? playerUuid.toString() : (practiceBot != null ? practiceBot.getUniqueId().toString() : "BOT")),
            0, stakeAmount, durationSec
        );
    }

    @Override
    public void cleanup() {
        if (botAI != null) {
            botAI.stop();
            botAI = null;
        }

        if (practiceBot != null) {
            if (practiceBot.getEntity() != null) {
                plugin.getMatchManager().unregisterParticipant(practiceBot.getEntity().getUniqueId());
            }
            plugin.getMatchManager().unregisterParticipant(practiceBot.getUniqueId());
            practiceBot.destroy();
            practiceBot = null;
        }

        if (stakeAmount > 0) {
            plugin.getEscrowManager().refund(matchId);
        }

        super.cleanup();
    }
}
