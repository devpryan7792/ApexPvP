package com.apexpvp.util;

/**
 * Enterprise-grade ELO rating calculator with dynamic K-factors.
 * Supports provisional calibration for new players and stabilization for high-tier masters.
 */
public final class EloCalculator {

    public static final int DEFAULT_K = 32;
    public static final int PROVISIONAL_K = 48;
    public static final int MASTER_K = 16;
    public static final int PROVISIONAL_MATCH_THRESHOLD = 15;
    public static final int MASTER_ELO_THRESHOLD = 2000;

    private EloCalculator() {
        // Utility class
    }

    /**
     * Calculates the expected winning probability for player A against player B.
     * Formula: 1 / (1 + 10 ^ ((eloB - eloA) / 400))
     *
     * @param eloA Player A's current rating
     * @param eloB Player B's current rating
     * @return Expected score between 0.0 and 1.0
     */
    public static double getExpectedScore(int eloA, int eloB) {
        return 1.0 / (1.0 + Math.pow(10.0, (eloB - eloA) / 400.0));
    }

    /**
     * Determines the appropriate K-factor based on player experience and current rating.
     *
     * @param elo Current player rating
     * @param totalMatches Total ranked matches completed
     * @return Dynamic K-factor (48 for provisional, 16 for master, 32 default)
     */
    public static int getKFactor(int elo, int totalMatches) {
        if (totalMatches < PROVISIONAL_MATCH_THRESHOLD) {
            return PROVISIONAL_K;
        }
        if (elo >= MASTER_ELO_THRESHOLD) {
            return MASTER_K;
        }
        return DEFAULT_K;
    }

    /**
     * Calculates the ELO delta awarded to the winner (and deducted from the loser)
     * using dynamic K-factor based on matches played.
     *
     * @param winnerElo Rating of the victor
     * @param loserElo Rating of the defeated player
     * @param winnerMatches Total matches played by the victor (for provisional scaling)
     * @return Positive ELO point delta (at least 1 point)
     */
    public static int calculateDynamicDelta(int winnerElo, int loserElo, int winnerMatches) {
        return calculateDelta(winnerElo, loserElo, getKFactor(winnerElo, winnerMatches));
    }

    /**
     * Calculates the ELO delta with an explicit K-factor.
     */
    public static int calculateDelta(int winnerElo, int loserElo, int kFactor) {
        double expectedWinner = getExpectedScore(winnerElo, loserElo);
        long delta = Math.round(kFactor * (1.0 - expectedWinner));
        return (int) Math.max(1, delta);
    }
}
