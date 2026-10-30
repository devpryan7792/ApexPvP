package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Administrative arena command for managing templates, viewing instances, teleporting,
 * setting spawn coordinates, and reloading configurations:
 * Usage: /arena list
 *        /arena tp <template>
 *        /arena setspawn <template> <a|b|spec>
 *        /arena reload
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public ArenaCommand(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.hasPermission("apexpvp.arena.admin")) {
            sender.sendMessage(TextUtil.parse("<red>You lack permission to execute arena administrative commands."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>ApexPvP Arena Management</bold></gradient>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena list</yellow> <gray>- View all configured templates & instances</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena tp <template></yellow> <gray>- Teleport into arena platform</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena setspawn <template> <a|b|spec></yellow> <gray>- Update spawn point</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena reload</yellow> <gray>- Reload arenas.yml and regenerate platforms</gray>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> {
                Collection<ArenaTemplate> templates = plugin.getArenaManager().getAllTemplates();
                sender.sendMessage(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Arena Templates</bold></gradient> <gray>(" + templates.size() + " loaded):</gray>"));
                for (ArenaTemplate t : templates) {
                    List<ArenaInstance> instances = plugin.getArenaManager().getInstances(t.getId());
                    int active = (int) instances.stream().filter(i -> i.getState() != com.apexpvp.arena.ArenaState.AVAILABLE).count();
                    int width = t.getRelativeBounds().getMaxX() - t.getRelativeBounds().getMinX();
                    int length = t.getRelativeBounds().getMaxZ() - t.getRelativeBounds().getMinZ();
                    sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gold>" + t.getId() + "</gold> » " + t.getDisplayName() +
                            " <gray>(" + width + "x" + length + " blocks | " + instances.size() + " instances, " + active + " active)</gray>"));
                }
                return true;
            }

            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can teleport to an arena."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /arena tp <template>");
                    return true;
                }
                ArenaTemplate template = plugin.getArenaManager().getTemplate(args[1]);
                if (template == null) {
                    TextUtil.sendPrefixed(player, "<red>Arena template '<yellow>" + args[1] + "</yellow>' not found.");
                    return true;
                }
                List<ArenaInstance> instances = plugin.getArenaManager().getInstances(template.getId());
                if (instances.isEmpty()) {
                    TextUtil.sendPrefixed(player, "<red>No instances allocated for template '<yellow>" + args[1] + "</yellow>'.");
                    return true;
                }
                ArenaInstance targetInstance = instances.get(0);
                Location loc = targetInstance.getSpectatorSpawn();
                player.teleportAsync(loc).thenAccept(success -> {
                    if (success) {
                        TextUtil.sendPrefixed(player, "<green>Teleported to spectator spawn of <gold>" + template.getDisplayName() + "</gold>.");
                    } else {
                        TextUtil.sendPrefixed(player, "<red>Failed to teleport to arena world.");
                    }
                });
                return true;
            }

            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only in-game players can set spawn points."));
                    return true;
                }
                if (args.length < 3) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /arena setspawn <template> <a|b|spec>");
                    return true;
                }
                String templateId = args[1].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(templateId);
                if (template == null) {
                    TextUtil.sendPrefixed(player, "<red>Arena template '<yellow>" + args[1] + "</yellow>' not found.");
                    return true;
                }

                String spawnType = args[2].toLowerCase();
                Location playerLoc = player.getLocation();

                // Compute origin offset
                int originX = template.getTemplateId() * 1000;
                int originY = 64; // baseline Y
                int originZ = 0;   // instance 0

                // If player is inside the arena world, calculate offset relative to nearest instance
                if (playerLoc.getWorld().equals(plugin.getArenaManager().getArenaWorld())) {
                    ArenaInstance inside = plugin.getArenaManager().findInstanceAt(playerLoc);
                    if (inside != null) {
                        originX = inside.getOriginX();
                        originY = inside.getOriginY();
                        originZ = inside.getOriginZ();
                    }
                }

                double relX = playerLoc.getX() - originX;
                double relY = playerLoc.getY() - originY;
                double relZ = playerLoc.getZ() - originZ;
                float yaw = playerLoc.getYaw();
                float pitch = playerLoc.getPitch();

                ArenaTemplate.SpawnPoint newSpawn = new ArenaTemplate.SpawnPoint(relX, relY, relZ, yaw, pitch);

                boolean updated;
                switch (spawnType) {
                    case "a", "spawna", "spawn-a" -> updated = plugin.getArenaManager().updateSpawnA(templateId, newSpawn);
                    case "b", "spawnb", "spawn-b" -> updated = plugin.getArenaManager().updateSpawnB(templateId, newSpawn);
                    case "spec", "spectator" -> updated = plugin.getArenaManager().updateSpectatorSpawn(templateId, newSpawn);
                    default -> {
                        TextUtil.sendPrefixed(player, "<red>Invalid spawn type. Must be: a, b, or spec.");
                        return true;
                    }
                }

                if (updated) {
                    TextUtil.sendPrefixed(player, "<green>Successfully updated spawn <gold>" + spawnType + "</gold> for arena <yellow>" + template.getDisplayName() + "</yellow> at relative coordinates (" +
                            String.format("%.1f, %.1f, %.1f", relX, relY, relZ) + ").");
                } else {
                    TextUtil.sendPrefixed(player, "<red>Failed to update spawn point.");
                }
                return true;
            }

            case "reload" -> {
                plugin.getArenaManager().reloadTemplates();
                sender.sendMessage(TextUtil.parse("<green>Arena templates and instances reloaded successfully from arenas.yml!"));
                return true;
            }

            default -> {
                sender.sendMessage(TextUtil.parse("<red>Unknown subcommand. Use /arena for help."));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("apexpvp.arena.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("tp");
            completions.add("setspawn");
            completions.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("setspawn"))) {
            for (ArenaTemplate t : plugin.getArenaManager().getAllTemplates()) {
                completions.add(t.getId());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) {
            completions.add("a");
            completions.add("b");
            completions.add("spec");
        }
        return completions;
    }
}
