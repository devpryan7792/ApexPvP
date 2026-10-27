package com.apexpvp.integration.papi;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * PlaceholderAPI expansion for ApexPvP providing scoreboards, tablists, and chat tags.
 * Placeholders:
 *   %apexpvp_rank%
 *   %apexpvp_rank_badge%
 *   %apexpvp_elo%
 *   %apexpvp_coins%
 *   %apexpvp_wins%
 *   %apexpvp_losses%
 *   %apexpvp_kdr%
 *   %apexpvp_active_matches%
 */
public class ApexExpansion extends PlaceholderExpansion {

    private final ApexPvPPlugin plugin;

    public ApexExpansion(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "apexpvp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ApexPvP Team";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("active_matches")) {
            return String.valueOf(plugin.getMatchManager().getActiveMatchCount());
        }
        if (params.equalsIgnoreCase("available_arenas")) {
            return String.valueOf(plugin.getArenaManager().getAvailableInstanceCount());
        }
        if (params.equalsIgnoreCase("total_arenas")) {
            return String.valueOf(plugin.getArenaManager().getTotalInstanceCount());
        }

        if (player == null) {
            return "";
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) {
            return "...";
        }

        String lower = params.toLowerCase();
        return switch (lower) {
            case "rank" -> profile.getRank().getDisplayName();
            case "rank_badge" -> profile.getRank().getMiniMessageBadge();
            case "tier" -> profile.getRank().getTier().name();
            case "division" -> profile.getRank().getSubRank().name();
            case "elo" -> String.valueOf(profile.getElo());
            case "coins" -> {
                if (plugin.getEconomy() != null) {
                    yield String.format("%.2f", plugin.getEconomy().getBalance(player));
                }
                yield String.format("%.2f", profile.getCoins());
            }
            case "wins" -> {
                int totalWins = profile.getAllStats().values().stream().mapToInt(PlayerStats::getWins).sum();
                yield String.valueOf(totalWins);
            }
            case "losses" -> {
                int totalLosses = profile.getAllStats().values().stream().mapToInt(PlayerStats::getLosses).sum();
                yield String.valueOf(totalLosses);
            }
            case "kdr" -> {
                int kills = profile.getAllStats().values().stream().mapToInt(PlayerStats::getKills).sum();
                int deaths = profile.getAllStats().values().stream().mapToInt(PlayerStats::getDeaths).sum();
                if (deaths == 0) yield String.valueOf(kills);
                yield String.format("%.2f", (double) kills / deaths);
            }
            case "streak", "current_streak", "win_streak" -> String.valueOf(profile.getCurrentStreak());
            case "best_streak", "best_win_streak" -> String.valueOf(profile.getBestStreak());
            case "loss_streak", "current_loss_streak" -> String.valueOf(profile.getCurrentLossStreak());
            case "worst_loss_streak" -> String.valueOf(profile.getWorstLossStreak());
            case "streak_multiplier" -> String.format("%.2f", profile.getStreakMultiplier());
            default -> null;
        };
    }
}
