package com.apexpvp.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotDifficultyTest {

    @Test
    @DisplayName("Verify bot difficulty settings, ascending CPS, and strict 3.0 reach cap")
    void testBotDifficulties() {
        for (BotDifficulty diff : BotDifficulty.values()) {
            assertEquals(3.0, diff.getReachLimit(), "All bots must strictly respect 3.0 block reach limit: " + diff);
            assertTrue(diff.getMinCps() <= diff.getMaxCps(), "Min CPS must not exceed Max CPS for: " + diff);
            assertTrue(diff.getHealHpThreshold() > 0.0, "Heal threshold must be positive: " + diff);
        }

        // Verify CPS monotonicity across difficulties
        assertTrue(BotDifficulty.EASY.getMaxCps() <= BotDifficulty.MEDIUM.getMinCps() || BotDifficulty.EASY.getMaxCps() <= BotDifficulty.MEDIUM.getMaxCps());
        assertTrue(BotDifficulty.MEDIUM.getMaxCps() <= BotDifficulty.HARD.getMaxCps());
        assertTrue(BotDifficulty.HARD.getMaxCps() <= BotDifficulty.HACKER.getMaxCps());
        assertEquals(20, BotDifficulty.HACKER.getMaxCps(), "Hacker bot must reach 20 CPS");
    }

    @Test
    @DisplayName("Verify string parsing fallback")
    void testParsing() {
        assertEquals(BotDifficulty.HACKER, BotDifficulty.fromString("hacker"));
        assertEquals(BotDifficulty.EASY, BotDifficulty.fromString("easy"));
        assertEquals(BotDifficulty.MEDIUM, BotDifficulty.fromString("unknown_diff"));
    }
}
