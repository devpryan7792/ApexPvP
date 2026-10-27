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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handles ranked matchmaking queue commands:
 * /queue <kit>
 * /leavequeue
 */
public class QueueCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public QueueCommand(@NotNull ApexPvPPlugin plugin) {
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

        if (label.equalsIgnoreCase("leavequeue")) {
            plugin.getQueueManager().leaveQueue(player);
            return true;
        }

        if (args.length == 0) {
            TextUtil.sendPrefixed(player, "<yellow>Usage: /queue <kit> OR /leavequeue");
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1 && !label.equalsIgnoreCase("leavequeue")) {
            List<String> completions = new ArrayList<>();
            for (Kit k : plugin.getKitManager().getAllKits()) {
                completions.add(k.getId());
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
