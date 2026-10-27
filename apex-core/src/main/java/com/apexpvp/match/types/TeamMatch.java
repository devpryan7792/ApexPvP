package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Team vs Team match supporting Party Split scrimmages and Party vs Party battles.
 */
public class TeamMatch extends Match {

    private final String team0Name;
    private final String team1Name;

    public TeamMatch(@NotNull ApexPvPPlugin plugin,
                     @NotNull ArenaInstance arena,
                     @NotNull Kit kit,
                     @NotNull List<Player> team0,
                     @NotNull List<Player> team1,
                     @NotNull String team0Name,
                     @NotNull String team1Name) {
        super(plugin, arena, kit);
        this.team0Name = team0Name;
        this.team1Name = team1Name;

        for (Player p : team0) {
            addParticipant(p, 0);
        }
        for (Player p : team1) {
            addParticipant(p, 1);
        }
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        String winnerTeam = (winningTeamIndex == 0) ? team0Name : (winningTeamIndex == 1 ? team1Name : "Neither");
        String loserTeam = (winningTeamIndex == 0) ? team1Name : (winningTeamIndex == 1 ? team0Name : "Neither");

        if (winningTeamIndex < 0) {
            broadcast("<yellow>The team match ended in a draw!");
            return;
        }

        broadcast("<gradient:#00FFAA:#00AAFF><bold>TEAM VICTORY!</bold></gradient> <gold>" + winnerTeam + "</gold> eliminated <red>" + loserTeam + "</red>!");

        // Record stats for all participants
        for (MatchParticipant part : participants.values()) {
            boolean isWinner = (part.getTeamIndex() == winningTeamIndex);
            var profile = plugin.getProfileManager().getProfile(part.getUuid());
            if (profile != null) {
                var stats = profile.getOrCreateStats(kit.getId());
                if (isWinner) {
                    stats.incrementWins();
                } else {
                    stats.incrementLosses();
                }
                profile.markDirty();
            }
        }
    }
}
