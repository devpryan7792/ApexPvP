package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Free-For-All (FFA) match where every participant battles independently.
 * Spawns combatants around a radial perimeter facing the center and resolves
 * when only a single surviving player remains.
 */
public class FfaMatch extends Match {

    public FfaMatch(@NotNull ApexPvPPlugin plugin,
                    @NotNull ArenaInstance arena,
                    @NotNull Kit kit,
                    @NotNull List<Player> combatants) {
        super(plugin, arena, kit);

        // Every participant receives their own unique team index (0..N-1)
        // to enable mutual combat without triggering same-team friendly fire cancellation.
        for (int i = 0; i < combatants.size(); i++) {
            addParticipant(combatants.get(i), i);
        }
    }

    @Override
    protected Location getSpawnForParticipant(@NotNull MatchParticipant participant) {
        World world = arena.getWorld();
        if (world == null) {
            return new Location(null, 0, 0, 0);
        }

        double centerX;
        double centerY;
        double centerZ;

        Location ffaSpawn = arena.getFfaSpawn();
        if (ffaSpawn != null && ffaSpawn.getWorld() != null) {
            centerX = ffaSpawn.getX();
            centerY = ffaSpawn.getY();
            centerZ = ffaSpawn.getZ();
        } else {
            centerX = (arena.getBounds().getMinX() + arena.getBounds().getMaxX()) / 2.0;
            centerY = arena.getOriginY();
            centerZ = (arena.getBounds().getMinZ() + arena.getBounds().getMaxZ()) / 2.0;
        }

        // Sort combatants deterministically to assign equidistant radial spawn points
        List<UUID> sortedUuids = participants.keySet().stream().sorted().toList();
        int index = Math.max(0, sortedUuids.indexOf(participant.getUuid()));
        int total = Math.max(1, sortedUuids.size());

        int halfWidth = (arena.getBounds().getMaxX() - arena.getBounds().getMinX()) / 2;
        int halfLength = (arena.getBounds().getMaxZ() - arena.getBounds().getMinZ()) / 2;
        double radius = Math.max(8.0, Math.min(halfWidth, halfLength) * 0.65);

        double angle = (2.0 * Math.PI * index) / total;
        double spawnX = centerX + radius * Math.cos(angle);
        double spawnZ = centerZ + radius * Math.sin(angle);

        // Face towards the center of the arena
        double dx = centerX - spawnX;
        double dz = centerZ - spawnZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        return new Location(world, spawnX, centerY, spawnZ, yaw, 0.0f);
    }

    @Override
    protected void checkWinCondition() {
        List<MatchParticipant> alive = participants.values().stream()
                .filter(MatchParticipant::isAlive)
                .toList();

        if (alive.size() <= 1) {
            if (alive.size() == 1) {
                enterEnding(alive.get(0).getTeamIndex());
            } else {
                enterEnding(-1); // Simultaneous elimination -> Draw
            }
        }
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        if (winningTeamIndex < 0) {
            broadcast("<yellow>The FFA match ended in a draw! No fighters survived.");
            return;
        }

        MatchParticipant winnerPart = participants.values().stream()
                .filter(p -> p.getTeamIndex() == winningTeamIndex)
                .findFirst()
                .orElse(null);

        if (winnerPart == null) return;
        Player winner = Bukkit.getPlayer(winnerPart.getUuid());
        String winnerName = (winner != null) ? winner.getName() : "Unknown";

        broadcast("<gradient:#00FFAA:#00AAFF><bold>FFA VICTORY!</bold></gradient> <gold>" + winnerName + "</gold> is the last player standing!");

        double winReward = plugin.getConfig().getDouble("ffa.win-reward", 50.0);
        boolean economyEnabled = plugin.getConfig().getBoolean("ranked.economy.enabled", true);

        for (MatchParticipant part : participants.values()) {
            boolean isWinner = (part.getTeamIndex() == winningTeamIndex);
            PlayerProfile profile = plugin.getProfileManager().getProfile(part.getUuid());
            if (profile != null) {
                var stats = profile.getOrCreateStats(kit.getId());
                if (isWinner) {
                    stats.incrementWins();
                    profile.incrementStreak();

                    if (economyEnabled && winReward > 0 && plugin.getEconomy() != null) {
                        double reward = winReward * profile.getStreakMultiplier();
                        plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(part.getUuid()), reward);
                        Player p = Bukkit.getPlayer(part.getUuid());
                        if (p != null) {
                            TextUtil.sendPrefixed(p, "<gradient:#00FFAA:#FFFF55><bold>REWARD!</bold></gradient> <gold>+" + String.format("%.2f", reward) + " coins</gold> awarded for FFA victory!");
                        }
                    }
                } else {
                    stats.incrementLosses();
                    profile.resetStreak();
                }
                profile.markDirty();
                plugin.getProfileManager().saveProfile(profile);
            }
        }
    }
}
