package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.party.Party;
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
import java.util.UUID;

/**
 * Handles party creation, roster management, invites, and party split scrimmages.
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public PartyCommand(@NotNull ApexPvPPlugin plugin) {
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

        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            plugin.getPartyGUI().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> plugin.getPartyManager().createParty(player);
            case "leave" -> plugin.getPartyManager().leaveParty(player);
            case "disband" -> plugin.getPartyManager().disbandParty(player);
            case "invite" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
                    return true;
                }
                plugin.getPartyManager().invitePlayer(player, target);
            }
            case "accept" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /party accept <leader>");
                    return true;
                }
                Player leader = Bukkit.getPlayer(args[1]);
                if (leader == null || !leader.isOnline()) {
                    TextUtil.sendPrefixed(player, "<red>Leader not found or offline.");
                    return true;
                }
                plugin.getPartyManager().acceptInvite(player, leader);
            }
            case "split" -> {
                Kit kit = null;
                if (args.length > 1) {
                    kit = plugin.getKitManager().getKit(args[1]);
                }
                if (kit == null) {
                    kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                    if (kit == null) {
                        TextUtil.sendPrefixed(player, "<red>No kits configured.");
                        return true;
                    }
                }
                plugin.getPartyManager().splitParty(player, kit);
            }
            case "info" -> {
                Party party = plugin.getPartyManager().getParty(player);
                if (party == null) {
                    TextUtil.sendPrefixed(player, "<red>You are not in a party.");
                    return true;
                }
                Player leader = Bukkit.getPlayer(party.getLeader());
                String leaderName = leader != null ? leader.getName() : "Unknown";

                List<String> memberNames = new ArrayList<>();
                for (UUID uuid : party.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) memberNames.add(p.getName());
                }

                TextUtil.sendPrefixed(player, "<gradient:#FFAA00:#FF5555><bold>PARTY ROSTER</bold></gradient> (<yellow>" + party.getSize() + "/" + party.getMaxMembers() + "</yellow>)");
                player.sendMessage(TextUtil.parse("<gray>Leader: <gold>" + leaderName));
                player.sendMessage(TextUtil.parse("<gray>Members: <white>" + String.join(", ", memberNames)));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        TextUtil.sendPrefixed(player, "<gradient:#FFAA00:#FF5555><bold>PARTY COMMANDS</bold></gradient>");
        player.sendMessage(TextUtil.parse("<yellow>/party create <gray>- Create a new party"));
        player.sendMessage(TextUtil.parse("<yellow>/party invite <player> <gray>- Invite a player"));
        player.sendMessage(TextUtil.parse("<yellow>/party accept <leader> <gray>- Accept an invite"));
        player.sendMessage(TextUtil.parse("<yellow>/party leave <gray>- Leave current party"));
        player.sendMessage(TextUtil.parse("<yellow>/party disband <gray>- Disband the party"));
        player.sendMessage(TextUtil.parse("<yellow>/party split [kit] <gray>- Split party into 2 teams"));
        player.sendMessage(TextUtil.parse("<yellow>/party info <gray>- View party roster"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "leave", "disband", "split", "info");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) list.add(p.getName());
            }
            return list;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("split")) {
            List<String> list = new ArrayList<>();
            for (Kit k : plugin.getKitManager().getAllKits()) list.add(k.getId());
            return list;
        }
        return Collections.emptyList();
    }
}
