package com.apexpvp.leaderboard;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.Rank;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * High-performance leaderboard cache manager providing asynchronous queries
 * and TTL caching for competitive ELO, win counts, and streak rankings.
 */
public class LeaderboardManager {

    public record LeaderboardEntry(
        int position,
        String username,
        Rank rank,
        int score
    ) {}

    private static class CacheEntry {
        final List<LeaderboardEntry> entries;
        final long timestamp;

        CacheEntry(List<LeaderboardEntry> entries) {
            this.entries = entries;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    private final ApexPvPPlugin plugin;
    private final long cacheTtlMillis = TimeUnit.SECONDS.toMillis(60);
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LeaderboardManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public CompletableFuture<List<LeaderboardEntry>> getTopElo(int limit) {
        String cacheKey = "elo_" + limit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.entries);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT username, current_rank, elo FROM apex_players ORDER BY elo DESC LIMIT ?";
            List<LeaderboardEntry> results = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        String name = rs.getString("username");
                        Rank rank = Rank.fromString(rs.getString("current_rank"));
                        int elo = rs.getInt("elo");
                        results.add(new LeaderboardEntry(pos++, name, rank, elo));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query ELO leaderboard", e);
            }
            List<LeaderboardEntry> immutable = Collections.unmodifiableList(results);
            cache.put(cacheKey, new CacheEntry(immutable));
            return immutable;
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> getTopWins(int limit) {
        String cacheKey = "wins_" + limit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.entries);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT p.username, p.current_rank, COALESCE(SUM(s.wins), 0) AS total_wins
                FROM apex_players p
                LEFT JOIN apex_player_stats s ON p.uuid = s.uuid
                GROUP BY p.uuid, p.username, p.current_rank
                ORDER BY total_wins DESC
                LIMIT ?
                """;
            List<LeaderboardEntry> results = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        String name = rs.getString("username");
                        Rank rank = Rank.fromString(rs.getString("current_rank"));
                        int wins = rs.getInt("total_wins");
                        results.add(new LeaderboardEntry(pos++, name, rank, wins));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query Wins leaderboard", e);
            }
            List<LeaderboardEntry> immutable = Collections.unmodifiableList(results);
            cache.put(cacheKey, new CacheEntry(immutable));
            return immutable;
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> getTopStreak(int limit) {
        String cacheKey = "streak_" + limit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.entries);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT username, current_rank, best_streak FROM apex_players ORDER BY best_streak DESC LIMIT ?";
            List<LeaderboardEntry> results = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        String name = rs.getString("username");
                        Rank rank = Rank.fromString(rs.getString("current_rank"));
                        int streak = rs.getInt("best_streak");
                        results.add(new LeaderboardEntry(pos++, name, rank, streak));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query Streaks leaderboard", e);
            }
            List<LeaderboardEntry> immutable = Collections.unmodifiableList(results);
            cache.put(cacheKey, new CacheEntry(immutable));
            return immutable;
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> getTopKitWins(String kitId, int limit) {
        String cacheKey = "kit_" + kitId.toLowerCase() + "_" + limit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return CompletableFuture.completedFuture(cached.entries);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT p.username, p.current_rank, s.wins
                FROM apex_players p
                JOIN apex_player_stats s ON p.uuid = s.uuid
                WHERE LOWER(s.kit_id) = LOWER(?)
                ORDER BY s.wins DESC
                LIMIT ?
                """;
            List<LeaderboardEntry> results = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, kitId.toLowerCase());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        String name = rs.getString("username");
                        Rank rank = Rank.fromString(rs.getString("current_rank"));
                        int wins = rs.getInt("wins");
                        results.add(new LeaderboardEntry(pos++, name, rank, wins));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query Kit Wins leaderboard for " + kitId, e);
            }
            List<LeaderboardEntry> immutable = Collections.unmodifiableList(results);
            cache.put(cacheKey, new CacheEntry(immutable));
            return immutable;
        });
    }

    public void invalidateCache() {
        cache.clear();
    }
}
