package com.apexpvp.match;

import org.jetbrains.annotations.NotNull;

/**
 * Deterministic lifecycle states for all matches in ApexPvP.
 */
public enum MatchState {
    /**
     * Snapshots player inventories, clears effects, applies kit, teleports to spawn points, freezes.
     */
    PREPARING,

    /**
     * 5-second countdown with titles and audio chimes.
     */
    COUNTDOWN,

    /**
     * Active combat phase. Damage, block tracking, and objectives enabled.
     */
    ACTIVE,

    /**
     * Match outcome determined. Winner declared, ELO/economy processed, 3-second delay.
     */
    ENDING,

    /**
     * Restores players to lobby state, triggers arena rollback, deallocates match.
     */
    CLEANUP;

    public boolean isCombatAllowed() {
        return this == ACTIVE;
    }

    public boolean isFrozen() {
        return this == PREPARING || this == COUNTDOWN;
    }
}
