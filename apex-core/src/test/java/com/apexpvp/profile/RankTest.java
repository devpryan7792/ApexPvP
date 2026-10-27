package com.apexpvp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RankTest {

    @Test
    @DisplayName("Verify exactly 18 competitive sub-ranks exist across 6 tiers in ascending order")
    void testRankCountAndOrder() {
        Rank[] ranks = Rank.values();
        assertEquals(18, ranks.length, "ApexPvP must have exactly 18 divisions (6 tiers * 3 divisions)");

        assertEquals(Rank.COPPER_1, ranks[0]);
        assertEquals(Rank.NETHERITE_3, ranks[17]);

        for (int i = 0; i < ranks.length - 1; i++) {
            assertTrue(
                ranks[i].getEloThreshold() <= ranks[i + 1].getEloThreshold(),
                "Rank thresholds must be strictly monotonically non-decreasing: " + ranks[i] + " vs " + ranks[i + 1]
            );
            assertEquals(ranks[i + 1], ranks[i].getNextRank());
            assertEquals(ranks[i], ranks[i + 1].getPreviousRank());
        }

        assertNull(Rank.NETHERITE_3.getNextRank(), "Pinnacle rank must have no next rank");
        assertNull(Rank.COPPER_1.getPreviousRank(), "Starting rank must have no previous rank");
    }

    @Test
    @DisplayName("Verify ELO mapping to corresponding Ranks")
    void testEloMapping() {
        assertEquals(Rank.COPPER_1, Rank.fromElo(0));
        assertEquals(Rank.COPPER_1, Rank.fromElo(800));
        assertEquals(Rank.COPPER_2, Rank.fromElo(900));
        assertEquals(Rank.COPPER_3, Rank.fromElo(1000));
        assertEquals(Rank.IRON_1, Rank.fromElo(1100));
        assertEquals(Rank.GOLD_1, Rank.fromElo(1400));
        assertEquals(Rank.DIAMOND_1, Rank.fromElo(1700));
        assertEquals(Rank.EMERALD_1, Rank.fromElo(2000));
        assertEquals(Rank.NETHERITE_1, Rank.fromElo(2450));
        assertEquals(Rank.NETHERITE_3, Rank.fromElo(2800));
        assertEquals(Rank.NETHERITE_3, Rank.fromElo(3500)); // Beyond pinnacle
    }

    @Test
    @DisplayName("Verify backward compatibility parsing for legacy rank strings")
    void testLegacyStringMigration() {
        assertEquals(Rank.IRON_1, Rank.fromString("IRON_ST"));
        assertEquals(Rank.IRON_2, Rank.fromString("IRON_LT"));
        assertEquals(Rank.IRON_3, Rank.fromString("IRON_MT"));
        assertEquals(Rank.IRON_3, Rank.fromString("IRON_HT"));
        assertEquals(Rank.NETHERITE_3, Rank.fromString("NETHERITE_HT"));
        assertEquals(Rank.COPPER_1, Rank.fromString("UNKNOWN_VALUE"));
        assertEquals(Rank.COPPER_1, Rank.fromString(null));
    }
}
