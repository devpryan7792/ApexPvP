package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.PlayerStats;
import com.apexpvp.profile.Rank;
import com.apexpvp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Displays player competitive statistics, rank progress, win streaks, and kit breakdown.
 * Usage: /stats [player]
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public StatsCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        OfflinePlayer target;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(TextUtil.parse("<red>Player '" + args[0] + "' has never joined the server."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(TextUtil.parse("<red>Console must specify a player: /stats <player>"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : args[0];

        PlayerProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile != null) {
            renderStatsCard(sender, profile);
        } else {
            // Load asynchronously if player is offline
            sender.sendMessage(TextUtil.parse("<gray>Loading profile for <gold>" + targetName + "</gold>..."));
            plugin.getProfileManager().loadProfile(uuid, targetName).thenAccept(loaded -> {
                renderStatsCard(sender, loaded);
            });
        }

        return true;
    }

    private void renderStatsCard(@NotNull CommandSender sender, @NotNull PlayerProfile profile) {
        Rank rank = profile.getRank();
        Rank nextRank = rank.getNextRank();
        int elo = profile.getElo();

        int totalWins = profile.getAllStats().values().stream().mapToInt(PlayerStats::getWins).sum();
        int totalLosses = profile.getAllStats().values().stream().mapToInt(PlayerStats::getLosses).sum();
        int totalKills = profile.getAllStats().values().stream().mapToInt(PlayerStats::getKills).sum();
        int totalDeaths = profile.getAllStats().values().stream().mapToInt(PlayerStats::getDeaths).sum();

        int totalMatches = totalWins + totalLosses;
        double winrate = (totalMatches > 0) ? ((double) totalWins / totalMatches * 100.0) : 0.0;
        double kdr = (totalDeaths > 0) ? ((double) totalKills / totalDeaths) : totalKills;

        String nextRankProgress;
        if (nextRank == null) {
            nextRankProgress = "<gold><bold>MAX RANK ACHIEVED!</bold></gold>";
        } else if (elo >= nextRank.getEloThreshold()) {
            nextRankProgress = "<green>Threshold Reached for " + nextRank.getDisplayName() + "! <yellow>(Use /rankup)</yellow></green>";
        } else {
            nextRankProgress = "<gray>Next: " + nextRank.getDisplayName() + " (<yellow>" + (nextRank.getEloThreshold() - elo) + "</yellow> ELO to go)</gray>";
        }

        double balance = profile.getCoins();
        if (plugin.getEconomy() != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(profile.getUuid());
            if (op.hasPlayedBefore() || op.isOnline()) {
                balance = plugin.getEconomy().getBalance(op);
            }
        }

        sender.sendMessage(TextUtil.parse("<dark_gray>================= <gradient:#00FFAA:#00AAFF><bold>" + profile.getUsername().toUpperCase() + "'S PROFILE</bold></gradient> <dark_gray>================="));
        sender.sendMessage(TextUtil.parse("<gray>Rank: " + rank.getMiniMessageBadge() + " " + rank.getMiniMessageDisplay() + "  <dark_gray>•</dark_gray>  <gray>Rating: <gold><bold>" + elo + "</bold> ELO</gold>"));
        sender.sendMessage(TextUtil.parse("<gray>Tier Progress: " + nextRankProgress));
        sender.sendMessage(TextUtil.parse("<gray>Balance: <yellow>" + String.format("%.2f", balance) + "</yellow> coins  <dark_gray>•</dark_gray>  <gray>Multiplier: <green>" + String.format("%.2fx", profile.getStreakMultiplier()) + "</green>"));
        sender.sendMessage(TextUtil.parse("<gray>Streak: <gold>" + profile.getCurrentStreak() + "</gold> current  <dark_gray>•</dark_gray>  <yellow>" + profile.getBestStreak() + "</yellow> best"));
        sender.sendMessage(TextUtil.parse("<gray>Record: <green>" + totalWins + "W</green> / <red>" + totalLosses + "L</red> (<aqua>" + String.format("%.1f", winrate) + "%</aqua>)  <dark_gray>•</dark_gray>  <gray>K/D: <green>" + totalKills + "</green>/<red>" + totalDeaths + "</red> (<yellow>" + String.format("%.2f", kdr) + "</yellow>)"));

        if (!profile.getAllStats().isEmpty()) {
            sender.sendMessage(TextUtil.parse("<gray>Kit Breakdown:"));
            for (PlayerStats stats : profile.getAllStats().values()) {
                sender.sendMessage(TextUtil.parse("  <dark_gray>»</dark_gray> <yellow>" + stats.getKitId().toUpperCase() + "</yellow>: <green>" + stats.getWins() + "W</green> / <red>" + stats.getLosses() + "L</red> <dark_gray>(</dark_gray><gray>Kills: " + stats.getKills() + "</gray><dark_gray>)</dark_gray>"));
            }
        }
        sender.sendMessage(TextUtil.parse("<dark_gray>========================================================"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
