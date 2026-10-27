package com.apexpvp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WinStreakTest {

    @Test
    @DisplayName("Verify streak increment, best streak retention, and reset")
    void testStreakLifecycle() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "StreakMaster", Rank.COPPER_1, 800, 0.0);

        assertEquals(0, profile.getCurrentStreak());
        assertEquals(0, profile.getBestStreak());

        assertEquals(1, profile.incrementStreak());
        assertEquals(1, profile.getCurrentStreak());
        assertEquals(1, profile.getBestStreak());

        profile.incrementStreak();
        profile.incrementStreak();
        profile.incrementStreak(); // 4
        assertEquals(4, profile.getCurrentStreak());
        assertEquals(4, profile.getBestStreak());

        profile.resetStreak();
        assertEquals(0, profile.getCurrentStreak());
        assertEquals(4, profile.getBestStreak(), "Best streak must be retained across resets");

        profile.incrementStreak();
        profile.incrementStreak(); // 2
        assertEquals(2, profile.getCurrentStreak());
        assertEquals(4, profile.getBestStreak());

        // Surpass previous best streak
        profile.incrementStreak();
        profile.incrementStreak();
        profile.incrementStreak(); // 5
        assertEquals(5, profile.getCurrentStreak());
        assertEquals(5, profile.getBestStreak());
    }

    @Test
    @DisplayName("Verify dynamic win streak multipliers")
    void testStreakMultipliers() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "MultiplierTester", Rank.COPPER_1, 800, 0.0);

        assertEquals(1.0, profile.getStreakMultiplier(), 0.001); // 0 wins

        profile.incrementStreak(); // 1
        assertEquals(1.0, profile.getStreakMultiplier(), 0.001);

        profile.incrementStreak(); // 2
        assertEquals(1.0, profile.getStreakMultiplier(), 0.001);

        profile.incrementStreak(); // 3
        assertEquals(1.25, profile.getStreakMultiplier(), 0.001);

        profile.incrementStreak(); // 4
        assertEquals(1.25, profile.getStreakMultiplier(), 0.001);

        profile.incrementStreak(); // 5
        assertEquals(1.50, profile.getStreakMultiplier(), 0.001);

        for (int i = 6; i <= 9; i++) {
            profile.incrementStreak();
            assertEquals(1.50, profile.getStreakMultiplier(), 0.001);
        }

        profile.incrementStreak(); // 10
        assertEquals(2.00, profile.getStreakMultiplier(), 0.001);

        profile.incrementStreak(); // 11
        assertEquals(2.00, profile.getStreakMultiplier(), 0.001);
    }
}
