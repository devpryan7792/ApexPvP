package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.Rank;
import com.apexpvp.util.TextUtil;
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

        boolean promoted = plugin.getProfileManager().checkAndApplyAutoRankup(profile, player);
        if (promoted) {
            return true;
        }

        Rank currentRank = profile.getRank();
        Rank nextRank = currentRank.getNextRank();

        if (nextRank == null) {
            TextUtil.sendPrefixed(player, "<gold>You are already at the pinnacle rank (<yellow>" + currentRank.getDisplayName() + "</yellow>)!");
            return true;
        }

        int currentElo = profile.getElo();
        int diff = Math.max(0, nextRank.getEloThreshold() - currentElo);
        TextUtil.sendPrefixed(player, "<yellow>Rankups are automatic! <gray>You are currently " + currentRank.getMiniMessageDisplay() +
                "<gray>. Reach <gold>" + nextRank.getEloThreshold() + " ELO</gold> (<yellow>" + diff + "</yellow> more ELO) to automatically advance to " +
                nextRank.getMiniMessageDisplay() + "<gray>!");
        return true;
    }
}
