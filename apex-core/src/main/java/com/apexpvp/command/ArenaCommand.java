package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
            sender.sendMessage(TextUtil.parse("<yellow>/arena toggle <template></yellow> <gray>- Toggle arena enabled/maintenance mode</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena prebuilt [enable|disable|toggle|status]</yellow> <gray>- Toggle prebuilt arenas</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena seticon <template> [material]</yellow> <gray>- Set unique map icon (uses held item if omitted)</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena create <id> [displayName] [radius]</yellow> <gray>- Create arena from WorldEdit selection</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena import <file> <id> [displayName]</yellow> <gray>- Import map from maps/ folder</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena tp <template></yellow> <gray>- Teleport into arena platform</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena setspawn <template> <a|b|spec></yellow> <gray>- Update spawn point</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/arena reload</yellow> <gray>- Reload arenas.yml and regenerate platforms</gray>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "import" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /arena import <schematicFile> <id> [displayName]"));
                    return true;
                }
                String fileName = args[1];
                String id = args[2].toLowerCase();
                String displayName = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id;

                if (plugin.getArenaManager().getTemplate(id) != null) {
                    sender.sendMessage(TextUtil.parse("<red>An arena template with ID '<yellow>" + id + "</yellow>' already exists!"));
                    return true;
                }

                File schemFile = plugin.getArenaManager().getSchematicFile(fileName);
                if (schemFile == null) {
                    sender.sendMessage(TextUtil.parse("<red>Schematic file '<yellow>" + fileName +
                            "</yellow>' not found in <white>plugins/ApexPvP/maps/</white>!"));
                    List<String> available = plugin.getArenaManager().getAvailableSchematicFiles();
                    if (!available.isEmpty()) {
                        sender.sendMessage(TextUtil.parse("<gray>Available maps in folder: <gold>" + String.join(", ", available) + "</gold>"));
                    }
                    return true;
                }

                ArenaBounds bounds = com.apexpvp.integration.worldedit.WorldEditHook.getSchematicBounds(schemFile);
                if (bounds == null) {
                    bounds = new ArenaBounds(-35, 0, -35, 35, 40, 35);
                }

                int halfX = bounds.getMaxX() * 2 / 3;
                ArenaTemplate.SpawnPoint spawnA = new ArenaTemplate.SpawnPoint(-halfX, 1.0, 0.5, -90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spawnB = new ArenaTemplate.SpawnPoint(halfX, 1.0, 0.5, 90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0.5, 15.0, 0.5, 0.0f, 45.0f);

                boolean created = plugin.getArenaManager().createTemplate(id, displayName, bounds, spawnA, spawnB, spec, fileName);
                if (created) {
                    sender.sendMessage(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>MAP IMPORTED!</bold></gradient> Imported <gold>" +
                            fileName + "</gold> as arena <gold>" + displayName + "</gold> (<yellow>" + id + "</yellow>)!\n" +
                            "<gray>Next step: Set your spawns with <yellow>/arena setspawn " + id + " <a|b|spec></yellow>."));
                } else {
                    sender.sendMessage(TextUtil.parse("<red>Failed to register imported arena template."));
                }
                return true;
            }

            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtil.parse("<red>Only players can create an arena."));
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /arena create <id> [displayName] [radius]");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (plugin.getArenaManager().getTemplate(id) != null) {
                    TextUtil.sendPrefixed(player, "<red>An arena template with ID '<yellow>" + id + "</yellow>' already exists!");
                    return true;
                }

                String displayName = args.length >= 3 ? args[2] : id;
                ArenaBounds bounds = null;

                // 1. Try to read active WorldEdit selection
                ArenaBounds weBounds = com.apexpvp.integration.worldedit.WorldEditHook.getPlayerSelectionBounds(player);
                if (weBounds != null) {
                    int centerX = (weBounds.getMinX() + weBounds.getMaxX()) / 2;
                    int centerZ = (weBounds.getMinZ() + weBounds.getMaxZ()) / 2;
                    int minY = weBounds.getMinY();

                    int relMinX = weBounds.getMinX() - centerX;
                    int relMaxX = weBounds.getMaxX() - centerX;
                    int relMinZ = weBounds.getMinZ() - centerZ;
                    int relMaxZ = weBounds.getMaxZ() - centerZ;
                    int relMaxY = Math.max(25, weBounds.getMaxY() - minY);

                    bounds = new ArenaBounds(relMinX, 0, relMinZ, relMaxX, relMaxY, relMaxZ);
                    TextUtil.sendPrefixed(player, "<green>Captured WorldEdit selection: <white>" +
                            (relMaxX - relMinX) + "x" + (relMaxZ - relMinZ) + "</white> blocks!");
                } else {
                    int radius = 30;
                    if (args.length >= 4) {
                        try {
                            radius = Integer.parseInt(args[3]);
                        } catch (NumberFormatException ignored) {}
                    }
                    bounds = new ArenaBounds(-radius, 0, -radius, radius, 40, radius);
                    TextUtil.sendPrefixed(player, "<yellow>No WorldEdit selection found. Defaulting to radius <gold>" + radius + "</gold> blocks.");
                }

                int halfX = bounds.getMaxX() * 2 / 3;
                ArenaTemplate.SpawnPoint spawnA = new ArenaTemplate.SpawnPoint(-halfX, 1.0, 0.5, -90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spawnB = new ArenaTemplate.SpawnPoint(halfX, 1.0, 0.5, 90.0f, 0.0f);
                ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0.5, 15.0, 0.5, 0.0f, 45.0f);

                boolean created = plugin.getArenaManager().createTemplate(id, displayName, bounds, spawnA, spawnB, spec);
                if (created) {
                    ItemStack hand = (player.getInventory() != null) ? player.getInventory().getItemInMainHand() : null;
                    if (hand != null && !hand.getType().isAir()) {
                        plugin.getArenaManager().setArenaIcon(id, hand.getType().name());
                        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>ARENA CREATED!</bold></gradient> Arena <gold>" + displayName +
                                "</gold> (<yellow>" + id + "</yellow>) is now registered!\n" +
                                "<green>Map Icon automatically set to held item: <aqua>" + hand.getType().name() + "</aqua>!</green>\n" +
                                "<gray>Next step: Set your spawns with <yellow>/arena setspawn " + id + " <a|b|spec></yellow>.");
                    } else {
                        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>ARENA CREATED!</bold></gradient> Arena <gold>" + displayName +
                                "</gold> (<yellow>" + id + "</yellow>) is now registered!\n" +
                                "<yellow>Notice: No item held in hand. Default map icon assigned. You can customize the map icon in <gold>/apex editor</gold> or hold an item and type <gold>/arena seticon " + id + "</gold>!</yellow>\n" +
                                "<gray>Next step: Set your spawns with <yellow>/arena setspawn " + id + " <a|b|spec></yellow>.");
                    }
                } else {
                    TextUtil.sendPrefixed(player, "<red>Failed to create arena template.");
                }
                return true;
            }

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

                boolean updated = switch (spawnType) {
                    case "a", "spawna", "spawn-a" -> plugin.getArenaManager().setSpawnA(templateId, playerLoc);
                    case "b", "spawnb", "spawn-b" -> plugin.getArenaManager().setSpawnB(templateId, playerLoc);
                    case "spec", "spectator" -> plugin.getArenaManager().setSpectatorSpawn(templateId, playerLoc);
                    case "ffa", "center" -> plugin.getArenaManager().setFfaSpawn(templateId, playerLoc);
                    default -> {
                        TextUtil.sendPrefixed(player, "<red>Invalid spawn type. Must be: a, b, spec, or ffa.");
                        yield false;
                    }
                };

                if (updated) {
                    ArenaTemplate updatedTemplate = plugin.getArenaManager().getTemplate(templateId);
                    ArenaTemplate.SpawnPoint sp = switch (spawnType) {
                        case "b", "spawnb", "spawn-b" -> updatedTemplate.getRelativeSpawnB();
                        case "spec", "spectator" -> updatedTemplate.getRelativeSpectatorSpawn();
                        case "ffa", "center" -> updatedTemplate.getRelativeFfaSpawn();
                        default -> updatedTemplate.getRelativeSpawnA();
                    };
                    String coords = (sp != null) ? String.format("%.1f, %.1f, %.1f", sp.x(), sp.y(), sp.z()) : "saved";
                    TextUtil.sendPrefixed(player, "<green>Successfully updated spawn <gold>" + spawnType + "</gold> for arena <yellow>" + template.getDisplayName() + "</yellow> at relative (" + coords + "). Changes are live!");
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

            case "toggle" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /arena toggle <template>"));
                    return true;
                }
                String templateId = args[1].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(templateId);
                if (template == null) {
                    sender.sendMessage(TextUtil.parse("<red>Arena template '<yellow>" + args[1] + "</yellow>' not found."));
                    return true;
                }
                boolean newState = plugin.getArenaManager().toggleArenaEnabled(templateId);
                sender.sendMessage(TextUtil.parse(newState
                        ? "<green>Arena <gold>" + template.getDisplayName() + "</gold> (<yellow>" + templateId + "</yellow>) is now <bold>ENABLED</bold>."
                        : "<yellow>Arena <gold>" + template.getDisplayName() + "</gold> (<yellow>" + templateId + "</yellow>) is now in <bold>MAINTENANCE (DISABLED)</bold>."));
                return true;
            }

            case "prebuilt", "toggleprebuilt" -> {
                String action = (args.length >= 2) ? args[1].toLowerCase() : "toggle";
                switch (action) {
                    case "enable", "on", "true" -> {
                        int count = plugin.getArenaManager().setPrebuiltArenasEnabled(true);
                        sender.sendMessage(TextUtil.parse("<green>All <gold>" + count + "</gold> prebuilt plugin arenas have been <bold>ENABLED</bold>."));
                    }
                    case "disable", "off", "false" -> {
                        int count = plugin.getArenaManager().setPrebuiltArenasEnabled(false);
                        sender.sendMessage(TextUtil.parse("<yellow>All <gold>" + count + "</gold> prebuilt plugin arenas have been <bold>DISABLED (MAINTENANCE)</bold>."));
                    }
                    case "status" -> {
                        boolean any = plugin.getArenaManager().arePrebuiltArenasEnabled();
                        sender.sendMessage(TextUtil.parse("<gray>Prebuilt Arenas Status: " + (any ? "<green><bold>ENABLED</bold></green>" : "<red><bold>DISABLED</bold></red>")));
                    }
                    case "toggle" -> {
                        boolean newState = plugin.getArenaManager().togglePrebuiltArenas();
                        sender.sendMessage(TextUtil.parse(newState
                                ? "<green>All prebuilt plugin arenas are now <bold>ENABLED</bold>."
                                : "<yellow>All prebuilt plugin arenas are now <bold>DISABLED (MAINTENANCE)</bold>."));
                    }
                    default -> sender.sendMessage(TextUtil.parse("<red>Usage: /arena prebuilt [enable|disable|toggle|status]"));
                }
                return true;
            }

            case "seticon" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /arena seticon <template> [material]"));
                    return true;
                }
                String templateId = args[1].toLowerCase();
                ArenaTemplate template = plugin.getArenaManager().getTemplate(templateId);
                if (template == null) {
                    sender.sendMessage(TextUtil.parse("<red>Arena template '<yellow>" + args[1] + "</yellow>' not found."));
                    return true;
                }

                org.bukkit.Material mat = null;
                if (args.length >= 3) {
                    mat = org.bukkit.Material.matchMaterial(args[2].toUpperCase());
                    if (mat == null) {
                        sender.sendMessage(TextUtil.parse("<red>Invalid material: " + args[2]));
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType() != org.bukkit.Material.AIR) {
                        mat = hand.getType();
                    } else {
                        sender.sendMessage(TextUtil.parse("<red>Please hold an item in your hand or specify a material: /arena seticon " + templateId + " <material>"));
                        return true;
                    }
                } else {
                    sender.sendMessage(TextUtil.parse("<red>Console must specify a material name: /arena seticon " + templateId + " <material>"));
                    return true;
                }

                plugin.getArenaManager().setArenaIcon(templateId, mat.name());
                sender.sendMessage(TextUtil.parse("<green>Set map icon for arena <gold>" + template.getDisplayName() + "</gold> to <yellow>" + mat.name() + "</yellow>!"));
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
            completions.add("create");
            completions.add("import");
            completions.add("tp");
            completions.add("setspawn");
            completions.add("toggle");
            completions.add("prebuilt");
            completions.add("seticon");
            completions.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("seticon"))) {
            for (ArenaTemplate t : plugin.getArenaManager().getAllTemplates()) {
                completions.add(t.getId());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("prebuilt")) {
            completions.add("enable");
            completions.add("disable");
            completions.add("toggle");
            completions.add("status");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("seticon")) {
            for (Material m : Material.values()) {
                if (m.isItem() && !m.isAir()) completions.add(m.name().toLowerCase());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            completions.addAll(plugin.getArenaManager().getAvailableSchematicFiles());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            String guess = args[1].replace(".schem", "").replace(".schematic", "").toLowerCase();
            completions.add(guess);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) {
            completions.add("a");
            completions.add("b");
            completions.add("spec");
        }
        return completions;
    }
}
