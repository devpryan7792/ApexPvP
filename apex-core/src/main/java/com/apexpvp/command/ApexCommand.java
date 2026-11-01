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
            case "arena" -> {
                return handleArena(sender, args);
            }
            case "editor" -> {
                if (!sender.hasPermission("apexpvp.admin")) {
                    sender.sendMessage(TextUtil.parse("<red>You do not have permission to access the admin editor."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only players can open the visual editor."));
                    return true;
                }
                plugin.getAdminEditorGUI().open(player);
                return true;
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

        ArenaInstance arenaInstance = plugin.getArenaManager().findAvailableInstanceForKit(kit.getId());
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(player, "<yellow>No arenas are currently available for this kit. Auto-scaling instance, please try in a moment...");
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

    private boolean handleArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("apexpvp.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You do not have permission to manage arenas."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.parse("<dark_gray>----------------- <gradient:#00FFAA:#00AAFF><bold>ARENA MANAGEMENT</bold></gradient> <dark_gray>-----------------"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena list <gray>- List all arena templates and instances"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena create <id> [displayName] <gray>- Create new arena template"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena dupe <id> <gray>- Dupe/allocate dynamic grid instance"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena tp <id> [instanceIndex] <gray>- Teleport to arena instance"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena setspawnA <id> <gray>- Set Spawn A location"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena setspawnB <id> <gray>- Set Spawn B location"));
            sender.sendMessage(TextUtil.parse("<gold>/apex arena setspec <id> <gray>- Set Spectator spawn location"));
            sender.sendMessage(TextUtil.parse("<dark_gray>--------------------------------------------------"));
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "list" -> {
                sender.sendMessage(TextUtil.parse("<dark_gray>================= <gradient:#00FFAA:#00AAFF><bold>ARENA TEMPLATES</bold></gradient> <dark_gray>================="));
                for (ArenaTemplate template : plugin.getArenaManager().getAllTemplates()) {
                    List<ArenaInstance> instList = plugin.getArenaManager().getInstances(template.getId());
                    long available = instList.stream().filter(i -> i.getState() == com.apexpvp.arena.ArenaState.AVAILABLE).count();
                    sender.sendMessage(TextUtil.parse(" <dark_gray>»</dark_gray> <yellow>" + template.getId() + "</yellow> (TID: " + template.getTemplateId() + "): "
                            + template.getDisplayName() + " <gray>• Instances: <green>" + available + "</green>/<aqua>" + instList.size() + " available</gray>"));
                }
                sender.sendMessage(TextUtil.parse("<gray>Total instances across grid: <green>" + plugin.getArenaManager().getTotalInstanceCount() + "</green>"));
                sender.sendMessage(TextUtil.parse("<dark_gray>=================================================="));
                return true;
            }
            case "create" -> {
                if (args.length < 3) {
                    TextUtil.sendPrefixed(sender, "<red>Usage: /apex arena create <id> [displayName]");
                    return true;
                }
                String id = args[2].toLowerCase();
                String displayName = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "<gradient:#FFAA00:#FF5555>" + id.substring(0, 1).toUpperCase() + id.substring(1) + "</gradient>";
                com.apexpvp.arena.ArenaBounds bounds = new com.apexpvp.arena.ArenaBounds(-30, 0, -30, 30, 40, 30);
                ArenaTemplate.SpawnPoint spawnA = new ArenaTemplate.SpawnPoint(-18.5, 1.0, 0.5, -90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spawnB = new ArenaTemplate.SpawnPoint(18.5, 1.0, 0.5, 90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0.5, 15.0, 0.5, 0.0f, 45.0f);

                boolean created = plugin.getArenaManager().createTemplate(id, displayName, bounds, spawnA, spawnB, spec);
                if (created) {
                    TextUtil.sendPrefixed(sender, "<green>Created arena template <yellow>" + id + "</yellow> with default grid platform!");
                } else {
                    TextUtil.sendPrefixed(sender, "<red>Arena template '" + id + "' already exists!");
                }
                return true;
            }
            case "dupe" -> {
                if (args.length < 3) {
                    TextUtil.sendPrefixed(sender, "<red>Usage: /apex arena dupe <templateId>");
                    return true;
                }
                String id = args[2].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(id);
                if (template == null) {
                    TextUtil.sendPrefixed(sender, "<red>Unknown arena template: " + id);
                    return true;
                }
                ArenaInstance dynamic = plugin.getArenaManager().allocateDynamicInstance(template);
                TextUtil.sendPrefixed(sender, "<green>Duplicated arena <yellow>" + template.getId() + "</yellow> -> Dynamic Instance <aqua>#"
                        + dynamic.getInstanceIndex() + "</aqua> at grid origin <gold>(" + dynamic.getOriginX() + ", 64, " + dynamic.getOriginZ() + ")</gold>.");
                return true;
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only players can teleport to arenas."));
                    return true;
                }
                if (args.length < 3) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /apex arena tp <templateId> [instanceIndex]");
                    return true;
                }
                String id = args[2].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(id);
                if (template == null) {
                    TextUtil.sendPrefixed(player, "<red>Unknown arena template: " + id);
                    return true;
                }
                int index = 0;
                if (args.length >= 4) {
                    try { index = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                List<ArenaInstance> list = plugin.getArenaManager().getInstances(template.getId());
                if (list.isEmpty()) {
                    TextUtil.sendPrefixed(player, "<red>No instances allocated for template: " + id);
                    return true;
                }
                if (index < 0 || index >= list.size()) {
                    TextUtil.sendPrefixed(player, "<red>Invalid instance index. Range: 0 to " + (list.size() - 1));
                    return true;
                }
                ArenaInstance targetInst = list.get(index);
                player.teleport(targetInst.getSpectatorSpawn());
                TextUtil.sendPrefixed(player, "<green>Teleported to <yellow>" + targetInst.getInstanceIdentifier() + "</yellow> at Spectator Spawn.");
                return true;
            }
            case "setspawna", "setspawnb", "setspec" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only players can set spawn locations."));
                    return true;
                }
                if (args.length < 3) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /apex arena " + action + " <templateId>");
                    return true;
                }
                String id = args[2].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(id);
                if (template == null) {
                    TextUtil.sendPrefixed(player, "<red>Unknown arena template: " + id);
                    return true;
                }

                org.bukkit.Location loc = player.getLocation();
                int originX = template.getTemplateId() * 1000;
                int originY = 64;
                int originZ = 0;
                double relX = loc.getX() - originX;
                double relY = loc.getY() - originY;
                double relZ = loc.getZ() - originZ;
                ArenaTemplate.SpawnPoint sp = new ArenaTemplate.SpawnPoint(relX, relY, relZ, loc.getYaw(), loc.getPitch());

                if (action.equals("setspawna")) {
                    plugin.getArenaManager().updateSpawnA(id, sp);
                    TextUtil.sendPrefixed(player, "<green>Spawn A updated for <yellow>" + id + "</yellow> at relative (" + String.format("%.1f, %.1f, %.1f", relX, relY, relZ) + ").");
                } else if (action.equals("setspawnb")) {
                    plugin.getArenaManager().updateSpawnB(id, sp);
                    TextUtil.sendPrefixed(player, "<green>Spawn B updated for <yellow>" + id + "</yellow> at relative (" + String.format("%.1f, %.1f, %.1f", relX, relY, relZ) + ").");
                } else {
                    plugin.getArenaManager().updateSpectatorSpawn(id, sp);
                    TextUtil.sendPrefixed(player, "<green>Spectator spawn updated for <yellow>" + id + "</yellow> at relative (" + String.format("%.1f, %.1f, %.1f", relX, relY, relZ) + ").");
                }
                return true;
            }
            default -> {
                TextUtil.sendPrefixed(sender, "<red>Unknown arena subcommand. Use /apex arena for help.");
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.parse("<dark_gray>----------------- <gradient:#00FFAA:#00AAFF><bold>APEXPVP COMMANDS</bold></gradient> <dark_gray>-----------------"));
        sender.sendMessage(TextUtil.parse("<gold>/apex editor <gray>- Open the Visual In-Game Admin Editor GUI"));
        sender.sendMessage(TextUtil.parse("<gold>/apex arena <list|create|dupe|tp|setspawnA|setspawnB|setspec> <gray>- Arena grid manager"));
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
            List<String> list = new ArrayList<>(Arrays.asList("editor", "arena", "simduel", "eco", "reload", "help"));
            return list;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("arena")) {
            if (args.length == 2) {
                return Arrays.asList("list", "create", "dupe", "tp", "setspawnA", "setspawnB", "setspec");
            } else if (args.length == 3) {
                List<String> tList = new ArrayList<>();
                for (ArenaTemplate t : plugin.getArenaManager().getAllTemplates()) tList.add(t.getId());
                return tList;
            }
        } else if (sub.equals("simduel")) {
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
