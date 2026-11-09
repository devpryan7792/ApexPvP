package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeSpawnFinderTest {

    @Test
    @DisplayName("Safe target already on solid ground returns centered coordinates")
    void testDirectSafeSpawn() {
        World world = mock(World.class);
        Block ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.STONE);

        Block feet = mock(Block.class);
        when(feet.getType()).thenReturn(Material.AIR);
        when(feet.isPassable()).thenReturn(true);

        Block head = mock(Block.class);
        when(head.getType()).thenReturn(Material.AIR);
        when(head.isPassable()).thenReturn(true);

        when(world.getBlockAt(10, 63, 20)).thenReturn(ground);
        when(world.getBlockAt(10, 64, 20)).thenReturn(feet);
        when(world.getBlockAt(10, 65, 20)).thenReturn(head);

        Location raw = new Location(world, 10, 64, 20, 90f, 0f);
        Location safe = SafeSpawnFinder.resolveSafeSpawn(raw);

        assertEquals(10.5, safe.getX());
        assertEquals(64.0, safe.getY());
        assertEquals(20.5, safe.getZ());
        assertEquals(90f, safe.getYaw());
    }

    @Test
    @DisplayName("Target suspended above ground scans downwards and finds true floor")
    void testScanDownwardsToFloor() {
        World world = mock(World.class);

        // Ground is 3 blocks down at Y=60 (ground at 59)
        Block floor = mock(Block.class);
        when(floor.getType()).thenReturn(Material.GRASS_BLOCK);

        Block feet = mock(Block.class);
        when(feet.getType()).thenReturn(Material.AIR);
        when(feet.isPassable()).thenReturn(true);

        Block head = mock(Block.class);
        when(head.getType()).thenReturn(Material.AIR);
        when(head.isPassable()).thenReturn(true);

        when(world.getBlockAt(5, 59, 5)).thenReturn(floor);
        when(world.getBlockAt(5, 60, 5)).thenReturn(feet);
        when(world.getBlockAt(5, 61, 5)).thenReturn(head);

        // Air above
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        for (int y = 62; y <= 65; y++) {
            when(world.getBlockAt(5, y, 5)).thenReturn(air);
        }

        Location suspended = new Location(world, 5, 64, 5);
        Location safe = SafeSpawnFinder.resolveSafeSpawn(suspended);

        assertEquals(5.5, safe.getX());
        assertEquals(60.0, safe.getY(), "Must snap down to true floor surface at Y=60");
        assertEquals(5.5, safe.getZ());
    }

    @Test
    @DisplayName("Null or empty world cleanly falls back to centered location without error")
    void testNullWorldFallback() {
        Location noWorld = new Location(null, 15, 64, 25);
        Location resolved = SafeSpawnFinder.resolveSafeSpawn(noWorld);
        assertEquals(15.0, resolved.getX());

        World mockWorld = mock(World.class);
        Location mockLoc = new Location(mockWorld, 15, 64, 25);
        Location resolvedMock = SafeSpawnFinder.resolveSafeSpawn(mockLoc);
        assertEquals(15.5, resolvedMock.getX());
        assertEquals(25.5, resolvedMock.getZ());
    }
}
