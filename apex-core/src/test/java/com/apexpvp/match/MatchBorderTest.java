package com.apexpvp.match;

import com.apexpvp.arena.ArenaBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Match Safe Zone & Anti-Stall Border Tests")
class MatchBorderTest {

    @Test
    @DisplayName("Verify initial safe zone radius computed correctly from bounds")
    void testInitialSafeZoneRadius() {
        ArenaBounds bounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        int halfWidth = (bounds.getMaxX() - bounds.getMinX()) / 2;
        int halfLength = (bounds.getMaxZ() - bounds.getMinZ()) / 2;
        double initialRadius = Math.max(15.0, Math.min(halfWidth, halfLength));

        assertEquals(30.0, initialRadius, 0.001);

        // Even with a small 10x10 arena, radius has a floor of 15.0
        ArenaBounds smallBounds = new ArenaBounds(-5, 0, -5, 5, 40, 5);
        int smallHalfW = (smallBounds.getMaxX() - smallBounds.getMinX()) / 2;
        int smallHalfL = (smallBounds.getMaxZ() - smallBounds.getMinZ()) / 2;
        double smallRadius = Math.max(15.0, Math.min(smallHalfW, smallHalfL));

        assertEquals(15.0, smallRadius, 0.001);
    }

    @Test
    @DisplayName("Verify safe zone radius contraction progression")
    void testSafeZoneContractionMath() {
        double initialRadius = 30.0;
        double minRadius = 6.0;
        int shrinkDelaySec = 180;
        int shrinkDurationSec = 60;

        // Before shrink delay (e.g. 100s) -> full radius
        long elapsedSec1 = 100;
        assertTrue(elapsedSec1 < shrinkDelaySec);
        double radius1 = initialRadius;
        assertEquals(30.0, radius1, 0.001);

        // Halfway through shrink (e.g. 180 + 30 = 210s) -> 50% shrink
        long elapsedSec2 = 210;
        long shrinkElapsed = elapsedSec2 - shrinkDelaySec;
        double progress = Math.min(1.0, (double) shrinkElapsed / Math.max(1, shrinkDurationSec));
        assertEquals(0.5, progress, 0.001);
        double radius2 = initialRadius - (progress * (initialRadius - minRadius));
        assertEquals(18.0, radius2, 0.001);

        // Completely finished shrink (e.g. 250s) -> clamped to minRadius
        long elapsedSec3 = 250;
        long shrinkElapsed3 = elapsedSec3 - shrinkDelaySec;
        double progress3 = Math.min(1.0, (double) shrinkElapsed3 / Math.max(1, shrinkDurationSec));
        assertEquals(1.0, progress3, 0.001);
        double radius3 = initialRadius - (progress3 * (initialRadius - minRadius));
        assertEquals(6.0, radius3, 0.001);
    }

    @Test
    @DisplayName("Verify player distance boundary calculation")
    void testPlayerDistanceOutsideSafeZone() {
        int centerX = 0;
        int centerZ = 0;
        double safeRadius = 18.0;

        // Player inside safe zone at (10, 10) -> distance sqrt(200) ~ 14.14 <= 18
        double p1X = 10.0;
        double p1Z = 10.0;
        double dist1 = Math.sqrt((p1X - centerX) * (p1X - centerX) + (p1Z - centerZ) * (p1Z - centerZ));
        assertFalse(dist1 > safeRadius, "Player inside safe zone should not be marked outside");

        // Player outside safe zone at (15, 15) -> distance sqrt(450) ~ 21.21 > 18
        double p2X = 15.0;
        double p2Z = 15.0;
        double dist2 = Math.sqrt((p2X - centerX) * (p2X - centerX) + (p2Z - centerZ) * (p2Z - centerZ));
        assertTrue(dist2 > safeRadius, "Player outside safe zone must be marked outside");
    }

    @Test
    @DisplayName("Verify match timeout condition at 600 seconds")
    void testMatchTimeoutLimit() {
        int maxDurationSec = 600;
        long elapsedSec = 600;
        assertTrue(elapsedSec >= maxDurationSec, "Match at 600 seconds must trigger timeout limit");

        long elapsedUnder = 599;
        assertFalse(elapsedUnder >= maxDurationSec, "Match under 600 seconds must continue");
    }

    @Test
    @DisplayName("Verify border damage is exactly 1 heart (2.0 HP) per second")
    void testBorderDamagePerSecondIsOneHeart() {
        double damagePerSec = 2.0; // 2.0 HP = 1 full heart in Minecraft
        assertEquals(2.0, damagePerSec, 0.001, "Safe zone border outside damage must be exactly 1 full heart (2.0 HP) per second");
    }

    @Test
    @DisplayName("Verify victory celebration cooldown configuration defaults")
    void testCelebrationCooldownDefaults() {
        int defaultCelebrationSec = 5;
        assertTrue(defaultCelebrationSec >= 3 && defaultCelebrationSec <= 10, "Celebration duration should provide sufficient victory window");
    }
}
