package com.apexpvp.integration.worldedit;

import com.apexpvp.arena.ArenaBounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Safe integration hook for FastAsyncWorldEdit (FAWE) and standard WorldEdit.
 * Safely extracts cuboid/polyhedral selection bounds without hard dependency crashes.
 */
public class WorldEditHook {

    public static boolean isAvailable() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                return false;
            }
            return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                    || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Attempts to read the player's active WorldEdit selection.
     * Returns null if WorldEdit is missing or if the player has no complete selection.
     */
    @Nullable
    public static ArenaBounds getPlayerSelectionBounds(Player player) {
        if (!isAvailable()) return null;
        try {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (plugin == null) {
                plugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
            }
            if (!(plugin instanceof com.sk89q.worldedit.bukkit.WorldEditPlugin wep)) {
                return null;
            }

            com.sk89q.worldedit.LocalSession session = wep.getSession(player);
            if (session == null) return null;

            com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player.getWorld());
            com.sk89q.worldedit.regions.Region region = session.getSelection(weWorld);
            if (region == null) return null;

            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();

            return new ArenaBounds(min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                                   max.getBlockX(), max.getBlockY(), max.getBlockZ());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
