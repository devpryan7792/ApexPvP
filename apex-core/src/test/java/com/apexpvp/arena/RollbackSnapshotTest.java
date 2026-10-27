package com.apexpvp.arena;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class RollbackSnapshotTest {

    private Block createMockBlock(int x, int y, int z, BlockState state) {
        return (Block) Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[]{Block.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getState" -> state;
                default -> null;
            }
        );
    }

    private BlockState createMockBlockState(String identifier) {
        return createMockBlockState(identifier, 0, 0, 0);
    }

    private BlockState createMockBlockState(String identifier, int x, int y, int z) {
        return (BlockState) Proxy.newProxyInstance(
            BlockState.class.getClassLoader(),
            new Class<?>[]{BlockState.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "toString" -> identifier;
                default -> null;
            }
        );
    }

    @Test
    @DisplayName("Verify first-write-wins: intermediate changes do not overwrite pristine state")
    void testFirstWriteWins() {
        RollbackSnapshot snapshot = new RollbackSnapshot();
        BlockPos pos = new BlockPos(10, 65, 10);

        BlockState pristineState = createMockBlockState("PristineStone");
        Block blockOriginal = createMockBlock(10, 65, 10, pristineState);

        // First modification (e.g. broken to Air)
        boolean firstRecorded = snapshot.recordOriginal(blockOriginal);
        assertTrue(firstRecorded, "First modification must be captured");
        assertTrue(snapshot.hasRecorded(pos));
        assertEquals(1, snapshot.getModifiedBlockCount());

        // Subsequent modification at same position (e.g. Cobblestone placed, then water spread)
        BlockState intermediateState = createMockBlockState("IntermediateCobblestone");
        Block blockModified = createMockBlock(10, 65, 10, intermediateState);

        boolean secondRecorded = snapshot.recordOriginal(blockModified);
        assertFalse(secondRecorded, "Subsequent modification must NOT overwrite original snapshot");
        assertEquals(1, snapshot.getModifiedBlockCount());

        // Playback queue must contain the pristine state
        Queue<BlockState> queue = snapshot.createPlaybackQueue();
        assertEquals(1, queue.size());
        assertEquals(pristineState, queue.poll());
    }

    @Test
    @DisplayName("Verify snapshot clear")
    void testClear() {
        RollbackSnapshot snapshot = new RollbackSnapshot();
        BlockState state = createMockBlockState("Sand");
        Block block = createMockBlock(5, 64, 5, state);

        snapshot.recordOriginal(block);
        assertEquals(1, snapshot.getModifiedBlockCount());

        snapshot.clear();
        assertEquals(0, snapshot.getModifiedBlockCount());
        assertTrue(snapshot.isEmpty());
    }

    @Test
    @DisplayName("Verify recordOriginal(BlockState) captures pristine state directly")
    void testRecordOriginalBlockStateDirectly() {
        RollbackSnapshot snapshot = new RollbackSnapshot();
        BlockState pristineAir = createMockBlockState("PristineAir", 12, 65, -8);

        boolean recorded = snapshot.recordOriginal(pristineAir);
        assertTrue(recorded, "Initial BlockState record must be saved");
        assertTrue(snapshot.hasRecorded(new BlockPos(12, 65, -8)));

        // Duplicate record at same coordinate must be rejected
        BlockState modifiedStone = createMockBlockState("PlacedCobblestone", 12, 65, -8);
        boolean duplicateRecorded = snapshot.recordOriginal(modifiedStone);
        assertFalse(duplicateRecorded, "Second BlockState record must be rejected");
        assertEquals(1, snapshot.getModifiedBlockCount());
        assertEquals(pristineAir, snapshot.createPlaybackQueue().poll());
    }

    @Test
    @DisplayName("Verify concurrent multi-threaded block record deduplication under rapid edits")
    void testConcurrentDeduplication() throws InterruptedException {
        RollbackSnapshot snapshot = new RollbackSnapshot();
        int uniquePositions = 50;
        int threads = 8;
        int operationsPerThread = 100;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        int posIndex = i % uniquePositions;
                        BlockState state = createMockBlockState("ThreadState_" + threadId + "_" + i);
                        Block block = createMockBlock(posIndex, 64, posIndex, state);
                        snapshot.recordOriginal(block);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 50 unique positions must be recorded despite 800 concurrent writes
        assertEquals(uniquePositions, snapshot.getModifiedBlockCount(),
                "Exactly 50 unique positions should be recorded despite 800 concurrent block updates");
        assertEquals(uniquePositions, snapshot.createPlaybackQueue().size());
    }
}
