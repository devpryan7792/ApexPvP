package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and orchestrator for all active PvP matches on the server.
 */
public class MatchManager {

    private final ApexPvPPlugin plugin;
    private final Map<UUID, Match> activeMatches = new ConcurrentHashMap<>();
    private final Map<UUID, Match> playerMatchMap = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRestoreSnapshot> pendingRestores = new ConcurrentHashMap<>();

    public MatchManager(@Nullable ApexPvPPlugin plugin) {
        this.plugin = plugin;
    }

    public void savePendingRestore(@NotNull UUID uuid, @NotNull PlayerRestoreSnapshot snapshot) {
        pendingRestores.put(uuid, snapshot);
    }

    @Nullable
    public PlayerRestoreSnapshot consumePendingRestore(@NotNull UUID uuid) {
        return pendingRestores.remove(uuid);
    }

    public boolean hasPendingRestore(@NotNull UUID uuid) {
        return pendingRestores.containsKey(uuid);
    }

    public void registerMatch(@NotNull Match match) {
        activeMatches.put(match.getMatchId(), match);
        for (MatchParticipant part : match.getParticipants()) {
            playerMatchMap.put(part.getUuid(), match);
        }
    }

    public void registerParticipant(@NotNull UUID uuid, @NotNull Match match) {
        playerMatchMap.put(uuid, match);
    }

    public void unregisterParticipant(@NotNull UUID uuid) {
        playerMatchMap.remove(uuid);
    }

    public void unregisterMatch(@NotNull UUID matchId) {
        Match match = activeMatches.remove(matchId);
        if (match != null) {
            for (MatchParticipant part : match.getParticipants()) {
                playerMatchMap.remove(part.getUuid());
            }
        }
    }

    @Nullable
    public Match getMatch(@NotNull Player player) {
        return getMatch(player.getUniqueId());
    }

    @Nullable
    public Match getMatch(@NotNull UUID uuid) {
        return playerMatchMap.get(uuid);
    }

    public boolean isInMatch(@NotNull Player player) {
        return playerMatchMap.containsKey(player.getUniqueId());
    }

    @NotNull
    public Collection<Match> getActiveMatches() {
        return Collections.unmodifiableCollection(activeMatches.values());
    }

    public int getActiveMatchCount() {
        return activeMatches.size();
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down MatchManager (" + activeMatches.size() + " active matches)...");
        for (Match match : new ArrayList<>(activeMatches.values())) {
            try {
                match.cleanup();
            } catch (Exception e) {
                plugin.getLogger().warning("Error cleaning up match " + match.getMatchId() + ": " + e.getMessage());
            }
        }
        activeMatches.clear();
        playerMatchMap.clear();
    }
}
