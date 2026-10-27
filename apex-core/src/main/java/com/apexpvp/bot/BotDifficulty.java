package com.apexpvp.bot;

import org.jetbrains.annotations.NotNull;

/**
 * Combat difficulty levels for Citizens 2 Practice Bots.
 */
public enum BotDifficulty {
    EASY("Easy", 6, 8, 3.0, 5.0, 0.1, 6),
    MEDIUM("Medium", 9, 11, 3.0, 7.0, 0.4, 4),
    HARD("Hard", 12, 14, 3.0, 8.0, 0.75, 2),
    HACKER("Hacker", 16, 20, 3.0, 8.0, 0.95, 1);

    private final String displayName;
    private final int minCps;
    private final int maxCps;
    private final double reachLimit;
    private final double healHpThreshold;
    private final double sprintResetProbability;
    private final int reactionDelayTicks;

    BotDifficulty(@NotNull String displayName,
                  int minCps,
                  int maxCps,
                  double reachLimit,
                  double healHpThreshold,
                  double sprintResetProbability,
                  int reactionDelayTicks) {
        this.displayName = displayName;
        this.minCps = minCps;
        this.maxCps = maxCps;
        this.reachLimit = reachLimit;
        this.healHpThreshold = healHpThreshold;
        this.sprintResetProbability = sprintResetProbability;
        this.reactionDelayTicks = reactionDelayTicks;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    public int getMinCps() {
        return minCps;
    }

    public int getMaxCps() {
        return maxCps;
    }

    public double getReachLimit() {
        return reachLimit;
    }

    public double getHealHpThreshold() {
        return healHpThreshold;
    }

    public double getSprintResetProbability() {
        return sprintResetProbability;
    }

    public int getReactionDelayTicks() {
        return reactionDelayTicks;
    }

    @NotNull
    public static BotDifficulty fromString(@NotNull String raw) {
        for (BotDifficulty diff : values()) {
            if (diff.name().equalsIgnoreCase(raw) || diff.displayName.equalsIgnoreCase(raw)) {
                return diff;
            }
        }
        return MEDIUM; // Default fallback
    }
}
