package com.apexpvp.match;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Tracks the match participant state, team alignment, and alive/spectator status.
 */
public class MatchParticipant {

    private final UUID uuid;
    private final String name;
    private final int teamIndex;
    private volatile boolean alive;
    private volatile boolean spectator;

    public MatchParticipant(@NotNull UUID uuid, @NotNull String name, int teamIndex) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.teamIndex = teamIndex;
        this.alive = true;
        this.spectator = false;
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public String getName() {
        return name;
    }

    public int getTeamIndex() {
        return teamIndex;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }
}
