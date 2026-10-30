package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Command handling 1v1 duel challenges, acceptance, and declination:
 * Usage: /duel <player> [kit] [stake]
 *        /duel accept <player>
 *        /duel decline <player>
 */
public class DuelCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public DuelCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.parse("<red>This command can only be executed by players."));
            return true;
        }

        if (args.length == 0) {
            TextUtil.sendPrefixed(player, "<yellow>Usage: /duel <player> [kit] [stake] OR /duel <accept|decline> <player>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // 0. Preview Challenge
        if (sub.equals("preview")) {
            if (args.length < 2) {
                TextUtil.sendPrefixed(player, "<red>Usage: /duel preview <player>");
                return true;
            }
            Player challenger = Bukkit.getPlayer(args[1]);
            if (challenger == null || !challenger.isOnline()) {
                TextUtil.sendPrefixed(player, "<red>Player is not online.");
                return true;
            }
            com.apexpvp.duel.DuelRequest req = plugin.getDuelManager().getPendingRequest(player, challenger);
            if (req == null) {
                TextUtil.sendPrefixed(player, "<red>No pending duel challenge from <gold>" + challenger.getName() + "</gold>.");
                return true;
            }
            plugin.getDuelPreviewGUI().open(player, challenger, req);
            return true;
        }

        // 1. Accept Challenge
        if (sub.equals("accept")) {
            if (args.length < 2) {
                TextUtil.sendPrefixed(player, "<red>Usage: /duel accept <player>");
                return true;
            }
            Player challenger = Bukkit.getPlayer(args[1]);
            if (challenger == null || !challenger.isOnline()) {
                TextUtil.sendPrefixed(player, "<red>Player is not online.");
                return true;
            }
            plugin.getDuelManager().acceptChallenge(player, challenger);
            return true;
        }

        // 2. Decline Challenge
        if (sub.equals("decline")) {
            if (args.length < 2) {
                TextUtil.sendPrefixed(player, "<red>Usage: /duel decline <player>");
                return true;
            }
            Player challenger = Bukkit.getPlayer(args[1]);
            if (challenger == null || !challenger.isOnline()) {
                TextUtil.sendPrefixed(player, "<red>Player is not online.");
                return true;
            }
            plugin.getDuelManager().declineChallenge(player, challenger);
            return true;
        }

        // 3. Send Challenge: /duel <target> [kit] [stake]
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
            return true;
        }

        if (target.equals(player)) {
            TextUtil.sendPrefixed(player, "<red>You cannot duel yourself!");
            return true;
        }

        if (plugin.getMatchManager().isInMatch(player) || plugin.getMatchManager().isInMatch(target)) {
            TextUtil.sendPrefixed(player, "<red>Either you or the target is currently in a match.");
            return true;
        }

        // When only target is provided, open interactive GUI for kit selection
        if (args.length == 1) {
            plugin.getDuelKitGUI().open(player, target, 0.0);
            return true;
        }

        // If args[1] is a numeric stake: /duel <target> <stake>
        double parsedStake = -1.0;
        try {
            parsedStake = Double.parseDouble(args[1]);
        } catch (NumberFormatException ignored) {}

        if (parsedStake >= 0.0) {
            plugin.getDuelKitGUI().open(player, target, parsedStake);
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            TextUtil.sendPrefixed(player, "<red>Kit '<yellow>" + args[1] + "</yellow>' not found. Choose a kit in the GUI or check <yellow>/kit list</yellow>.");
            return true;
        }

        double stake = 0.0;
        if (args.length > 2) {
            try {
                stake = Double.parseDouble(args[2]);
                if (!Double.isFinite(stake) || stake < 0.0) {
                    TextUtil.sendPrefixed(player, "<red>Stake amount must be a non-negative finite number.");
                    return true;
                }
            } catch (NumberFormatException ignored) {
                TextUtil.sendPrefixed(player, "<red>Invalid stake amount.");
                return true;
            }
        }

        plugin.getDuelArenaGUI().open(player, target, kit, stake);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("accept");
            completions.add("decline");
            completions.add("preview");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("accept") && !args[0].equalsIgnoreCase("decline")) {
            for (Kit k : plugin.getKitManager().getAllKits()) {
                completions.add(k.getId());
            }
        } else if (args.length == 3) {
            completions.add("0");
            completions.add("100");
            completions.add("500");
            completions.add("1000");
        }
        return completions;
    }
}
