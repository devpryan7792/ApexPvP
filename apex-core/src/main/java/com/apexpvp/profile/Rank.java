package com.apexpvp.profile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 20-tier competitive hierarchy for ApexPvP.
 * Combines 5 Main Tiers with 4 Sub-Ranks each.
 */
public enum Rank {

    // COPPER TIER
    COPPER_1(RankTier.COPPER, SubRank.ONE, 800, 0.0),
    COPPER_2(RankTier.COPPER, SubRank.TWO, 900, 50.0),
    COPPER_3(RankTier.COPPER, SubRank.THREE, 1000, 100.0),

    // IRON TIER
    IRON_1(RankTier.IRON, SubRank.ONE, 1100, 175.0),
    IRON_2(RankTier.IRON, SubRank.TWO, 1200, 250.0),
    IRON_3(RankTier.IRON, SubRank.THREE, 1300, 350.0),

    // GOLD TIER
    GOLD_1(RankTier.GOLD, SubRank.ONE, 1400, 500.0),
    GOLD_2(RankTier.GOLD, SubRank.TWO, 1500, 650.0),
    GOLD_3(RankTier.GOLD, SubRank.THREE, 1600, 800.0),

    // DIAMOND TIER
    DIAMOND_1(RankTier.DIAMOND, SubRank.ONE, 1700, 1000.0),
    DIAMOND_2(RankTier.DIAMOND, SubRank.TWO, 1800, 1250.0),
    DIAMOND_3(RankTier.DIAMOND, SubRank.THREE, 1900, 1500.0),

    // EMERALD TIER
    EMERALD_1(RankTier.EMERALD, SubRank.ONE, 2000, 1800.0),
    EMERALD_2(RankTier.EMERALD, SubRank.TWO, 2150, 2200.0),
    EMERALD_3(RankTier.EMERALD, SubRank.THREE, 2300, 2600.0),

    // NETHERITE TIER
    NETHERITE_1(RankTier.NETHERITE, SubRank.ONE, 2450, 3200.0),
    NETHERITE_2(RankTier.NETHERITE, SubRank.TWO, 2600, 4000.0),
    NETHERITE_3(RankTier.NETHERITE, SubRank.THREE, 2800, 5000.0);

    private final RankTier tier;
    private final SubRank subRank;
    private final int eloThreshold;
    private final double rankupCost;

    Rank(@NotNull RankTier tier, @NotNull SubRank subRank, int eloThreshold, double rankupCost) {
        this.tier = tier;
        this.subRank = subRank;
        this.eloThreshold = eloThreshold;
        this.rankupCost = rankupCost;
    }

    @NotNull
    public RankTier getTier() {
        return tier;
    }

    @NotNull
    public SubRank getSubRank() {
        return subRank;
    }

    public int getEloThreshold() {
        return eloThreshold;
    }

    public double getRankupCost() {
        return rankupCost;
    }

    @NotNull
    public String getDisplayName() {
        return tier.getDisplayName() + " " + subRank.getAbbreviation();
    }

    @NotNull
    public String getMiniMessageDisplay() {
        return "<color:" + tier.getHexColor() + "><bold>" + tier.getDisplayName() + "</bold> " + subRank.getAbbreviation() + "</color>";
    }

    @NotNull
    public String getMiniMessageBadge() {
        return "<color:" + tier.getHexColor() + ">[" + tier.getDisplayName().substring(0, 1) + "-" + subRank.getAbbreviation() + "]</color>";
    }

    @Nullable
    public Rank getNextRank() {
        int nextOrdinal = this.ordinal() + 1;
        Rank[] allRanks = values();
        if (nextOrdinal < allRanks.length) {
            return allRanks[nextOrdinal];
        }
        return null; // Already at the pinnacle rank
    }

    @Nullable
    public Rank getPreviousRank() {
        int prevOrdinal = this.ordinal() - 1;
        if (prevOrdinal >= 0) {
            return values()[prevOrdinal];
        }
        return null; // At the lowest rank
    }

    @NotNull
    public static Rank fromElo(int elo) {
        Rank bestMatch = COPPER_1;
        for (Rank rank : values()) {
            if (elo >= rank.getEloThreshold()) {
                bestMatch = rank;
            } else {
                break;
            }
        }
        return bestMatch;
    }

    @NotNull
    public static Rank fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return COPPER_1;
        }
        String upper = raw.toUpperCase().trim();
        // Legacy backward-compatibility mapping
        switch (upper) {
            case "IRON_ST" -> { return IRON_1; }
            case "IRON_LT" -> { return IRON_2; }
            case "IRON_MT", "IRON_HT" -> { return IRON_3; }
            case "GOLD_ST" -> { return GOLD_1; }
            case "GOLD_LT" -> { return GOLD_2; }
            case "GOLD_MT", "GOLD_HT" -> { return GOLD_3; }
            case "DIAMOND_ST" -> { return DIAMOND_1; }
            case "DIAMOND_LT" -> { return DIAMOND_2; }
            case "DIAMOND_MT", "DIAMOND_HT" -> { return DIAMOND_3; }
            case "EMERALD_ST" -> { return EMERALD_1; }
            case "EMERALD_LT" -> { return EMERALD_2; }
            case "EMERALD_MT", "EMERALD_HT" -> { return EMERALD_3; }
            case "NETHERITE_ST" -> { return NETHERITE_1; }
            case "NETHERITE_LT" -> { return NETHERITE_2; }
            case "NETHERITE_MT", "NETHERITE_HT" -> { return NETHERITE_3; }
            default -> {}
        }
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            return COPPER_1;
        }
    }
}
