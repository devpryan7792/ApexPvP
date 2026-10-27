package com.apexpvp.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchStateTest {

    @Test
    @DisplayName("Verify match lifecycle states and combat/freeze boundaries")
    void testStateFlags() {
        assertTrue(MatchState.PREPARING.isFrozen());
        assertFalse(MatchState.PREPARING.isCombatAllowed());

        assertTrue(MatchState.COUNTDOWN.isFrozen());
        assertFalse(MatchState.COUNTDOWN.isCombatAllowed());

        assertFalse(MatchState.ACTIVE.isFrozen());
        assertTrue(MatchState.ACTIVE.isCombatAllowed());

        assertFalse(MatchState.ENDING.isFrozen());
        assertFalse(MatchState.ENDING.isCombatAllowed());

        assertFalse(MatchState.CLEANUP.isFrozen());
        assertFalse(MatchState.CLEANUP.isCombatAllowed());
    }
}
