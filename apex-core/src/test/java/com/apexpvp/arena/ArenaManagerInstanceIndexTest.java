package com.apexpvp.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Arena dynamic instance index allocation")
class ArenaManagerInstanceIndexTest {

    private ArenaInstance instance(int index) {
        ArenaInstance instance = mock(ArenaInstance.class);
        when(instance.getInstanceIndex()).thenReturn(index);
        return instance;
    }

    @Test
    @DisplayName("Empty list starts at index 1 (0 is reserved for the static instance)")
    void testEmptyList() {
        assertEquals(1, ArenaManager.computeNextInstanceIndex(List.of()));
    }

    @Test
    @DisplayName("Sequential indexes continue from the maximum")
    void testSequentialIndexes() {
        List<ArenaInstance> list = new ArrayList<>(List.of(instance(0), instance(1), instance(2)));
        assertEquals(3, ArenaManager.computeNextInstanceIndex(list));
    }

    @Test
    @DisplayName("Removing a middle instance never reuses a live instance's Z origin")
    void testMiddleRemovalDoesNotCollide() {
        // Simulates [0,1,2] with index 1 removed: size is 2, but index 2 is still live.
        List<ArenaInstance> list = new ArrayList<>(List.of(instance(0), instance(2)));
        assertEquals(3, ArenaManager.computeNextInstanceIndex(list));
    }

    @Test
    @DisplayName("Removing the highest index can safely reuse it")
    void testHighestRemoval() {
        List<ArenaInstance> list = new ArrayList<>(List.of(instance(0), instance(1)));
        assertEquals(2, ArenaManager.computeNextInstanceIndex(list));
    }
}
