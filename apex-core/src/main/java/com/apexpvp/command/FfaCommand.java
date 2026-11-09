package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.util.TextUtil;
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

/**
 * Command handler for Free-For-All (FFA) deathmatches.
 * Supports /ffa, /ffa queue <kit>, /ffa leave, and /ffa status.
 */
public class FfaCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public FfaCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.parse("<red>Only players can execute FFA commands."));
            return true;
        }

        if (!CommandPermissions.require(player, "apexpvp.ffa")) {
            return true;
        }

        if (args.length == 0) {
            // Always show usage. Starting a match requires an explicit
            // /ffa party or /team ffa so nobody is dropped into a fight by accident.
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "queue", "join" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /ffa queue <kit>");
                    return true;
                }
                Kit kit = plugin.getKitManager().getKit(args[1]);
                if (kit == null) {
                    TextUtil.sendPrefixed(player, "<red>Kit '<yellow>" + args[1] + "</yellow>' not found.");
                    return true;
                }
                plugin.getQueueManager().queueFfaPlayer(player, kit);
            }
            case "leave" -> {
                if (!plugin.getQueueManager().isInFfaQueue(player.getUniqueId())) {
                    TextUtil.sendPrefixed(player, "<red>You are not in an FFA queue.");
                    return true;
                }
                plugin.getQueueManager().leaveFfaQueue(player);
            }
            case "status" -> {
                TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>FFA QUEUE STATUS</bold></gradient>");
                int targetSize = plugin.getConfig().getInt("ffa.queue-size", 4);
                boolean any = false;
                for (Kit kit : plugin.getKitManager().getAllKits()) {
                    int count = plugin.getQueueManager().getFfaQueueCount(kit.getId());
                    if (count > 0) {
                        any = true;
                        player.sendMessage(TextUtil.parse("<gray>• <yellow>" + kit.getDisplayName() + "</yellow>: <gold>" + count + "/" + targetSize + " players</gold>"));
                    }
                }
                if (!any) {
                    player.sendMessage(TextUtil.parse("<gray>No players are currently waiting in FFA queues. Join one with <yellow>/ffa queue <kit></yellow>!"));
                }
            }
            case "party" -> {
                Kit kit = null;
                if (args.length > 1) {
                    kit = plugin.getKitManager().getKit(args[1]);
                }
                if (kit == null) {
                    kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                }
                if (kit == null) {
                    TextUtil.sendPrefixed(player, "<red>No kits configured.");
                    return true;
                }
                plugin.getPartyManager().startPartyFfa(player, kit);
            }
            default -> {
                // Check if user passed a kit name directly, e.g. /ffa spear
                Kit directKit = plugin.getKitManager().getKit(sub);
                if (directKit != null) {
                    // Direct kit always means "join the queue". Party scrimmages
                    // require the explicit /ffa party or /team ffa form.
                    plugin.getQueueManager().queueFfaPlayer(player, directKit);
                    return true;
                }
                sendHelp(player);
            }
        }

        return true;
    }

    private void sendHelp(@NotNull Player player) {
        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>FREE-FOR-ALL (FFA) COMMANDS</bold></gradient>");
        player.sendMessage(TextUtil.parse("<yellow>/ffa <kit> <gray>- Quick-queue for 4-player deathmatch"));
        player.sendMessage(TextUtil.parse("<yellow>/ffa queue <kit> <gray>- Join FFA matchmaking queue"));
        player.sendMessage(TextUtil.parse("<yellow>/ffa leave <gray>- Leave active FFA queue"));
        player.sendMessage(TextUtil.parse("<yellow>/ffa status <gray>- View active FFA queues"));
        player.sendMessage(TextUtil.parse("<yellow>/ffa party [kit] <gray>- Start an FFA scrimmage with your team (2-16p)"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("queue", "join", "leave", "status", "party"));
            for (Kit k : plugin.getKitManager().getAllKits()) {
                list.add(k.getId());
            }
            return list;
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("queue") || args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("party"))) {
            List<String> list = new ArrayList<>();
            for (Kit k : plugin.getKitManager().getAllKits()) {
                list.add(k.getId());
            }
            return list;
        }
        return Collections.emptyList();
    }
}
