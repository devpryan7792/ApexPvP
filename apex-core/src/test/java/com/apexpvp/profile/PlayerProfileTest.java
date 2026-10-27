package com.apexpvp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileTest {

    @Test
    @DisplayName("Verify profile ELO mutation and thread safety")
    void testEloMutation() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Steve", Rank.COPPER_1, 800, 100.0);

        assertEquals(800, profile.getElo());
        assertFalse(profile.isDirty());

        int newElo = profile.modifyElo(32);
        assertEquals(832, newElo);
        assertEquals(832, profile.getElo());
        assertTrue(profile.isDirty());

        // Test non-negative clamp
        profile.modifyElo(-2000);
        assertEquals(0, profile.getElo());
    }

    @Test
    @DisplayName("Verify coin deduction escrow safety")
    void testCoinDeduction() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Alex", Rank.COPPER_1, 800, 50.0);

        assertTrue(profile.deductCoins(30.0));
        assertEquals(20.0, profile.getCoins());

        // Insufficient funds
        assertFalse(profile.deductCoins(30.0));
        assertEquals(20.0, profile.getCoins());

        profile.addCoins(100.0);
        assertEquals(120.0, profile.getCoins());
    }

    @Test
    @DisplayName("Verify kit stats tracking and win-rate calculation")
    void testKitStats() {
        PlayerStats stats = new PlayerStats("Nodebuff", 7, 3, 14, 5);
        assertEquals("nodebuff", stats.getKitId());
        assertEquals(7, stats.getWins());
        assertEquals(3, stats.getLosses());
        assertEquals(10, stats.getTotalMatches());
        assertEquals(70.0, stats.getWinRate(), 0.001);
        assertEquals(2.8, stats.getKDR(), 0.001);

        stats.incrementWins();
        assertEquals(8, stats.getWins());
        assertEquals(11, stats.getTotalMatches());
    }

    @Test
    @DisplayName("Verify concurrency & double-spend immunity under multi-threaded contention")
    void testConcurrentDoubleSpendImmunity() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        // Starting balance of 100.0 coins
        PlayerProfile profile = new PlayerProfile(uuid, "SteveContender", Rank.COPPER_1, 800, 100.0);

        int threads = 10;
        double stakePerThread = 20.0; // 10 * 20.0 = 200.0 coins requested from 100.0 balance

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger successfulDeductions = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failedDeductions = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    boolean success = profile.deductCoins(stakePerThread);
                    if (success) {
                        successfulDeductions.incrementAndGet();
                    } else {
                        failedDeductions.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 5 deductions must succeed (5 * 20 = 100) and 5 must fail
        assertEquals(5, successfulDeductions.get(), "Exactly 5 deductions should succeed");
        assertEquals(5, failedDeductions.get(), "Remaining 5 deductions must fail due to zero balance");
        assertEquals(0.0, profile.getCoins(), 0.0001, "Final balance must be exactly 0.0 and never negative");
    }
}
