package com.apexpvp.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaGridMathTest {

    @Test
    @DisplayName("Verify mathematical grid allocation prevents spatial collision across templates and instances")
    void testGridNonCollision() {
        // Test 5 templates with 10 dynamic instances each
        List<ArenaBounds> allBounds = new ArrayList<>();
        ArenaBounds baseBounds = new ArenaBounds(-50, 0, -50, 50, 40, 50);

        for (int templateId = 1; templateId <= 5; templateId++) {
            for (int instanceIndex = 0; instanceIndex < 10; instanceIndex++) {
                int originX = templateId * 1000;
                int originZ = instanceIndex * 1000;
                ArenaBounds instanceBounds = baseBounds.offset(originX, 64, originZ);
                allBounds.add(instanceBounds);
            }
        }

        // Verify total instances generated
        assertEquals(50, allBounds.size());

        // Check every pair for pairwise disjointness
        for (int i = 0; i < allBounds.size(); i++) {
            ArenaBounds a = allBounds.get(i);
            for (int j = i + 1; j < allBounds.size(); j++) {
                ArenaBounds b = allBounds.get(j);

                boolean disjoint = a.getMaxX() < b.getMinX() || a.getMinX() > b.getMaxX() ||
                                   a.getMaxY() < b.getMinY() || a.getMinY() > b.getMaxY() ||
                                   a.getMaxZ() < b.getMinZ() || a.getMinZ() > b.getMaxZ();

                assertTrue(disjoint, "Collision detected between instance " + i + " and " + j);
            }
        }
    }

    @Test
    @DisplayName("Verify minimum spatial clearance between adjacent instances along grid")
    void testMinimumClearance() {
        ArenaBounds baseBounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        int templateId = 1;

        ArenaBounds inst0 = baseBounds.offset(templateId * 1000, 64, 0 * 1000);
        ArenaBounds inst1 = baseBounds.offset(templateId * 1000, 64, 1 * 1000);

        int clearanceZ = inst1.getMinZ() - inst0.getMaxZ();
        // 970 - 30 = 940 blocks of clearance
        assertEquals(940, clearanceZ, "Adjacent instances must maintain at least 900 blocks of void buffer");
    }

    @Test
    @DisplayName("Verify large scale mathematical non-collision across 10,000 instances including negative coordinates")
    void testLargeScaleGridNonCollision() {
        // 100 templates x 100 instances = 10,000 instances
        java.util.Set<Long> occupiedGridCells = new java.util.HashSet<>();
        ArenaBounds baseBounds = new ArenaBounds(-75, 0, -75, 75, 50, 75); // 150x150 footprint

        for (int templateId = -50; templateId < 50; templateId++) {
            for (int instanceIndex = -50; instanceIndex < 50; instanceIndex++) {
                int originX = templateId * 1000;
                int originZ = instanceIndex * 1000;

                ArenaBounds instanceBounds = baseBounds.offset(originX, 64, originZ);

                // Ensure bounds stay strictly within its allocated 1000x1000 cell boundary
                assertTrue(instanceBounds.getMinX() > originX - 500);
                assertTrue(instanceBounds.getMaxX() < originX + 500);
                assertTrue(instanceBounds.getMinZ() > originZ - 500);
                assertTrue(instanceBounds.getMaxZ() < originZ + 500);

                long cellKey = (((long) templateId) << 32) ^ (instanceIndex & 0xFFFFFFFFL);
                assertTrue(occupiedGridCells.add(cellKey),
                        "Duplicate grid cell detected for template " + templateId + " index " + instanceIndex);
            }
        }
        assertEquals(10000, occupiedGridCells.size());
    }
}
