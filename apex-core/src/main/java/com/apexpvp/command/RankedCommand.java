package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.queue.QueueTicket;
import com.apexpvp.util.TextUtil;
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
 * Unified Competitive Ranked Command:
 * Usage:
 *   /ranked                          - Open competitive hub GUI
 *   /ranked queue <kit> (or /ranked join) - Directly queue for a kit
 *   /ranked leave                    - Exit the matchmaking queue
 *   /ranked status                   - Check current queue search range
 */
public class RankedCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public RankedCommand(@NotNull ApexPvPPlugin plugin) {
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

        // Direct alias support for /leavequeue
        if (label.equalsIgnoreCase("leavequeue")) {
            plugin.getQueueManager().leaveQueue(player);
            return true;
        }

        // Direct alias support for /queue <kit>
        if (label.equalsIgnoreCase("queue")) {
            if (args.length == 0) {
                plugin.getRankedMenuGUI().open(player);
                return true;
            }
            String kitName = args[0].toLowerCase();
            Kit kit = plugin.getKitManager().getKit(kitName);
            if (kit == null) {
                TextUtil.sendPrefixed(player, "<red>Kit '<gold>" + kitName + "</gold>' not found.");
                return true;
            }
            plugin.getQueueManager().queuePlayer(player, kit);
            return true;
        }

        // /ranked [no args] or /ranked menu -> open GUI
        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            plugin.getRankedMenuGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "queue", "join" -> {
                if (args.length < 2) {
                    plugin.getRankedMenuGUI().open(player);
                    return true;
                }
                String kitName = args[1].toLowerCase();
                Kit kit = plugin.getKitManager().getKit(kitName);
                if (kit == null) {
                    TextUtil.sendPrefixed(player, "<red>Kit '<gold>" + kitName + "</gold>' not found.");
                    return true;
                }
                plugin.getQueueManager().queuePlayer(player, kit);
                return true;
            }

            case "leave", "leavequeue", "cancel", "quit" -> {
                plugin.getQueueManager().leaveQueue(player);
                return true;
            }

            case "status" -> {
                QueueTicket ticket = plugin.getQueueManager().getTicket(player.getUniqueId());
                if (ticket != null) {
                    long elapsedSec = (System.currentTimeMillis() - ticket.getJoinTimestamp()) / 1000L;
                    TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>QUEUE STATUS:</bold></gradient> Searching for <gold>" +
                            ticket.getKitId() + "</gold> match (±" + ticket.getCurrentSearchRange() + " ELO | " + elapsedSec + "s elapsed).");
                } else {
                    TextUtil.sendPrefixed(player, "<yellow>You are not currently in a matchmaking queue. Use <gold>/ranked</gold> to join!");
                }
                return true;
            }

            default -> {
                // If user typed a kit directly e.g. /ranked spear
                Kit kit = plugin.getKitManager().getKit(sub);
                if (kit != null) {
                    plugin.getQueueManager().queuePlayer(player, kit);
                    return true;
                }

                TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>ApexPvP Ranked Matchmaking</bold></gradient>\n" +
                        "<yellow>/ranked</yellow> <gray>- Open competitive menu</gray>\n" +
                        "<yellow>/ranked queue <kit></yellow> <gray>- Join matchmaking queue</gray>\n" +
                        "<yellow>/ranked leave</yellow> <gray>- Leave matchmaking queue</gray>\n" +
                        "<yellow>/ranked status</yellow> <gray>- View your queue status</gray>");
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (label.equalsIgnoreCase("leavequeue")) {
            return Collections.emptyList();
        }

        if (label.equalsIgnoreCase("queue")) {
            if (args.length == 1) {
                List<String> kits = new ArrayList<>();
                for (Kit k : plugin.getKitManager().getAllKits()) {
                    kits.add(k.getId());
                }
                return kits;
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("menu", "queue", "join", "leave", "status"));
            for (Kit k : plugin.getKitManager().getAllKits()) {
                list.add(k.getId());
            }
            return list;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("join"))) {
            List<String> kits = new ArrayList<>();
            for (Kit k : plugin.getKitManager().getAllKits()) {
                kits.add(k.getId());
            }
            return kits;
        }

        return Collections.emptyList();
    }
}
