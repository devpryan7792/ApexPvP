package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.match.MatchState;
import com.apexpvp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handles placing spectator bets on active duels:
 * /bet <player> <amount>
 */
public class BetCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public BetCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.parse("<red>This command can only be run by players."));
            return true;
        }

        if (!CommandPermissions.require(player, "apexpvp.bet")) {
            return true;
        }

        if (args.length < 2) {
            TextUtil.sendPrefixed(player, "<yellow>Usage: /bet <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            TextUtil.sendPrefixed(player, "<red>Player '" + args[0] + "' is not online.");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            TextUtil.sendPrefixed(player, "<red>You cannot bet on yourself!");
            return true;
        }

        Match match = plugin.getMatchManager().getMatch(target);
        if (match == null) {
            TextUtil.sendPrefixed(player, "<red>" + target.getName() + " is not currently in a match.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            TextUtil.sendPrefixed(player, "<red>Invalid bet amount: " + args[1]);
            return true;
        }

        if (!Double.isFinite(amount) || amount <= 0.0) {
            TextUtil.sendPrefixed(player, "<red>Bet amount must be a positive finite number.");
            return true;
        }

        if (plugin.getBettingManager() == null) {
            TextUtil.sendPrefixed(player, "<red>Betting system is currently unavailable.");
            return true;
        }

        plugin.getBettingManager().placeBet(player, match, target.getUniqueId(), amount);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Match match : plugin.getMatchManager().getActiveMatches()) {
                if (match.getState() == MatchState.PREPARING || match.getState() == MatchState.COUNTDOWN) {
                    for (MatchParticipant part : match.getParticipants()) {
                        Player p = Bukkit.getPlayer(part.getUuid());
                        if (p != null && p.isOnline()) {
                            names.add(p.getName());
                        }
                    }
                }
            }
            return names;
        } else if (args.length == 2) {
            return Arrays.asList("10", "25", "50", "100", "250", "500");
        }
        return Collections.emptyList();
    }
}
