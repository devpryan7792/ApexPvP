package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.ui.gui.KitGUI;
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
 * Command handling in-game kit selection GUI and administrative kit configuration.
 * /kits
 * /kit create <id> [displayName]
 * /kit delete <id>
 * /kit give <player> <id>
 * /kit list
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;
    private final KitGUI kitGUI;

    public KitCommand(@NotNull ApexPvPPlugin plugin, @NotNull KitGUI kitGUI) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.kitGUI = Objects.requireNonNull(kitGUI, "kitGUI cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (label.equalsIgnoreCase("kiteditor")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.parse("<red>Only players can edit kit layouts."));
                return true;
            }
            if (args.length >= 1) {
                Kit kit = plugin.getKitManager().getKit(args[0]);
                if (kit != null) {
                    plugin.getKitEditorGUI().open(player, kit);
                    return true;
                } else {
                    TextUtil.sendPrefixed(player, "<red>Kit '" + args[0] + "' not found. Opening kit selection...");
                }
            }
            kitGUI.open(player, true);
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.parse("<red>Console must use /kit list."));
                return true;
            }
            kitGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                sender.sendMessage(TextUtil.parse("<dark_gray>================= <gradient:#00FFAA:#00AAFF><bold>AVAILABLE KITS</bold></gradient> <dark_gray>================="));
                for (Kit kit : plugin.getKitManager().getAllKits()) {
                    sender.sendMessage(TextUtil.parse(" <dark_gray>»</dark_gray> <yellow>" + kit.getId() + "</yellow>: "
                            + kit.getDisplayName() + " <dark_gray>(</dark_gray><gray>Icon: " + kit.getIcon().getType().name() + "</gray><dark_gray>)</dark_gray>"));
                }
                sender.sendMessage(TextUtil.parse("<dark_gray>=================================================="));
                return true;
            }
            case "editor", "edit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can edit kit layouts."));
                    return true;
                }
                if (args.length < 2) {
                    kitGUI.open(player, true);
                    return true;
                }
                String id = args[1].toLowerCase();
                Kit kit = plugin.getKitManager().getKit(id);
                if (kit == null) {
                    TextUtil.sendPrefixed(player, "<red>Kit '" + id + "' not found.");
                    return true;
                }
                plugin.getKitEditorGUI().open(player, kit);
                return true;
            }
            case "get" -> {
                if (!sender.hasPermission("apexpvp.kit.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to inspect/get kits."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can use /kit get."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /kit get <kit>");
                    return true;
                }
                String id = args[1].toLowerCase();
                Kit kit = plugin.getKitManager().getKit(id);
                if (kit == null) {
                    TextUtil.sendPrefixed(player, "<red>Kit '" + id + "' not found.");
                    return true;
                }
                kit.apply(player, null);
                TextUtil.sendPrefixed(player, "<green>Equipped kit <yellow>" + kit.getId() + "</yellow> into your inventory.");
                TextUtil.sendPrefixed(player, "<gray>Tip: Modify items in your inventory and run <yellow>/kit set " + kit.getId() + "</yellow> to update it!");
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission("apexpvp.kit.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to modify kits."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can set kits from inventory."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /kit set <kit>");
                    return true;
                }
                String id = args[1].toLowerCase();
                boolean updated = plugin.getKitManager().updateKitFromPlayer(id, player);
                if (updated) {
                    TextUtil.sendPrefixed(player, "<green>Successfully overwritten kit <yellow>" + id + "</yellow> with your current inventory and armor (saved to kits.yml)!");
                } else {
                    TextUtil.sendPrefixed(player, "<red>Kit '" + id + "' does not exist. Use /kit create " + id + " to create a new kit first.");
                }
                return true;
            }
            case "create" -> {
                if (!sender.hasPermission("apexpvp.kit.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to create kits."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can create kits from inventory."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /kit create <id> [displayName]");
                    return true;
                }
                String id = args[1].toLowerCase();
                String displayName = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : "<gradient:#FFAA00:#FF5555>" + id.substring(0, 1).toUpperCase() + id.substring(1) + "</gradient>";

                boolean success = plugin.getKitManager().createKitFromPlayer(id, displayName, player);
                if (success) {
                    TextUtil.sendPrefixed(player, "<green>Kit <yellow>" + id + "</yellow> created from your current inventory and saved to kits.yml!");
                }
                return true;
            }
            case "delete" -> {
                if (!sender.hasPermission("apexpvp.kit.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to delete kits."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(sender, "<red>Usage: /kit delete <id>");
                    return true;
                }
                String id = args[1].toLowerCase();
                boolean deleted = plugin.getKitManager().deleteKitFromConfig(id);
                if (deleted) {
                    TextUtil.sendPrefixed(sender, "<green>Kit <yellow>" + id + "</yellow> successfully deleted from memory and kits.yml.");
                } else {
                    TextUtil.sendPrefixed(sender, "<red>Kit '" + id + "' not found.");
                }
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("apexpvp.kit.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to give kits."));
                    return true;
                }
                if (args.length < 3) {
                    TextUtil.sendPrefixed(sender, "<red>Usage: /kit give <player> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    TextUtil.sendPrefixed(sender, "<red>Player '" + args[1] + "' is not online.");
                    return true;
                }
                Kit kit = plugin.getKitManager().getKit(args[2]);
                if (kit == null) {
                    TextUtil.sendPrefixed(sender, "<red>Kit '" + args[2] + "' not found.");
                    return true;
                }
                kit.apply(target, null);
                TextUtil.sendPrefixed(sender, "<green>Equipped kit <yellow>" + kit.getId() + "</yellow> to " + target.getName() + ".");
                TextUtil.sendPrefixed(target, "<green>Equipped kit <yellow>" + kit.getId() + "</yellow>.");
                return true;
            }
            default -> {
                if (sender instanceof Player player) {
                    kitGUI.open(player);
                } else {
                    TextUtil.sendPrefixed(sender, "<red>Usage: /kit <list|editor|create|set|get|delete|give>");
                }
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (label.equalsIgnoreCase("kiteditor")) {
            if (args.length == 1) {
                List<String> kits = new ArrayList<>();
                for (Kit k : plugin.getKitManager().getAllKits()) kits.add(k.getId());
                return kits;
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("list");
            list.add("editor");
            if (sender.hasPermission("apexpvp.kit.admin")) {
                list.add("create");
                list.add("delete");
                list.add("give");
                list.add("set");
                list.add("get");
            }
            return list;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("delete") || sub.equals("get") || sub.equals("set") || sub.equals("editor") || sub.equals("edit")) {
            if (args.length == 2) {
                List<String> kits = new ArrayList<>();
                for (Kit k : plugin.getKitManager().getAllKits()) kits.add(k.getId());
                return kits;
            }
        } else if (sub.equals("give")) {
            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return players;
            } else if (args.length == 3) {
                List<String> kits = new ArrayList<>();
                for (Kit k : plugin.getKitManager().getAllKits()) kits.add(k.getId());
                return kits;
            }
        }

        return Collections.emptyList();
    }
}
