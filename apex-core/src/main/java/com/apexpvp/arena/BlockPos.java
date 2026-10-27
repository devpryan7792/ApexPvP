package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable block coordinate record for arena block tracking.
 */
public record BlockPos(int x, int y, int z) {

    @NotNull
    public static BlockPos of(@NotNull Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    @NotNull
    public static BlockPos of(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @NotNull
    public static BlockPos of(@NotNull Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
