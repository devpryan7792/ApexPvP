package com.apexpvp.arena;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Queue;

/**
 * Tick-budgeted, zero-lag rollback task that amortizes block state restoration over multiple ticks
 * and purges leftover match entities (dropped items, arrows, TNT).
 */
public class ArenaRollbackTask implements Runnable {

    private final Plugin plugin;
    private final ArenaInstance instance;
    private final int blocksPerTick;
    private final Runnable onComplete;

    private final Queue<BlockState> playbackQueue;
    private BukkitTask scheduledTask;

    public ArenaRollbackTask(@NotNull Plugin plugin,
                             @NotNull ArenaInstance instance,
                             int blocksPerTick,
                             @NotNull Runnable onComplete) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.instance = Objects.requireNonNull(instance, "instance cannot be null");
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.onComplete = Objects.requireNonNull(onComplete, "onComplete cannot be null");
        this.playbackQueue = instance.getRollbackSnapshot().createPlaybackQueue();
    }

    public void start() {
        if (playbackQueue.isEmpty()) {
            cleanEntities();
            onComplete.run();
            return;
        }
        // Run synchronously on the main thread, 1 tick delay, repeat every 1 tick
        this.scheduledTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public void run() {
        int restoredThisTick = 0;

        while (restoredThisTick < blocksPerTick && !playbackQueue.isEmpty()) {
            BlockState state = playbackQueue.poll();
            if (state != null) {
                // update(force = true, applyPhysics = false) to eliminate physics cascade lag
                state.update(true, false);
                restoredThisTick++;
            }
        }

        if (playbackQueue.isEmpty()) {
            if (scheduledTask != null) {
                scheduledTask.cancel();
            }
            cleanEntities();
            onComplete.run();
        }
    }

    /**
     * Purges dropped items, lingering potion clouds, arrows, and primed TNT inside the arena bounds.
     */
    private void cleanEntities() {
        ArenaBounds bounds = instance.getBounds();
        org.bukkit.Location center = bounds.getCenter(instance.getWorld());
        double dx = (bounds.getMaxX() - bounds.getMinX() + 1) / 2.0;
        double dy = (bounds.getMaxY() - bounds.getMinY() + 1) / 2.0;
        double dz = (bounds.getMaxZ() - bounds.getMinZ() + 1) / 2.0;

        for (Entity entity : instance.getWorld().getNearbyEntities(center, dx, dy, dz)) {
            if (bounds.contains(entity.getLocation())) {
                if (entity instanceof Item ||
                    entity instanceof Projectile ||
                    entity instanceof TNTPrimed ||
                    entity instanceof AreaEffectCloud) {
                    entity.remove();
                }
            }
        }
    }
}
