package com.apexpvp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LossStreakTest {

    @Test
    @DisplayName("Verify loss streak initial state, increment, worst streak retention, and reset")
    void testLossStreakLifecycle() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "LossMaster", Rank.COPPER_1, 800, 0.0);

        assertEquals(0, profile.getCurrentLossStreak());
        assertEquals(0, profile.getWorstLossStreak());

        assertEquals(1, profile.incrementLossStreak());
        assertEquals(1, profile.getCurrentLossStreak());
        assertEquals(1, profile.getWorstLossStreak());

        profile.incrementLossStreak();
        profile.incrementLossStreak();
        profile.incrementLossStreak(); // 4
        assertEquals(4, profile.getCurrentLossStreak());
        assertEquals(4, profile.getWorstLossStreak());

        // Victory resets loss streak but retains worst loss streak record
        profile.resetLossStreak();
        assertEquals(0, profile.getCurrentLossStreak());
        assertEquals(4, profile.getWorstLossStreak(), "Worst loss streak must be retained across resets");

        profile.incrementLossStreak();
        profile.incrementLossStreak(); // 2
        assertEquals(2, profile.getCurrentLossStreak());
        assertEquals(4, profile.getWorstLossStreak());

        // Surpass previous worst loss streak
        profile.incrementLossStreak();
        profile.incrementLossStreak();
        profile.incrementLossStreak(); // 5
        assertEquals(5, profile.getCurrentLossStreak());
        assertEquals(5, profile.getWorstLossStreak());
    }

    @Test
    @DisplayName("Verify constructor with full streak parameters")
    void testConstructorWithLossStreaks() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Veteran", Rank.GOLD_2, 1550, 100.0, 3, 10, 2, 7);

        assertEquals(3, profile.getCurrentStreak());
        assertEquals(10, profile.getBestStreak());
        assertEquals(2, profile.getCurrentLossStreak());
        assertEquals(7, profile.getWorstLossStreak());
    }

    @Test
    @DisplayName("Verify mutual exclusion between win streak and loss streak")
    void testMutualExclusionBehavior() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "DualStreak", Rank.IRON_1, 1100, 50.0);

        // Win 3 matches
        profile.incrementStreak();
        profile.incrementStreak();
        profile.incrementStreak();
        profile.resetLossStreak();

        assertEquals(3, profile.getCurrentStreak());
        assertEquals(0, profile.getCurrentLossStreak());

        // Suffer a loss
        profile.resetStreak();
        profile.incrementLossStreak();

        assertEquals(0, profile.getCurrentStreak());
        assertEquals(1, profile.getCurrentLossStreak());
        assertEquals(3, profile.getBestStreak());

        // Suffer second loss
        profile.incrementLossStreak();
        assertEquals(2, profile.getCurrentLossStreak());

        // Achieve a comeback win
        profile.incrementStreak();
        profile.resetLossStreak();

        assertEquals(1, profile.getCurrentStreak());
        assertEquals(0, profile.getCurrentLossStreak());
        assertEquals(2, profile.getWorstLossStreak());
    }
}
