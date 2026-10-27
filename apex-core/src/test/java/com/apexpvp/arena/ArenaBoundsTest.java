package com.apexpvp.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArenaBoundsTest {

    @Test
    @DisplayName("Verify ArenaBounds point containment and edge inclusivity")
    void testContainment() {
        ArenaBounds bounds = new ArenaBounds(-10, 0, -10, 10, 20, 10);

        // Inside
        assertTrue(bounds.contains(0, 10, 0));
        assertTrue(bounds.contains(-10, 0, -10));
        assertTrue(bounds.contains(10, 20, 10));

        // Outside
        assertFalse(bounds.contains(11, 10, 0));
        assertFalse(bounds.contains(0, 21, 0));
        assertFalse(bounds.contains(0, -1, 0));
        assertFalse(bounds.contains(0, 10, -11));
    }

    @Test
    @DisplayName("Verify bounds translation offset for mathematical grid allocation")
    void testOffset() {
        ArenaBounds base = new ArenaBounds(-20, 0, -20, 20, 30, 20);
        ArenaBounds offsetBounds = base.offset(1000, 64, 2000);

        assertEquals(980, offsetBounds.getMinX());
        assertEquals(1020, offsetBounds.getMaxX());
        assertEquals(64, offsetBounds.getMinY());
        assertEquals(94, offsetBounds.getMaxY());
        assertEquals(1980, offsetBounds.getMinZ());
        assertEquals(2020, offsetBounds.getMaxZ());

        assertTrue(offsetBounds.contains(1000, 70, 2000));
        assertFalse(offsetBounds.contains(0, 10, 0));
    }

    @Test
    @DisplayName("Verify chunk key calculation covering the bounding box")
    void testChunkKeys() {
        ArenaBounds bounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        Set<Long> chunks = bounds.getChunkKeys();

        // X spans -30 to 30 => chunk -2 to 1 (4 chunks)
        // Z spans -30 to 30 => chunk -2 to 1 (4 chunks)
        // Total chunks = 4 * 4 = 16
        assertEquals(16, chunks.size());
    }

    @Test
    @DisplayName("Verify volume calculation")
    void testVolume() {
        ArenaBounds bounds = new ArenaBounds(0, 0, 0, 10, 10, 10);
        // (10 - 0 + 1)^3 = 11 * 11 * 11 = 1331
        assertEquals(1331, bounds.getVolume());
    }
}
