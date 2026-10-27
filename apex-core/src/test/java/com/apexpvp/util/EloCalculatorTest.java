package com.apexpvp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EloCalculator Mathematical & Dynamic K-Factor Tests")
public class EloCalculatorTest {

    @Test
    @DisplayName("Verify expected score between equally rated opponents is 0.5")
    void testEqualRatingExpectedScore() {
        double expected = EloCalculator.getExpectedScore(1200, 1200);
        assertEquals(0.5, expected, 0.0001, "Expected score for equal ratings must be exactly 0.5");

        int delta = EloCalculator.calculateDelta(1200, 1200, 32);
        assertEquals(16, delta, "Delta for equal ratings at K=32 must be 16");
    }

    @Test
    @DisplayName("Verify upset victories yield higher ELO gains than expected wins")
    void testUpsetVictoryGains() {
        int underdog = 1000;
        int favorite = 1400;

        int upsetDelta = EloCalculator.calculateDelta(underdog, favorite, 32);
        int expectedWinDelta = EloCalculator.calculateDelta(favorite, underdog, 32);

        assertTrue(upsetDelta > expectedWinDelta,
                "Underdog victory should reward significantly more ELO than favorite victory");
        assertTrue(upsetDelta >= 28, "Upset delta against +400 ELO should be >= 28 points");
        assertTrue(expectedWinDelta <= 4, "Favorite win against -400 ELO should be <= 4 points");
    }

    @Test
    @DisplayName("Verify minimum ELO delta is at least 1 point even in extreme mismatches")
    void testMinimumDeltaClamping() {
        int extremeFavorite = 3000;
        int extremeUnderdog = 500;

        int delta = EloCalculator.calculateDelta(extremeFavorite, extremeUnderdog, 32);
        assertEquals(1, delta, "Delta must clamp to at least 1 point");
    }

    @Test
    @DisplayName("Verify dynamic K-factor selection based on match count and ELO threshold")
    void testDynamicKFactorTransitions() {
        // Provisional player (< 15 matches)
        assertEquals(EloCalculator.PROVISIONAL_K, EloCalculator.getKFactor(1000, 0));
        assertEquals(EloCalculator.PROVISIONAL_K, EloCalculator.getKFactor(1000, 14));

        // Established player (>= 15 matches, < 2000 ELO)
        assertEquals(EloCalculator.DEFAULT_K, EloCalculator.getKFactor(1200, 15));
        assertEquals(EloCalculator.DEFAULT_K, EloCalculator.getKFactor(1999, 100));

        // Master player (>= 2000 ELO)
        assertEquals(EloCalculator.MASTER_K, EloCalculator.getKFactor(2000, 50));
        assertEquals(EloCalculator.MASTER_K, EloCalculator.getKFactor(2500, 200));
    }

    @Test
    @DisplayName("Verify monotonic scaling: higher opponent ELO strictly increases delta")
    void testMonotonicDeltaScaling() {
        int winner = 1200;
        int lastDelta = 0;

        for (int opponentElo = 800; opponentElo <= 1600; opponentElo += 100) {
            int delta = EloCalculator.calculateDelta(winner, opponentElo, 32);
            assertTrue(delta >= lastDelta, "Delta must monotonically increase as opponent ELO rises");
            lastDelta = delta;
        }
    }

    @Test
    @DisplayName("Verify calculateDynamicDelta applies provisional K-factor for new players")
    void testDynamicDeltaProvisional() {
        int provisionalDelta = EloCalculator.calculateDynamicDelta(1000, 1000, 5); // 5 matches -> K=48 -> delta=24
        int establishedDelta = EloCalculator.calculateDynamicDelta(1000, 1000, 20); // 20 matches -> K=32 -> delta=16
        assertEquals(24, provisionalDelta);
        assertEquals(16, establishedDelta);
    }
}
