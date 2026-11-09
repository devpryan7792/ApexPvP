package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Arena relative spawn math")
class ArenaRelativeSpawnTest {

    private ArenaManager manager;
    private World arenaWorld;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        com.apexpvp.ApexPvPPlugin plugin = mock(com.apexpvp.ApexPvPPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getString("arenas.world-name", "apex_arenas")).thenReturn("apex_arenas");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(new File("build/tmp/arena-test"));

        manager = new ArenaManager(plugin);

        arenaWorld = mock(World.class);
        Field worldField = ArenaManager.class.getDeclaredField("arenaWorld");
        worldField.setAccessible(true);
        worldField.set(manager, arenaWorld);
    }

    @Test
    @DisplayName("A non-zero Z origin is subtracted when converting a world location")
    void nonZeroZOriginIsSubtracted() {
        World world = arenaWorld;
        Location loc = new Location(world, 7010.5, 67.0, 2005.5, 90.0f, 10.0f);

        ArenaBounds bounds = new ArenaBounds(7000, 60, 2000, 7100, 90, 2100);
        ArenaInstance instance = mock(ArenaInstance.class);
        when(instance.getBounds()).thenReturn(bounds);
        when(instance.getOriginX()).thenReturn(7000);
        when(instance.getOriginY()).thenReturn(64);
        when(instance.getOriginZ()).thenReturn(2000);

        installInstance("test", instance);

        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getTemplateId()).thenReturn(7);

        ArenaTemplate.SpawnPoint spawn = manager.toRelativeSpawn(template, loc);

        // Origin is (7000, 64, 2000), so Z must be 5.5, not 2005.5.
        assertEquals(10.5, spawn.x(), 0.0001);
        assertEquals(3.0, spawn.y(), 0.0001);
        assertEquals(5.5, spawn.z(), 0.0001);
        assertEquals(90.0f, spawn.yaw(), 0.0001);
        assertEquals(10.0f, spawn.pitch(), 0.0001);
    }

    @Test
    @DisplayName("Locations outside the arena world fall back to the template origin")
    void outsideWorldFallsBackToTemplateOrigin() {
        World otherWorld = mock(World.class);
        Location loc = new Location(otherWorld, 7010.0, 67.0, 5.0);

        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getTemplateId()).thenReturn(7);

        ArenaTemplate.SpawnPoint spawn = manager.toRelativeSpawn(template, loc);

        // Fallback origin is (templateId * 1000, 64, 0).
        assertEquals(10.0, spawn.x(), 0.0001);
        assertEquals(3.0, spawn.y(), 0.0001);
        assertEquals(5.0, spawn.z(), 0.0001);
    }

    @SuppressWarnings("unchecked")
    private void installInstance(String templateId, ArenaInstance instance) {
        try {
            Field instancesField = ArenaManager.class.getDeclaredField("instances");
            instancesField.setAccessible(true);
            Map<String, List<ArenaInstance>> instances =
                    (Map<String, List<ArenaInstance>>) instancesField.get(manager);
            if (instances == null) {
                instances = new ConcurrentHashMap<>();
                instancesField.set(manager, instances);
            }
            instances.put(templateId, List.of(instance));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not install test instance", e);
        }
    }
}
