package com.apexpvp.arena;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures pristine block states before modifications occur in an arena.
 * Implements a first-write-wins snapshot so intermediate changes never overwrite original blocks.
 */
public class RollbackSnapshot {

    private final Map<BlockPos, BlockState> pristineBlocks = new ConcurrentHashMap<>();

    /**
     * Records the original block state if and only if this coordinate has not been modified yet.
     *
     * @param block The block prior to mutation.
     * @return true if this was the first modification at this position and the original state was recorded.
     */
    public boolean recordOriginal(@NotNull Block block) {
        BlockPos pos = BlockPos.of(block);
        // Only snapshot if absent to preserve the pristine initial state
        return pristineBlocks.putIfAbsent(pos, block.getState()) == null;
    }

    /**
     * Records the original block state directly (e.g. from BlockPlaceEvent#getBlockReplacedState).
     */
    public boolean recordOriginal(@NotNull BlockState state) {
        BlockPos pos = BlockPos.of(state.getX(), state.getY(), state.getZ());
        return pristineBlocks.putIfAbsent(pos, state) == null;
    }

    /**
     * Checks if a position has already been recorded in this snapshot.
     */
    public boolean hasRecorded(@NotNull BlockPos pos) {
        return pristineBlocks.containsKey(pos);
    }

    /**
     * Creates an unmodifiable queue of pristine BlockStates ready for playback during rollback.
     */
    @NotNull
    public Queue<BlockState> createPlaybackQueue() {
        return new ArrayDeque<>(pristineBlocks.values());
    }

    public int getModifiedBlockCount() {
        return pristineBlocks.size();
    }

    public boolean isEmpty() {
        return pristineBlocks.isEmpty();
    }

    public void clear() {
        pristineBlocks.clear();
    }
}
