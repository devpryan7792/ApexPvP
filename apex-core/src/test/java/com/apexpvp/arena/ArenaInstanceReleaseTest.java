package com.apexpvp.arena;

import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Arena instance release without regeneration")
class ArenaInstanceReleaseTest {

    private ArenaInstance instance;

    @BeforeEach
    void setUp() {
        ArenaBounds bounds = mock(ArenaBounds.class);
        when(bounds.offset(1000, 64, 0)).thenReturn(bounds);
        when(bounds.getChunkKeys()).thenReturn(Set.of());
        when(bounds.getMaxX()).thenReturn(30);
        when(bounds.getMinX()).thenReturn(-30);
        when(bounds.getMaxZ()).thenReturn(30);
        when(bounds.getMinZ()).thenReturn(-30);

        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getTemplateId()).thenReturn(1);
        when(template.getRelativeBounds()).thenReturn(bounds);
        when(template.getRelativeSpawnA()).thenReturn(new ArenaTemplate.SpawnPoint(0, 1, 0, 0, 0));
        when(template.getRelativeSpawnB()).thenReturn(new ArenaTemplate.SpawnPoint(0, 1, 0, 0, 0));
        when(template.getRelativeSpectatorSpawn()).thenReturn(new ArenaTemplate.SpawnPoint(0, 5, 0, 0, 0));
        when(template.getRelativeFfaSpawn()).thenReturn(null);

        instance = new ArenaInstance(template, 0, mock(World.class), 64, false);
    }

    @Test
    @DisplayName("finishWithoutRollback returns the instance to AVAILABLE and runs the callback")
    void testFinishWithoutRollback() {
        assertTrue(instance.acquire(mock(org.bukkit.plugin.Plugin.class)));
        assertEquals(ArenaState.IN_USE, instance.getState());

        AtomicBoolean callback = new AtomicBoolean(false);
        instance.finishWithoutRollback(mock(org.bukkit.plugin.Plugin.class), () -> callback.set(true));

        assertEquals(ArenaState.AVAILABLE, instance.getState());
        assertTrue(callback.get());
    }
}
