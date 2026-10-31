package com.apexpvp.profile;

import com.apexpvp.database.DatabaseManager;
import com.apexpvp.database.DatabaseType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Asynchronous profile repository and caching manager.
 */
public class ProfileManager {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerProfile> profileCache;

    public ProfileManager(@NotNull JavaPlugin plugin, @NotNull DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.profileCache = new ConcurrentHashMap<>();

        // Start periodic auto-save task (every 5 minutes) via Paper Async Scheduler
        startAutoSaveTask();
    }

    @Nullable
    public PlayerProfile getProfile(@NotNull UUID uuid) {
        return profileCache.get(uuid);
    }

    @Nullable
    public PlayerProfile getProfile(@NotNull Player player) {
        return getProfile(player.getUniqueId());
    }

    public void registerProfile(@NotNull PlayerProfile profile) {
        profileCache.put(profile.getUuid(), profile);
    }

    public void unloadProfile(@NotNull UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            saveProfile(profile);
        }
    }

    /**
     * Loads a player profile from the database asynchronously.
     * If the profile does not exist, a default record is inserted.
     */
    @NotNull
    public CompletableFuture<PlayerProfile> loadProfile(@NotNull UUID uuid, @NotNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile cached = profileCache.get(uuid);
            if (cached != null) {
                cached.setUsername(username);
                return cached;
            }

            try (Connection conn = databaseManager.getConnection()) {
                PlayerProfile profile = null;

                // 1. Fetch main player record
                String queryPlayer = "SELECT username, current_rank, elo, coins, current_streak, best_streak, loss_streak, worst_loss_streak, allow_duel_requests FROM apex_players WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(queryPlayer)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Rank rank = Rank.fromString(rs.getString("current_rank"));
                            int elo = rs.getInt("elo");
                            double coins = rs.getDouble("coins");
                            int curStreak = 0;
                            int bestStreak = 0;
                            int curLossStreak = 0;
                            int worstLossStreak = 0;
                            boolean allowDuelReq = true;
                            try {
                                curStreak = rs.getInt("current_streak");
                                bestStreak = rs.getInt("best_streak");
                                curLossStreak = rs.getInt("loss_streak");
                                worstLossStreak = rs.getInt("worst_loss_streak");
                                allowDuelReq = rs.getBoolean("allow_duel_requests");
                            } catch (SQLException ignored) {}
                            profile = new PlayerProfile(uuid, username, rank, elo, coins, curStreak, bestStreak, curLossStreak, worstLossStreak, allowDuelReq);
                        }
                    }
                }

                // If not found in DB, initialize new profile and persist
                if (profile == null) {
                    profile = new PlayerProfile(uuid, username);
                    insertNewPlayer(conn, profile);
                }

                // 2. Fetch kit statistics
                String queryStats = "SELECT kit_id, wins, losses, kills, deaths FROM apex_player_stats WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(queryStats)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String kitId = rs.getString("kit_id");
                            int wins = rs.getInt("wins");
                            int losses = rs.getInt("losses");
                            int kills = rs.getInt("kills");
                            int deaths = rs.getInt("deaths");
                            profile.putStats(new PlayerStats(kitId, wins, losses, kills, deaths));
                        }
                    }
                }

                // 3. Fetch custom hotbar layouts
                String queryLayouts = "SELECT kit_id, serialized_layout FROM apex_player_kit_layouts WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(queryLayouts)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String kitId = rs.getString("kit_id");
                            String layout = rs.getString("serialized_layout");
                            profile.setCustomHotbar(kitId, layout);
                        }
                    }
                }

                profile.markClean();
                profileCache.put(uuid, profile);
                return profile;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player profile for " + username + " (" + uuid + ")", e);
                // Return a fallback profile in memory to prevent login freezes
                PlayerProfile fallback = new PlayerProfile(uuid, username);
                profileCache.put(uuid, fallback);
                return fallback;
            }
        });
    }

    private void insertNewPlayer(@NotNull Connection conn, @NotNull PlayerProfile profile) throws SQLException {
        String insertSql = "INSERT INTO apex_players (uuid, username, current_rank, elo, coins, current_streak, best_streak, loss_streak, worst_loss_streak, allow_duel_requests) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, profile.getUuid().toString());
            ps.setString(2, profile.getUsername());
            ps.setString(3, profile.getRank().name());
            ps.setInt(4, profile.getElo());
            ps.setDouble(5, profile.getCoins());
            ps.setInt(6, profile.getCurrentStreak());
            ps.setInt(7, profile.getBestStreak());
            ps.setInt(8, profile.getCurrentLossStreak());
            ps.setInt(9, profile.getWorstLossStreak());
            ps.setBoolean(10, profile.isAllowDuelRequests());
            ps.executeUpdate();
        }
    }

    /**
     * Saves a player profile to the database asynchronously using dialect-specific upserts.
     */
    @NotNull
    public CompletableFuture<Void> saveProfile(@NotNull PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    savePlayerInternal(conn, profile);
                    saveStatsInternal(conn, profile);
                    saveLayoutsInternal(conn, profile);

                    conn.commit();
                    profile.markClean();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save profile for " + profile.getUsername(), e);
            }
        });
    }

    private void savePlayerInternal(@NotNull Connection conn, @NotNull PlayerProfile profile) throws SQLException {
        DatabaseType type = databaseManager.getDatabaseType();
        String sql = switch (type) {
            case SQLITE, POSTGRESQL -> """
                INSERT INTO apex_players (uuid, username, current_rank, elo, coins, current_streak, best_streak, loss_streak, worst_loss_streak, allow_duel_requests, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(uuid) DO UPDATE SET
                    username = EXCLUDED.username,
                    current_rank = EXCLUDED.current_rank,
                    elo = EXCLUDED.elo,
                    coins = EXCLUDED.coins,
                    current_streak = EXCLUDED.current_streak,
                    best_streak = EXCLUDED.best_streak,
                    loss_streak = EXCLUDED.loss_streak,
                    worst_loss_streak = EXCLUDED.worst_loss_streak,
                    allow_duel_requests = EXCLUDED.allow_duel_requests,
                    last_seen = CURRENT_TIMESTAMP;
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO apex_players (uuid, username, current_rank, elo, coins, current_streak, best_streak, loss_streak, worst_loss_streak, allow_duel_requests, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    username = VALUES(username),
                    current_rank = VALUES(current_rank),
                    elo = VALUES(elo),
                    coins = VALUES(coins),
                    current_streak = VALUES(current_streak),
                    best_streak = VALUES(best_streak),
                    loss_streak = VALUES(loss_streak),
                    worst_loss_streak = VALUES(worst_loss_streak),
                    allow_duel_requests = VALUES(allow_duel_requests),
                    last_seen = CURRENT_TIMESTAMP;
                """;
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getUuid().toString());
            ps.setString(2, profile.getUsername());
            ps.setString(3, profile.getRank().name());
            ps.setInt(4, profile.getElo());
            ps.setDouble(5, profile.getCoins());
            ps.setInt(6, profile.getCurrentStreak());
            ps.setInt(7, profile.getBestStreak());
            ps.setInt(8, profile.getCurrentLossStreak());
            ps.setInt(9, profile.getWorstLossStreak());
            ps.setBoolean(10, profile.isAllowDuelRequests());
            ps.executeUpdate();
        }
    }

    private void saveStatsInternal(@NotNull Connection conn, @NotNull PlayerProfile profile) throws SQLException {
        if (profile.getAllStats().isEmpty()) return;

        DatabaseType type = databaseManager.getDatabaseType();
        String sql = switch (type) {
            case SQLITE, POSTGRESQL -> """
                INSERT INTO apex_player_stats (uuid, kit_id, wins, losses, kills, deaths)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, kit_id) DO UPDATE SET
                    wins = EXCLUDED.wins,
                    losses = EXCLUDED.losses,
                    kills = EXCLUDED.kills,
                    deaths = EXCLUDED.deaths;
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO apex_player_stats (uuid, kit_id, wins, losses, kills, deaths)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    wins = VALUES(wins),
                    losses = VALUES(losses),
                    kills = VALUES(kills),
                    deaths = VALUES(deaths);
                """;
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PlayerStats stats : profile.getAllStats().values()) {
                ps.setString(1, profile.getUuid().toString());
                ps.setString(2, stats.getKitId());
                ps.setInt(3, stats.getWins());
                ps.setInt(4, stats.getLosses());
                ps.setInt(5, stats.getKills());
                ps.setInt(6, stats.getDeaths());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void saveLayoutsInternal(@NotNull Connection conn, @NotNull PlayerProfile profile) throws SQLException {
        if (profile.getAllCustomHotbars().isEmpty()) return;

        DatabaseType type = databaseManager.getDatabaseType();
        String sql = switch (type) {
            case SQLITE, POSTGRESQL -> """
                INSERT INTO apex_player_kit_layouts (uuid, kit_id, serialized_layout, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(uuid, kit_id) DO UPDATE SET
                    serialized_layout = EXCLUDED.serialized_layout,
                    updated_at = CURRENT_TIMESTAMP;
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO apex_player_kit_layouts (uuid, kit_id, serialized_layout, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    serialized_layout = VALUES(serialized_layout),
                    updated_at = CURRENT_TIMESTAMP;
                """;
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : profile.getAllCustomHotbars().entrySet()) {
                ps.setString(1, profile.getUuid().toString());
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Resets and deletes a custom hotbar layout for a player in memory and database.
     */
    @NotNull
    public CompletableFuture<Void> resetCustomHotbar(@NotNull UUID uuid, @NotNull String kitId) {
        PlayerProfile profile = getProfile(uuid);
        if (profile != null) {
            profile.removeCustomHotbar(kitId);
        }
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = "DELETE FROM apex_player_kit_layouts WHERE uuid = ? AND kit_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, kitId.toLowerCase());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete kit layout for " + uuid + " (" + kitId + ")", e);
            }
        });
    }

    /**
     * Flushes all cached dirty profiles to the database synchronously on server shutdown.
     */
    public void saveAllSync() {
        plugin.getLogger().info("Flushing " + profileCache.size() + " player profiles to database...");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PlayerProfile profile : profileCache.values()) {
            if (profile.isDirty()) {
                futures.add(saveProfile(profile));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        plugin.getLogger().info("Player profiles flush complete.");
    }

    private void startAutoSaveTask() {
        try {
            // Runs every 5 minutes asynchronously
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
                int savedCount = 0;
                for (PlayerProfile profile : profileCache.values()) {
                    if (profile.isDirty()) {
                        saveProfile(profile);
                        savedCount++;
                    }
                }
                if (savedCount > 0) {
                    plugin.getLogger().info("Auto-saved " + savedCount + " dirty player profiles.");
                }
            }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Throwable ignored) {
            // Server not initialized (e.g. in headless unit tests)
        }
    }

    /**
     * Checks if a player's current ELO qualifies them for a higher rank tier,
     * and automatically promotes them with title and sound celebrations.
     */
    public boolean checkAndApplyAutoRankup(@NotNull PlayerProfile profile, @Nullable Player player) {
        Rank currentRank = profile.getRank();
        Rank targetRank = Rank.fromElo(profile.getElo());
        if (targetRank.ordinal() > currentRank.ordinal()) {
            profile.setRank(targetRank);
            if (player != null && player.isOnline()) {
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        com.apexpvp.util.TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>RANK PROMOTION!</bold></gradient>"),
                        com.apexpvp.util.TextUtil.parse("<yellow>You reached " + targetRank.getDisplayName() + "!</yellow>"),
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ofMillis(100),
                                java.time.Duration.ofMillis(2500),
                                java.time.Duration.ofMillis(500)
                        )
                );
                player.showTitle(title);
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                com.apexpvp.util.TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>AUTOMATIC PROMOTION!</bold></gradient> Your ELO reached <gold>" +
                        profile.getElo() + "</gold>, advancing you to " + targetRank.getMiniMessageDisplay() + "!");
            }
            if (plugin instanceof com.apexpvp.ApexPvPPlugin apex && apex.getLobbyBossBarManager() != null && player != null) {
                apex.getLobbyBossBarManager().update(player);
            }
            try {
                plugin.getServer().broadcast(com.apexpvp.util.TextUtil.prefixed("<gold>" + profile.getUsername() + "</gold> reached rank " + targetRank.getMiniMessageDisplay() + "<reset>!"));
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }
}
