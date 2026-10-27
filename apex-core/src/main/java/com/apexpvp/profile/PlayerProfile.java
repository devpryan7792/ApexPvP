package com.apexpvp.profile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe domain model representing a player's profile, competitive rating, and custom preferences.
 */
public class PlayerProfile {

    private final UUID uuid;
    private volatile String username;
    private volatile Rank rank;
    private final AtomicInteger elo;
    private volatile double coins;

    private final AtomicInteger currentStreak;
    private final AtomicInteger bestStreak;

    private final Map<String, PlayerStats> kitStats;
    private final Map<String, String> customHotbars;

    private final AtomicBoolean dirty;

    public PlayerProfile(@NotNull UUID uuid, @NotNull String username) {
        this(uuid, username, Rank.COPPER_1, 800, 0.0, 0, 0);
    }

    public PlayerProfile(@NotNull UUID uuid,
                         @NotNull String username,
                         @NotNull Rank rank,
                         int elo,
                         double coins) {
        this(uuid, username, rank, elo, coins, 0, 0);
    }

    public PlayerProfile(@NotNull UUID uuid,
                         @NotNull String username,
                         @NotNull Rank rank,
                         int elo,
                         double coins,
                         int currentStreak,
                         int bestStreak) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.rank = Objects.requireNonNull(rank, "rank cannot be null");
        this.elo = new AtomicInteger(Math.max(0, elo));
        this.coins = Math.max(0.0, coins);
        this.currentStreak = new AtomicInteger(Math.max(0, currentStreak));
        this.bestStreak = new AtomicInteger(Math.max(this.currentStreak.get(), bestStreak));

        this.kitStats = new ConcurrentHashMap<>();
        this.customHotbars = new ConcurrentHashMap<>();
        this.dirty = new AtomicBoolean(false);
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NotNull String username) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        markDirty();
    }

    @NotNull
    public Rank getRank() {
        return rank;
    }

    public void setRank(@NotNull Rank rank) {
        this.rank = Objects.requireNonNull(rank, "rank cannot be null");
        markDirty();
    }

    public int getElo() {
        return elo.get();
    }

    public void setElo(int newElo) {
        this.elo.set(Math.max(0, newElo));
        markDirty();
    }

    public int modifyElo(int delta) {
        int updated = elo.accumulateAndGet(delta, (current, change) -> Math.max(0, current + change));
        markDirty();
        return updated;
    }

    public double getCoins() {
        return coins;
    }

    public synchronized void setCoins(double coins) {
        this.coins = Math.max(0.0, coins);
        markDirty();
    }

    public synchronized void addCoins(double amount) {
        if (amount > 0) {
            this.coins += amount;
            markDirty();
        }
    }

    public synchronized boolean deductCoins(double amount) {
        if (amount <= 0) return true;
        if (this.coins >= amount) {
            this.coins -= amount;
            markDirty();
            return true;
        }
        return false;
    }

    public int getCurrentStreak() {
        return currentStreak.get();
    }

    public int getBestStreak() {
        return bestStreak.get();
    }

    public int incrementStreak() {
        int newStreak = currentStreak.incrementAndGet();
        bestStreak.accumulateAndGet(newStreak, Math::max);
        markDirty();
        return newStreak;
    }

    public void resetStreak() {
        currentStreak.set(0);
        markDirty();
    }

    public double getStreakMultiplier() {
        int streak = currentStreak.get();
        if (streak >= 10) return 2.00;
        if (streak >= 5) return 1.50;
        if (streak >= 3) return 1.25;
        return 1.00;
    }

    @NotNull
    public PlayerStats getOrCreateStats(@NotNull String kitId) {
        return kitStats.computeIfAbsent(kitId.toLowerCase(), PlayerStats::new);
    }

    public void putStats(@NotNull PlayerStats stats) {
        kitStats.put(stats.getKitId().toLowerCase(), stats);
        markDirty();
    }

    @NotNull
    public Map<String, PlayerStats> getAllStats() {
        return Collections.unmodifiableMap(kitStats);
    }

    @Nullable
    public String getCustomHotbar(@NotNull String kitId) {
        return customHotbars.get(kitId.toLowerCase());
    }

    public void setCustomHotbar(@NotNull String kitId, @NotNull String serializedLayout) {
        customHotbars.put(kitId.toLowerCase(), serializedLayout);
        markDirty();
    }

    @NotNull
    public Map<String, String> getAllCustomHotbars() {
        return Collections.unmodifiableMap(customHotbars);
    }

    public void markDirty() {
        this.dirty.set(true);
    }

    public void markClean() {
        this.dirty.set(false);
    }

    public boolean isDirty() {
        return dirty.get();
    }
}
