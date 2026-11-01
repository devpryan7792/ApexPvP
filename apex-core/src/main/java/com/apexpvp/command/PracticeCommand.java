package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.bot.BotDifficulty;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.BotMatch;
import com.apexpvp.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
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
 * Handles launching PvE practice bot matches:
 * /practice <kit> [difficulty]
 */
public class PracticeCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public PracticeCommand(@NotNull ApexPvPPlugin plugin) {
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

        if (!plugin.isCitizensAvailable()) {
            TextUtil.sendPrefixed(player, "<red>Practice Bot combat is currently unavailable (Citizens 2 API is required).");
            return true;
        }

        if (plugin.getMatchManager().isInMatch(player)) {
            TextUtil.sendPrefixed(player, "<red>You are already in a match!");
            return true;
        }

        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            TextUtil.sendPrefixed(player, "<red>You must leave the matchmaking queue before starting practice.");
            return true;
        }

        Kit kit = null;
        if (args.length > 0) {
            kit = plugin.getKitManager().getKit(args[0]);
        }
        if (kit == null) {
            kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
            if (kit == null) {
                TextUtil.sendPrefixed(player, "<red>No kits configured.");
                return true;
            }
        }

        String diffStr = (args.length > 1) ? args[1] : "MEDIUM";
        BotDifficulty difficulty = BotDifficulty.fromString(diffStr);

        // Economy Pre-check
        boolean economyEnabled = plugin.getConfig().getBoolean("practice.economy.enabled", true);
        double fee = plugin.getConfig().getDouble("practice.economy.flat-fee", 10.0);
        if (economyEnabled && fee > 0.0) {
            Economy economy = plugin.getEconomy();
            if (economy != null && !economy.has(player, fee)) {
                TextUtil.sendPrefixed(player, "<red>You need at least <gold>" + fee + "</gold> coins to enter Practice mode!");
                return true;
            }
        }

        // Check arena availability first
        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kit.getId());
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(player, "<yellow>No arenas are currently available for this kit. Auto-scaling instance, please try in a moment...");
            return true;
        }

        // Deduct practice fee only after arena is secured
        if (economyEnabled && fee > 0.0) {
            Economy economy = plugin.getEconomy();
            if (economy != null) {
                var res = economy.withdrawPlayer(player, fee);
                if (!res.transactionSuccess()) {
                    TextUtil.sendPrefixed(player, "<red>Failed to deduct practice fee.");
                    return true;
                }
            }
        }

        BotMatch match = new BotMatch(plugin, arenaInstance, kit, player, difficulty.name());
        plugin.getMatchManager().registerMatch(match);
        match.start();

        TextUtil.sendPrefixed(player, "<green>Practice match against <gold>" + difficulty.getDisplayName() + "</gold> bot started!");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Kit k : plugin.getKitManager().getAllKits()) list.add(k.getId());
            return list;
        } else if (args.length == 2) {
            return Arrays.asList("EASY", "MEDIUM", "HARD", "HACKER");
        }
        return Collections.emptyList();
    }
}
