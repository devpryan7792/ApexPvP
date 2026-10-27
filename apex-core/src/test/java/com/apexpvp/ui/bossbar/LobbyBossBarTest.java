package com.apexpvp.ui.bossbar;

import com.apexpvp.profile.Rank;
import com.apexpvp.profile.RankTier;
import net.kyori.adventure.bossbar.BossBar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyBossBarTest {

    @Test
    @DisplayName("Verify BossBar color mapping matches each competitive tier")
    void testResolveColor() {
        assertEquals(BossBar.Color.RED, LobbyBossBarManager.resolveColor(RankTier.COPPER));
        assertEquals(BossBar.Color.WHITE, LobbyBossBarManager.resolveColor(RankTier.IRON));
        assertEquals(BossBar.Color.YELLOW, LobbyBossBarManager.resolveColor(RankTier.GOLD));
        assertEquals(BossBar.Color.BLUE, LobbyBossBarManager.resolveColor(RankTier.DIAMOND));
        assertEquals(BossBar.Color.GREEN, LobbyBossBarManager.resolveColor(RankTier.EMERALD));
        assertEquals(BossBar.Color.PURPLE, LobbyBossBarManager.resolveColor(RankTier.NETHERITE));
    }

    @Test
    @DisplayName("Verify proportional promotion progress calculation")
    void testProgressCalculation() {
        // COPPER_1 threshold = 800, next rank COPPER_2 threshold = 900
        Rank rank = Rank.COPPER_1;
        Rank nextRank = rank.getNextRank();
        assertNotNull(nextRank);
        assertEquals(800, rank.getEloThreshold());
        assertEquals(900, nextRank.getEloThreshold());

        // Exactly at floor -> 0.0
        float progress0 = calculateProgress(800, rank, nextRank);
        assertEquals(0.0f, progress0, 0.001f);

        // Halfway to next tier (850 ELO) -> 0.5
        float progress50 = calculateProgress(850, rank, nextRank);
        assertEquals(0.5f, progress50, 0.001f);

        // Threshold reached (900 ELO) -> 1.0
        float progress100 = calculateProgress(900, rank, nextRank);
        assertEquals(1.0f, progress100, 0.001f);

        // Surpassed threshold (950 ELO before /rankup) -> clamped to 1.0
        float progressOver = calculateProgress(950, rank, nextRank);
        assertEquals(1.0f, progressOver, 0.001f);

        // Below floor -> clamped to 0.0
        float progressUnder = calculateProgress(750, rank, nextRank);
        assertEquals(0.0f, progressUnder, 0.001f);
    }

    @Test
    @DisplayName("Verify pinnacle rank NETHERITE_3 gives full 1.0 progress")
    void testMaxRankProgress() {
        Rank maxRank = Rank.NETHERITE_3;
        assertNull(maxRank.getNextRank());

        float progress = calculateProgress(3000, maxRank, null);
        assertEquals(1.0f, progress, 0.001f);
    }

    private float calculateProgress(int elo, Rank rank, Rank nextRank) {
        if (nextRank == null) {
            return 1.0f;
        }
        int lower = rank.getEloThreshold();
        int upper = nextRank.getEloThreshold();
        if (upper > lower) {
            float raw = (float) (elo - lower) / (float) (upper - lower);
            return Math.min(1.0f, Math.max(0.0f, raw));
        }
        return 1.0f;
    }
}
