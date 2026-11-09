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
 * Simplified team (party) command. Replaces the old /party command.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/team create              — Create a new team</li>
 *   <li>/team invite &lt;player&gt;    — Invite a player to your team</li>
 *   <li>/team join &lt;leader&gt;     — Accept a pending invite OR a pending party-duel challenge from that leader</li>
 *   <li>/team leave               — Leave your current team</li>
 *   <li>/team disband             — Disband the team (leader only)</li>
 *   <li>/team split [kit]         — Split the team into two balanced sides for a scrimmage</li>
 *   <li>/team ffa [kit]           — Start a Free-For-All within the team</li>
 *   <li>/team duel &lt;leader&gt; [kit] — Challenge another team to a party duel</li>
 *   <li>/team duel decline &lt;leader&gt; — Decline an incoming party-duel challenge</li>
 *   <li>/team info                — Show current team roster</li>
 * </ul>
 *
 * <p>Both {@code /team} and {@code /party} are aliased to this handler.
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    private final ApexPvPPlugin plugin;

    public TeamCommand(@NotNull ApexPvPPlugin plugin) {
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

        if (!CommandPermissions.require(player, "apexpvp.team")) {
            return true;
        }

        // No args → open GUI
        if (args.length == 0) {
            plugin.getPartyGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // ── GUI ──────────────────────────────────────────────────────────────
            case "gui" -> plugin.getPartyGUI().open(player);

            // ── Create ───────────────────────────────────────────────────────────
            case "create" -> plugin.getPartyManager().createParty(player);

            // ── Invite ───────────────────────────────────────────────────────────
            case "invite" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /team invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
                    return true;
                }
                plugin.getPartyManager().invitePlayer(player, target);
            }

            // ── Join (unified: accepts invite OR party-duel challenge) ─────────
            case "join" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /team join <leader>");
                    return true;
                }
                Player leader = Bukkit.getPlayer(args[1]);
                if (leader == null || !leader.isOnline()) {
                    TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
                    return true;
                }
                plugin.getPartyManager().joinTeam(player, leader);
            }

            // ── Leave ────────────────────────────────────────────────────────────
            case "leave" -> plugin.getPartyManager().leaveParty(player);

            // ── Disband ──────────────────────────────────────────────────────────
            case "disband" -> plugin.getPartyManager().disbandParty(player);

            // ── Duel ─────────────────────────────────────────────────────────────
            // /team duel <leader> [kit]       — challenge another team
            // /team duel decline <leader>     — decline an incoming challenge
            case "duel" -> {
                if (args.length < 2) {
                    TextUtil.sendPrefixed(player, "<red>Usage: /team duel <leader> [kit]  <gray>|  <red>/team duel decline <leader>");
                    return true;
                }

                // Decline sub-action
                if (args[1].equalsIgnoreCase("decline")) {
                    if (args.length < 3) {
                        TextUtil.sendPrefixed(player, "<red>Usage: /team duel decline <leader>");
                        return true;
                    }
                    Player challenger = Bukkit.getPlayer(args[2]);
                    if (challenger == null || !challenger.isOnline()) {
                        TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
                        return true;
                    }
                    plugin.getPartyManager().declinePartyDuel(player, challenger);
                    return true;
                }

                // Challenge
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    TextUtil.sendPrefixed(player, "<red>Player not found or offline.");
                    return true;
                }
                Kit kit = null;
                if (args.length > 2) {
                    kit = plugin.getKitManager().getKit(args[2]);
                    if (kit == null) {
                        TextUtil.sendPrefixed(player, "<red>Kit '<yellow>" + args[2] + "</yellow>' not found.");
                        return true;
                    }
                }
                if (kit == null) {
                    kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
                    if (kit == null) {
                        TextUtil.sendPrefixed(player, "<red>No kits are configured on this server.");
                        return true;
                    }
                }
                plugin.getPartyManager().challengeParty(player, target, kit);
            }

            // ── Split ────────────────────────────────────────────────────────────
            case "split" -> {
                Kit kit = resolveKit(args, 1, player);
                if (kit == null) return true;
                plugin.getPartyManager().splitParty(player, kit);
            }

            // ── FFA ──────────────────────────────────────────────────────────────
            case "ffa" -> {
                Kit kit = resolveKit(args, 1, player);
                if (kit == null) return true;
                plugin.getPartyManager().startPartyFfa(player, kit);
            }

            // ── Info ─────────────────────────────────────────────────────────────
            case "info" -> {
                Party party = plugin.getPartyManager().getParty(player);
                if (party == null) {
                    TextUtil.sendPrefixed(player, "<red>You are not in a team.");
                    return true;
                }
                Player leaderPlayer = Bukkit.getPlayer(party.getLeader());
                String leaderName = leaderPlayer != null ? leaderPlayer.getName() : "Unknown";

                List<String> memberNames = new ArrayList<>();
                for (UUID uuid : party.getMembers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) memberNames.add(p.getName());
                }

                TextUtil.sendPrefixed(player,
                        "<gradient:#FFAA00:#FF5555><bold>TEAM ROSTER</bold></gradient> (<yellow>"
                                + party.getSize() + "/" + party.getMaxMembers() + "</yellow>)");
                player.sendMessage(TextUtil.parse("<gray>Leader: <gold>" + leaderName));
                player.sendMessage(TextUtil.parse("<gray>Members: <white>" + String.join(", ", memberNames)));
            }

            // ── Help / unknown ───────────────────────────────────────────────────
            default -> sendHelp(player, label);
        }

        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves a kit from {@code args[argIndex]} if present, otherwise picks the first available kit.
     * Returns {@code null} (and sends an error) if no kits exist.
     */
    @Nullable
    private Kit resolveKit(@NotNull String[] args, int argIndex, @NotNull Player player) {
        Kit kit = null;
        if (args.length > argIndex) {
            kit = plugin.getKitManager().getKit(args[argIndex]);
            if (kit == null) {
                TextUtil.sendPrefixed(player, "<red>Kit '<yellow>" + args[argIndex] + "</yellow>' not found. Check <yellow>/kits list</yellow>.");
                return null;
            }
        }
        if (kit == null) {
            kit = plugin.getKitManager().getAllKits().stream().findFirst().orElse(null);
            if (kit == null) {
                TextUtil.sendPrefixed(player, "<red>No kits are configured on this server.");
                return null;
            }
        }
        return kit;
    }

    private void sendHelp(@NotNull Player player, @NotNull String label) {
        TextUtil.sendPrefixed(player, "<gradient:#FFAA00:#FF5555><bold>TEAM COMMANDS</bold></gradient>");
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " create <gray>- Create a new team"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " invite <player> <gray>- Invite a player to your team"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " join <leader> <gray>- Accept an invite or party-duel challenge"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " leave <gray>- Leave your current team"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " disband <gray>- Disband the team (leader only)"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " duel <leader> [kit] <gray>- Challenge another team to a party duel"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " duel decline <leader> <gray>- Decline an incoming party-duel challenge"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " split [kit] <gray>- Scrimmage: split into two balanced teams"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " ffa [kit] <gray>- Start an all-out Free-For-All (2–16 players)"));
        player.sendMessage(TextUtil.parse("<yellow>/" + label + " info <gray>- Show team roster"));
    }

    // ── Tab Completion ────────────────────────────────────────────────────────

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "invite", "join", "leave", "disband", "duel", "split", "ffa", "info"
    );

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS;
        }

        String sub = args[0].toLowerCase();

        // arg 2: player names for invite / join
        if (args.length == 2 && (sub.equals("invite") || sub.equals("join"))) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) players.add(p.getName());
            }
            return players;
        }

        // /team duel <leader> → player names; "decline" is also an option here
        if (args.length == 2 && sub.equals("duel")) {
            List<String> opts = new ArrayList<>();
            opts.add("decline");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) opts.add(p.getName());
            }
            return opts;
        }

        // /team duel decline <leader>
        if (args.length == 3 && sub.equals("duel") && args[1].equalsIgnoreCase("decline")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) players.add(p.getName());
            }
            return players;
        }

        // /team duel <leader> [kit]
        if (args.length == 3 && sub.equals("duel") && !args[1].equalsIgnoreCase("decline")) {
            List<String> kits = new ArrayList<>();
            plugin.getKitManager().getAllKits().forEach(k -> kits.add(k.getId()));
            return kits;
        }

        // /team split [kit] and /team ffa [kit]
        if (args.length == 2 && (sub.equals("split") || sub.equals("ffa"))) {
            List<String> kits = new ArrayList<>();
            plugin.getKitManager().getAllKits().forEach(k -> kits.add(k.getId()));
            return kits;
        }

        return Collections.emptyList();
    }
}
