package com.apexpvp.queue;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an active ticket in the competitive matchmaking queue.
 */
public class QueueTicket {

    private final UUID playerId;
    private final String kitId;
    private final int initialElo;
    private final AtomicInteger currentSearchRange;
    private final long joinTimestamp;
    private volatile long lastExpansionTimestamp;

    public QueueTicket(@NotNull UUID playerId,
                       @NotNull String kitId,
                       int initialElo,
                       int initialRange) {
        this.playerId = Objects.requireNonNull(playerId, "playerId cannot be null");
        this.kitId = Objects.requireNonNull(kitId, "kitId cannot be null").toLowerCase();
        this.initialElo = initialElo;
        this.currentSearchRange = new AtomicInteger(Math.max(10, initialRange));
        this.joinTimestamp = System.currentTimeMillis();
        this.lastExpansionTimestamp = System.currentTimeMillis();
    }

    public void expandRange(int step) {
        this.currentSearchRange.addAndGet(Math.max(1, step));
        this.lastExpansionTimestamp = System.currentTimeMillis();
    }

    /**
     * Verifies mutual compatibility:
     * Same kit, distinct players, and ELO delta falls within BOTH tickets' current search radiuses.
     */
    public boolean matches(@NotNull QueueTicket other) {
        if (this.playerId.equals(other.playerId)) return false;
        if (!this.kitId.equalsIgnoreCase(other.kitId)) return false;

        int eloDifference = Math.abs(this.initialElo - other.initialElo);
        return eloDifference <= this.currentSearchRange.get() && eloDifference <= other.currentSearchRange.get();
    }

    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    @NotNull
    public String getKitId() {
        return kitId;
    }

    public int getInitialElo() {
        return initialElo;
    }

    public int getCurrentSearchRange() {
        return currentSearchRange.get();
    }

    public long getJoinTimestamp() {
        return joinTimestamp;
    }

    public long getLastExpansionTimestamp() {
        return lastExpansionTimestamp;
    }
}
