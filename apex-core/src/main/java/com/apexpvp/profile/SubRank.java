package com.apexpvp.profile;

import org.jetbrains.annotations.NotNull;

/**
 * Sub-rank divisions within each main competitive tier.
 * Ordered in ascending difficulty: ST -> LT -> MT -> HT.
 */
public enum SubRank {
    ONE("Division 1", "1", 1),
    TWO("Division 2", "2", 2),
    THREE("Division 3", "3", 3);

    private final String fullName;
    private final String abbreviation;
    private final int level;

    SubRank(@NotNull String fullName, @NotNull String abbreviation, int level) {
        this.fullName = fullName;
        this.abbreviation = abbreviation;
        this.level = level;
    }

    @NotNull
    public String getFullName() {
        return fullName;
    }

    @NotNull
    public String getAbbreviation() {
        return abbreviation;
    }

    public int getLevel() {
        return level;
    }
}
