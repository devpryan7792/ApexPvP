package com.apexpvp.profile;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe statistics container for a player within a specific Kit.
 */
public class PlayerStats {

    private final String kitId;
    private final AtomicInteger wins;
    private final AtomicInteger losses;
    private final AtomicInteger kills;
    private final AtomicInteger deaths;

    public PlayerStats(@NotNull String kitId) {
        this(kitId, 0, 0, 0, 0);
    }

    public PlayerStats(@NotNull String kitId, int wins, int losses, int kills, int deaths) {
        this.kitId = Objects.requireNonNull(kitId, "kitId cannot be null").toLowerCase();
        this.wins = new AtomicInteger(Math.max(0, wins));
        this.losses = new AtomicInteger(Math.max(0, losses));
        this.kills = new AtomicInteger(Math.max(0, kills));
        this.deaths = new AtomicInteger(Math.max(0, deaths));
    }

    @NotNull
    public String getKitId() {
        return kitId;
    }

    public int getWins() {
        return wins.get();
    }

    public int getLosses() {
        return losses.get();
    }

    public int getKills() {
        return kills.get();
    }

    public int getDeaths() {
        return deaths.get();
    }

    public void incrementWins() {
        wins.incrementAndGet();
    }

    public void incrementLosses() {
        losses.incrementAndGet();
    }

    public void incrementKills() {
        kills.incrementAndGet();
    }

    public void incrementDeaths() {
        deaths.incrementAndGet();
    }

    public int getTotalMatches() {
        return wins.get() + losses.get();
    }

    public double getWinRate() {
        int total = getTotalMatches();
        if (total == 0) return 0.0;
        return ((double) wins.get() / total) * 100.0;
    }

    public double getKDR() {
        int d = deaths.get();
        if (d == 0) return kills.get();
        return (double) kills.get() / d;
    }
}
