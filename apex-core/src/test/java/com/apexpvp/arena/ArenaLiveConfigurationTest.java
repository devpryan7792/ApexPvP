package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Arena Live Configuration and Hot Reloading")
class ArenaLiveConfigurationTest {

    @Test
    @DisplayName("ArenaInstance dynamically hot-updates spawn points upon applyTemplateUpdate")
    void testLiveInstanceTemplateUpdate() {
        World world = mock(World.class);

        ArenaBounds initialBounds = new ArenaBounds(-20, 0, -20, 20, 40, 20);
        ArenaTemplate.SpawnPoint initialA = new ArenaTemplate.SpawnPoint(-10, 1.0, 0, -90f, 0f);
        ArenaTemplate.SpawnPoint initialB = new ArenaTemplate.SpawnPoint(10, 1.0, 0, 90f, 0f);
        ArenaTemplate.SpawnPoint initialSpec = new ArenaTemplate.SpawnPoint(0, 15.0, 0, 0f, 45f);

        ArenaTemplate template = new ArenaTemplate(
                1, "test_arena", "Test Arena", initialBounds,
                initialA, initialB, initialSpec, true, true, 64, 84, null
        );

        ArenaInstance instance = new ArenaInstance(template, 0, world, 64, false);

        assertEquals(990.5, instance.getSpawnA().getX(), 0.01);
        assertEquals(1010.5, instance.getSpawnB().getX(), 0.01);

        // Admin updates Spawn A to a new location in the arena
        ArenaTemplate.SpawnPoint newA = new ArenaTemplate.SpawnPoint(-5.5, 2.0, 3.5, -45f, 0f);
        ArenaTemplate updatedTemplate = template.withSpawnA(newA);

        // Apply update live (what propagateTemplateUpdate does)
        instance.applyTemplateUpdate(updatedTemplate);

        // Spawn A must immediately reflect the update without server reload or reboot
        assertEquals(994.5, instance.getSpawnA().getX(), 0.01, "OriginX (1000) - 5.5 + 0.5 center");
        assertEquals(66.0, instance.getSpawnA().getY(), 0.01, "Baseline 64 + 2.0 = 66.0");
        assertEquals(3.5, instance.getSpawnA().getZ(), 0.01);
        assertEquals(-45f, instance.getSpawnA().getYaw(), 0.01);
    }

    @Test
    @DisplayName("Admin configured spawns are not destroyed by anti-collision guard")
    void testAntiCollisionDoesNotDestroySpawns() {
        World world = mock(World.class);

        ArenaBounds bounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        // Spawns configured 2 blocks apart (e.g. Sumo duel)
        ArenaTemplate.SpawnPoint spawnA = new ArenaTemplate.SpawnPoint(-1.0, 1.0, 0, -90f, 0f);
        ArenaTemplate.SpawnPoint spawnB = new ArenaTemplate.SpawnPoint(1.0, 1.0, 0, 90f, 0f);
        ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0, 10.0, 0, 0f, 45f);

        ArenaTemplate template = new ArenaTemplate(
                2, "sumo_arena", "Sumo", bounds,
                spawnA, spawnB, spec, true, true, 64, 84, null
        );

        ArenaInstance instance = new ArenaInstance(template, 0, world, 64, false);

        // OriginX = 2000. Spawn A: 2000 - 1.0 + 0.5 = 1999.5. Spawn B: 2000 + 1.0 + 0.5 = 2001.5.
        // Distance is 2.0 blocks. Anti-collision should NOT displace them.
        assertEquals(1999.5, instance.getSpawnA().getX(), 0.01);
        assertEquals(2001.5, instance.getSpawnB().getX(), 0.01);
    }
}
