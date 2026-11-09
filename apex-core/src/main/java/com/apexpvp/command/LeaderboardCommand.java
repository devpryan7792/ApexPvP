package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.leaderboard.LeaderboardManager;
import com.apexpvp.leaderboard.LeaderboardManager.LeaderboardEntry;
import com.apexpvp.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Handles /top and /leaderboard commands to view competitive rankings.
 */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public LeaderboardCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!CommandPermissions.require(sender, "apexpvp.top")) {
            return true;
        }

        String category = (args.length > 0) ? args[0].toLowerCase() : "elo";

        LeaderboardManager lm = plugin.getLeaderboardManager();
        if (lm == null) {
            sender.sendMessage(TextUtil.parse("<red>Leaderboard system is currently unavailable."));
            return true;
        }

        CompletableFuture<List<LeaderboardEntry>> future;
        String title;
        String unit;

        switch (category) {
            case "wins" -> {
                future = lm.getTopWins(10);
                title = "TOTAL WINS";
                unit = "Wins";
            }
            case "streak", "streaks" -> {
                future = lm.getTopStreak(10);
                title = "BEST WIN STREAKS";
                unit = "Streak";
            }
            case "elo" -> {
                future = lm.getTopElo(10);
                title = "GLOBAL ELO";
                unit = "ELO";
            }
            default -> {
                // Check if it's a valid kit ID
                Kit kit = plugin.getKitManager().getKit(category);
                if (kit != null) {
                    future = lm.getTopKitWins(kit.getId(), 10);
                    title = kit.getDisplayName().toUpperCase() + " WINS";
                    unit = "Wins";
                } else {
                    future = lm.getTopElo(10);
                    title = "GLOBAL ELO";
                    unit = "ELO";
                }
            }
        }

        sender.sendMessage(TextUtil.parse("<gray>Fetching leaderboard data..."));

        future.thenAccept(entries -> {
            sender.sendMessage(TextUtil.parse("<dark_gray>----------------- <gradient:#FFAA00:#FF5555><bold>TOP 10: " + title + "</bold></gradient> <dark_gray>-----------------"));
            if (entries.isEmpty()) {
                sender.sendMessage(TextUtil.parse("<yellow>No player records found yet."));
            } else {
                for (LeaderboardEntry entry : entries) {
                    String badge = entry.rank().getMiniMessageBadge();
                    String posColor = switch (entry.position()) {
                        case 1 -> "<gold><bold>#1</bold></gold>";
                        case 2 -> "<white><bold>#2</bold></white>";
                        case 3 -> "<color:#CD7F32><bold>#3</bold></color>";
                        default -> "<gray>#" + entry.position() + "</gray>";
                    };
                    sender.sendMessage(TextUtil.parse(posColor + " " + badge + " <yellow>" + entry.username() + "</yellow> <dark_gray>-</dark_gray> <aqua>" + entry.score() + "</aqua> <gray>" + unit + "</gray>"));
                }
            }
            sender.sendMessage(TextUtil.parse("<dark_gray>--------------------------------------------------------"));
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("elo", "wins", "streak"));
            for (Kit kit : plugin.getKitManager().getAllKits()) {
                completions.add(kit.getId());
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
