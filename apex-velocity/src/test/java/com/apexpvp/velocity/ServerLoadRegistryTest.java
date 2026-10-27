package com.apexpvp.velocity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Velocity ServerLoadRegistry Tests")
public class ServerLoadRegistryTest {

    private ServerLoadRegistry registry;

    @BeforeEach
    void setUp() {
        // 500ms timeout for test agility
        registry = new ServerLoadRegistry(500L);
    }

    @Test
    @DisplayName("Should select server with highest available arena capacity")
    void testBestServerCapacitySelection() {
        registry.updateHeartbeat("pvp-node-1", 5, 20, 15);
        registry.updateHeartbeat("pvp-node-2", 2, 20, 18);
        registry.updateHeartbeat("pvp-node-3", 10, 20, 10);

        ServerLoadInfo best = registry.getBestServer();
        assertNotNull(best, "Best server should not be null");
        assertEquals("pvp-node-2", best.serverId());
        assertEquals(18, best.availableArenas());
    }

    @Test
    @DisplayName("Should tie-break by choosing server with fewer active matches")
    void testBestServerTieBreaker() {
        // Both have 10 available arenas, but node-2 has fewer active matches (2 vs 5)
        registry.updateHeartbeat("pvp-node-1", 5, 15, 10);
        registry.updateHeartbeat("pvp-node-2", 2, 12, 10);

        ServerLoadInfo best = registry.getBestServer();
        assertNotNull(best);
        assertEquals("pvp-node-2", best.serverId());
        assertEquals(2, best.activeMatches());
    }

    @Test
    @DisplayName("Should prune stale servers past timeout threshold")
    void testStaleServerPruning() throws InterruptedException {
        registry.updateHeartbeat("fresh-node", 0, 10, 10);
        registry.updateHeartbeat("stale-node", 0, 50, 50); // Higher capacity, but will go stale

        // Sleep 600ms to exceed 500ms stale timeout
        Thread.sleep(600);

        // Refresh only the fresh node
        registry.updateHeartbeat("fresh-node", 0, 10, 10);

        ServerLoadInfo best = registry.getBestServer();
        assertNotNull(best);
        assertEquals("fresh-node", best.serverId(), "Stale node with 50 capacity should have been pruned");
        assertEquals(1, registry.getRegisteredServerCount(), "Stale node should be evicted from registry");
    }

    @Test
    @DisplayName("Should return null when all servers are stale")
    void testAllServersStale() throws InterruptedException {
        registry.updateHeartbeat("dead-node-1", 1, 10, 9);
        registry.updateHeartbeat("dead-node-2", 2, 10, 8);

        Thread.sleep(600);

        ServerLoadInfo best = registry.getBestServer();
        assertNull(best, "Expected null when all registered servers have timed out");
        assertEquals(0, registry.getRegisteredServerCount());
    }

    @Test
    @DisplayName("Should support concurrent heartbeat updates without race conditions")
    void testConcurrentHeartbeatUpdates() throws InterruptedException {
        int threads = 8;
        int updatesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < updatesPerThread; i++) {
                        registry.updateHeartbeat("node-" + (threadId % 3), i, 50, 50 - i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(3, registry.getRegisteredServerCount());
        assertNotNull(registry.getBestServer());
    }
}
