package com.apexpvp.integration.worldedit;

import com.apexpvp.arena.ArenaBounds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Safe integration hook for FastAsyncWorldEdit (FAWE) and standard WorldEdit.
 * Uses lazy inner class loading to prevent NoClassDefFoundError when WorldEdit is absent.
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

    @Nullable
    public static ArenaBounds getPlayerSelectionBounds(@NotNull Player player) {
        if (!isAvailable()) return null;
        try {
            return WorldEditAdapter.getPlayerSelectionBounds(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static ArenaBounds getSchematicBounds(@Nullable File file) {
        if (!isAvailable() || file == null || !file.exists()) return null;
        try {
            return WorldEditAdapter.getSchematicBounds(file);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean pasteSchematic(@NotNull File file,
                                         @NotNull World world,
                                         int centerX, int originY, int centerZ) {
        return pasteSchematic(file, world, centerX, originY, centerZ, 0);
    }

    public static boolean pasteSchematic(@NotNull File file,
                                         @NotNull World world,
                                         int centerX, int originY, int centerZ, int floorOffset) {
        if (!isAvailable() || !file.exists()) return false;
        try {
            return WorldEditAdapter.pasteSchematic(file, world, centerX, originY, centerZ, floorOffset);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Inner adapter isolating WorldEdit/FAWE class references.
     * Only loaded into JVM if isAvailable() evaluates to true.
     */
    private static class WorldEditAdapter {

        @Nullable
        static ArenaBounds getPlayerSelectionBounds(@NotNull Player player) {
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
        }

        @Nullable
        static ArenaBounds getSchematicBounds(@NotNull File file) {
            try {
                com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format =
                        com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
                if (format == null) return null;

                try (InputStream in = new FileInputStream(file);
                     com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader = format.getReader(in)) {
                    com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = reader.read();
                    if (clipboard == null) return null;

                    com.sk89q.worldedit.regions.Region region = clipboard.getRegion();
                    int width = region.getWidth();
                    int height = region.getHeight();
                    int length = region.getLength();

                    int halfW = width / 2;
                    int halfL = length / 2;
                    return new ArenaBounds(-halfW, 0, -halfL, halfW, Math.max(30, height), halfL);
                }
            } catch (Throwable ignored) {
                return null;
            }
        }

        static boolean pasteSchematic(@NotNull File file,
                                      @NotNull World world,
                                      int centerX, int originY, int centerZ, int floorOffset) {
            try {
                com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format =
                        com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
                if (format == null) return false;

                try (InputStream in = new FileInputStream(file);
                     com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader = format.getReader(in)) {
                    com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = reader.read();
                    if (clipboard == null) return false;

                    com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world);
                    try (com.sk89q.worldedit.EditSession editSession =
                                 com.sk89q.worldedit.WorldEdit.getInstance().newEditSession(weWorld)) {
                        int targetMinX = centerX - clipboard.getDimensions().getX() / 2;
                        int targetMinY = originY - floorOffset;
                        int targetMinZ = centerZ - clipboard.getDimensions().getZ() / 2;

                        int toX = targetMinX + (clipboard.getOrigin().getX() - clipboard.getMinimumPoint().getX());
                        int toY = targetMinY + (clipboard.getOrigin().getY() - clipboard.getMinimumPoint().getY());
                        int toZ = targetMinZ + (clipboard.getOrigin().getZ() - clipboard.getMinimumPoint().getZ());

                        com.sk89q.worldedit.function.operation.Operation operation =
                                new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                                        .createPaste(editSession)
                                        .to(com.sk89q.worldedit.math.BlockVector3.at(toX, toY, toZ))
                                        .ignoreAirBlocks(false)
                                        .build();
                        com.sk89q.worldedit.function.operation.Operations.complete(operation);
                        return true;
                    }
                }
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
