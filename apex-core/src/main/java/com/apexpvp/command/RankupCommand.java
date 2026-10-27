package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.Rank;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Handles rank promotions when ELO threshold and currency rankup requirements are met.
 * Usage: /rankup
 */
public class RankupCommand implements CommandExecutor {

    private final ApexPvPPlugin plugin;

    public RankupCommand(@NotNull ApexPvPPlugin plugin) {
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

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) {
            TextUtil.sendPrefixed(player, "<red>Profile data not loaded.");
            return true;
        }

        Rank currentRank = profile.getRank();
        Rank nextRank = currentRank.getNextRank();

        if (nextRank == null) {
            TextUtil.sendPrefixed(player, "<gold>You are already at the pinnacle rank (<yellow>" + currentRank.getDisplayName() + "</yellow>)!");
            return true;
        }

        int currentElo = profile.getElo();
        if (currentElo < nextRank.getEloThreshold()) {
            TextUtil.sendPrefixed(player, "<red>You need <gold><req_elo></gold> ELO to unlock <rank_display><red> (Current: <yellow><cur_elo></yellow> ELO).",
                    Placeholder.parsed("req_elo", String.valueOf(nextRank.getEloThreshold())),
                    Placeholder.parsed("rank_display", nextRank.getDisplayName()),
                    Placeholder.parsed("cur_elo", String.valueOf(currentElo)));
            return true;
        }

        double cost = nextRank.getRankupCost();
        if (cost > 0.0) {
            Economy economy = plugin.getEconomy();
            if (economy != null) {
                if (!economy.has(player, cost)) {
                    TextUtil.sendPrefixed(player, "<red>You need <gold><cost></gold> coins to rank up to <rank_display><red>!",
                            Placeholder.parsed("cost", String.valueOf(cost)),
                            Placeholder.parsed("rank_display", nextRank.getDisplayName()));
                    return true;
                }
                var res = economy.withdrawPlayer(player, cost);
                if (!res.transactionSuccess()) {
                    TextUtil.sendPrefixed(player, "<red>Failed to process rankup transaction.");
                    return true;
                }
            }
        }

        // Apply Promotion
        profile.setRank(nextRank);
        TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>PROMOTION SUCCESSFUL!</bold></gradient> You advanced to " + nextRank.getMiniMessageDisplay() + "!");
        plugin.getServer().broadcast(TextUtil.prefixed("<gold>" + player.getName() + "</gold> promoted to " + nextRank.getMiniMessageDisplay() + "<reset>!"));

        return true;
    }
}
