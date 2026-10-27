package com.apexpvp.arena.listener;

import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaState;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * High-performance event listener that intercepts world modifications within active arenas
 * and captures pristine block snapshots for zero-lag rollback.
 */
public class BlockTrackerListener implements Listener {

    private final ArenaManager arenaManager;

    public BlockTrackerListener(@NotNull ArenaManager arenaManager) {
        this.arenaManager = Objects.requireNonNull(arenaManager, "arenaManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance == null) return;

        // Block modifications outside of active matches are strictly prohibited
        if (instance.getState() != ArenaState.IN_USE || !instance.getTemplate().isAllowBlockBreak()) {
            event.setCancelled(true);
            return;
        }

        instance.getRollbackSnapshot().recordOriginal(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance == null) return;

        // Verify state and build permission
        if (instance.getState() != ArenaState.IN_USE || !instance.getTemplate().isAllowBuild()) {
            event.setCancelled(true);
            return;
        }

        // Enforce arena vertical boundary height limits
        int y = block.getY();
        if (y < instance.getTemplate().getMinBuildY() || y > instance.getTemplate().getMaxBuildY()) {
            event.setCancelled(true);
            return;
        }

        // Record the pristine state before the placed block replaced it (e.g., Air, TallGrass)
        instance.getRollbackSnapshot().recordOriginal(event.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance == null) return;

        if (instance.getState() != ArenaState.IN_USE || !instance.getTemplate().isAllowBuild()) {
            event.setCancelled(true);
            return;
        }

        int y = block.getY();
        if (y < instance.getTemplate().getMinBuildY() || y > instance.getTemplate().getMaxBuildY()) {
            event.setCancelled(true);
            return;
        }

        instance.getRollbackSnapshot().recordOriginal(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlockClicked();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance == null) return;

        if (instance.getState() != ArenaState.IN_USE || !instance.getTemplate().isAllowBlockBreak()) {
            event.setCancelled(true);
            return;
        }

        instance.getRollbackSnapshot().recordOriginal(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
            if (instance != null && instance.getState() == ArenaState.IN_USE) {
                instance.getRollbackSnapshot().recordOriginal(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
            if (instance != null && instance.getState() == ArenaState.IN_USE) {
                instance.getRollbackSnapshot().recordOriginal(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidSpread(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(toBlock.getLocation());
        if (instance != null && instance.getState() == ArenaState.IN_USE) {
            instance.getRollbackSnapshot().recordOriginal(toBlock);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance != null && instance.getState() == ArenaState.IN_USE) {
            instance.getRollbackSnapshot().recordOriginal(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance != null && instance.getState() == ArenaState.IN_USE) {
            instance.getRollbackSnapshot().recordOriginal(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block block = event.getBlock();
        ArenaInstance instance = arenaManager.findInstanceAt(block.getLocation());
        if (instance != null && instance.getState() == ArenaState.IN_USE) {
            instance.getRollbackSnapshot().recordOriginal(block);
        }
    }
}
