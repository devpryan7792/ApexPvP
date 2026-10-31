package com.apexpvp.queue;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an active ticket in the competitive matchmaking queue.
 */
public class QueueTicket {

    private final UUID playerId; // Leader or solo player
    private final List<UUID> partyMembers;
    private final int teamSize;
    private final String kitId;
    private final int initialElo;
    private final AtomicInteger currentSearchRange;
    private final long joinTimestamp;
    private volatile long lastExpansionTimestamp;

    public QueueTicket(@NotNull UUID playerId,
                       @NotNull String kitId,
                       int initialElo,
                       int initialRange) {
        this(playerId, java.util.Collections.singletonList(playerId), 1, kitId, initialElo, initialRange);
    }

    public QueueTicket(@NotNull UUID leaderId,
                       @NotNull List<UUID> partyMembers,
                       int teamSize,
                       @NotNull String kitId,
                       int initialElo,
                       int initialRange) {
        this.playerId = Objects.requireNonNull(leaderId, "leaderId cannot be null");
        this.partyMembers = List.copyOf(Objects.requireNonNull(partyMembers, "partyMembers cannot be null"));
        this.teamSize = Math.max(1, teamSize);
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
     * Same team size (1v1, 2v2, 3v3, 4v4), distinct parties, same kit,
     * and ELO delta falls within BOTH tickets' current search radiuses.
     */
    public boolean matches(@NotNull QueueTicket other) {
        if (this.teamSize != other.teamSize) return false;
        if (this.playerId.equals(other.playerId)) return false;
        if (!java.util.Collections.disjoint(this.partyMembers, other.partyMembers)) return false;
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

    public int getTeamSize() {
        return teamSize;
    }

    @NotNull
    public List<UUID> getPartyMembers() {
        return partyMembers;
    }

    public boolean isTeamQueue() {
        return teamSize > 1;
    }
}
