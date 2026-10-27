package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.bot.BotDifficulty;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.SimulatedDuelMatch;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Administrative master command for ApexPvP:
 * /apex simduel <kit> [difficulty] [stake]
 * /apex eco <give|take|set> <player> <amount>
 * /apex reload
 * /apex help
 */
public class ApexCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public ApexCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "simduel" -> {
                return handleSimDuel(sender, args);
            }
            case "eco" -> {
                return handleEco(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("apexpvp.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to run this command."));
                    return true;
                }
                plugin.reloadConfig();
                TextUtil.sendPrefixed(sender, "<green>ApexPvP configuration reloaded successfully.");
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleSimDuel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("apexpvp.simduel") && !sender.hasPermission("apexpvp.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to run simulated duels."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.parse("<red>This command can only be run by players in-game."));
            return true;
        }

        if (!plugin.isCitizensAvailable()) {
            TextUtil.sendPrefixed(player, "<red>Simulation engine is currently locked (Citizens 2 API is required).");
            return true;
        }

        if (plugin.getMatchManager().isInMatch(player)) {
            TextUtil.sendPrefixed(player, "<red>You are already in a match!");
            return true;
        }

        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            TextUtil.sendPrefixed(player, "<red>You must leave the matchmaking queue before starting a simulation.");
            return true;
        }

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

        String diffStr = (args.length > 2) ? args[2] : "MEDIUM";
        BotDifficulty difficulty = BotDifficulty.fromString(diffStr);

        double stake = 0.0;
        if (args.length > 3) {
            try {
                stake = Double.parseDouble(args[3]);
                if (!Double.isFinite(stake) || stake < 0.0) {
                    TextUtil.sendPrefixed(player, "<red>Stake amount must be a non-negative finite number.");
                    return true;
                }
            } catch (NumberFormatException e) {
                TextUtil.sendPrefixed(player, "<red>Invalid stake amount: " + args[3]);
                return true;
            }
        }

        // Validate stake balance
        if (stake > 0.0) {
            Economy eco = plugin.getEconomy();
            if (eco == null) {
                TextUtil.sendPrefixed(player, "<red>Economy system is unavailable. Staked duels cannot be started.");
                return true;
            }
            if (!eco.has(player, stake)) {
                TextUtil.sendPrefixed(player, "<red>You need at least <gold>" + stake + "</gold> coins to wager this amount!");
                return true;
            }
        }

        ArenaTemplate template = plugin.getArenaManager().getAllTemplates().stream().findFirst().orElse(null);
        if (template == null) {
            TextUtil.sendPrefixed(player, "<red>No arenas configured.");
            return true;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().getAvailableInstance(template);
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(player, "<yellow>No arenas are currently available. Auto-scaling instance, please try in a moment...");
            plugin.getArenaManager().requestScaling(template.getId());
            return true;
        }

        SimulatedDuelMatch match = new SimulatedDuelMatch(plugin, arenaInstance, kit, player, difficulty, stake);
        plugin.getMatchManager().registerMatch(match);
        match.start();

        TextUtil.sendPrefixed(player, "<green>Launched simulated competitive duel against <gold>Sim_"
                + difficulty.getDisplayName() + "</gold>! (Kit: " + kit.getDisplayName() + ", Stake: " + stake + ")");
        return true;
    }

    private boolean handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("apexpvp.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to manage economy."));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(TextUtil.parse("<yellow>Usage: /apex eco <give|take|set> <player> <amount>"));
            return true;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
            if (!Double.isFinite(amount) || amount < 0) {
                sender.sendMessage(TextUtil.parse("<red>Amount must be a non-negative finite number."));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.parse("<red>Invalid number: " + args[3]));
            return true;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            sender.sendMessage(TextUtil.parse("<red>Vault Economy is not enabled."));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Player onlineTarget = Bukkit.getPlayer(targetName);
        PlayerProfile profile = (onlineTarget != null) ? plugin.getProfileManager().getProfile(onlineTarget.getUniqueId()) : null;

        switch (action) {
            case "give" -> {
                economy.depositPlayer(target, amount);
                if (profile != null) {
                    profile.addCoins(amount);
                }
                TextUtil.sendPrefixed(sender, "<green>Deposited <gold>" + amount + " coins</gold> to <yellow>"
                        + (target.getName() != null ? target.getName() : targetName) + "</yellow>. New balance: <gold>"
                        + String.format("%.2f", economy.getBalance(target)) + "</gold>");
            }
            case "take" -> {
                economy.withdrawPlayer(target, amount);
                if (profile != null) {
                    profile.deductCoins(amount);
                }
                TextUtil.sendPrefixed(sender, "<green>Withdrew <gold>" + amount + " coins</gold> from <yellow>"
                        + (target.getName() != null ? target.getName() : targetName) + "</yellow>. New balance: <gold>"
                        + String.format("%.2f", economy.getBalance(target)) + "</gold>");
            }
            case "set" -> {
                double current = economy.getBalance(target);
                if (amount > current) {
                    economy.depositPlayer(target, amount - current);
                } else if (amount < current) {
                    economy.withdrawPlayer(target, current - amount);
                }
                if (profile != null) {
                    profile.setCoins(amount);
                }
                TextUtil.sendPrefixed(sender, "<green>Set balance of <yellow>"
                        + (target.getName() != null ? target.getName() : targetName) + "</yellow> to <gold>"
                        + String.format("%.2f", amount) + " coins</gold>.");
            }
            default -> {
                sender.sendMessage(TextUtil.parse("<yellow>Unknown action. Usage: /apex eco <give|take|set> <player> <amount>"));
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.parse("<dark_gray>----------------- <gradient:#00FFAA:#00AAFF><bold>APEXPVP COMMANDS</bold></gradient> <dark_gray>-----------------"));
        sender.sendMessage(TextUtil.parse("<gold>/apex simduel <kit> [difficulty] [stake] <gray>- Solo 2-player combat simulation"));
        sender.sendMessage(TextUtil.parse("<gold>/apex eco <give|take|set> <player> <amount> <gray>- Administrative economy management"));
        sender.sendMessage(TextUtil.parse("<gold>/apex reload <gray>- Reload configuration files"));
        sender.sendMessage(TextUtil.parse("<gold>/stats [player] <gray>- View player ranking profile"));
        sender.sendMessage(TextUtil.parse("<gold>/top [elo|wins|streak|<kit>] <gray>- Competitive leaderboards"));
        sender.sendMessage(TextUtil.parse("<gold>/bet <player> <amount> <gray>- Wager on active duel combatants"));
        sender.sendMessage(TextUtil.parse("<dark_gray>--------------------------------------------------"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("simduel", "eco", "reload", "help"));
            return list;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("simduel")) {
            if (args.length == 2) {
                List<String> kits = new ArrayList<>();
                for (Kit k : plugin.getKitManager().getAllKits()) kits.add(k.getId());
                return kits;
            } else if (args.length == 3) {
                return Arrays.asList("EASY", "MEDIUM", "HARD", "HACKER");
            } else if (args.length == 4) {
                return Arrays.asList("0", "50", "100", "250", "500");
            }
        } else if (sub.equals("eco")) {
            if (args.length == 2) {
                return Arrays.asList("give", "take", "set");
            } else if (args.length == 3) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return players;
            } else if (args.length == 4) {
                return Arrays.asList("100", "500", "1000", "5000");
            }
        }

        return Collections.emptyList();
    }
}
