package com.apexpvp.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KitEditorLayoutTest {

    @Test
    @DisplayName("Verify valid hotbar layout permits full 9 hotbar slot reorderings")
    void testValidHotbarLayoutPermutations() {
        Map<Integer, Integer> layout = new HashMap<>();
        // Player moves sword from slot 0 to slot 1, pearls from slot 1 to slot 0
        layout.put(0, 1); // Hotbar slot 0 gets pearls (kit slot 1)
        layout.put(1, 0); // Hotbar slot 1 gets sword (kit slot 0)
        layout.put(2, 2); // Hotbar slot 2 gets speed
        layout.put(3, 3); // Hotbar slot 3 gets fire res
        layout.put(4, 9); // Hotbar slot 4 pulls a heal pot from inventory slot 9
        layout.put(5, 10); // Hotbar slot 5 pulls a heal pot from inventory slot 10
        layout.put(6, 11);
        layout.put(7, 12);
        layout.put(8, 8); // Hotbar slot 8 gets steak

        assertTrue(HotbarLayoutValidator.isValid(layout));

        String serialized = HotbarSerializer.serialize(layout);
        assertNotNull(serialized);
        assertFalse(serialized.isBlank());

        Map<Integer, Integer> deserialized = HotbarSerializer.deserialize(serialized);
        assertEquals(layout.size(), deserialized.size());
        assertEquals(1, deserialized.get(0));
        assertEquals(0, deserialized.get(1));
        assertEquals(9, deserialized.get(4));
        assertEquals(8, deserialized.get(8));
    }

    @Test
    @DisplayName("Verify validator catches duplication exploits where same source slot is mapped twice")
    void testDuplicationExploitPrevention() {
        Map<Integer, Integer> exploitLayout = new HashMap<>();
        exploitLayout.put(0, 0); // Hotbar 0 gets sword
        exploitLayout.put(1, 0); // Attempting to duplicate sword into Hotbar 1!

        assertFalse(HotbarLayoutValidator.isValid(exploitLayout), "Layout duplicating slot 0 must fail validation");
    }

    @Test
    @DisplayName("Verify validator catches out-of-bounds slot assignments")
    void testOutOfBoundsPrevention() {
        Map<Integer, Integer> outOfBoundsTarget = new HashMap<>();
        outOfBoundsTarget.put(9, 0); // Hotbar is 0-8, 9 is illegal target
        assertFalse(HotbarLayoutValidator.isValid(outOfBoundsTarget));

        Map<Integer, Integer> outOfBoundsSource = new HashMap<>();
        outOfBoundsSource.put(0, 36); // Main inventory is 0-35, 36 is illegal source
        assertFalse(HotbarLayoutValidator.isValid(outOfBoundsSource));

        Map<Integer, Integer> negativeSource = new HashMap<>();
        negativeSource.put(0, -1);
        assertFalse(HotbarLayoutValidator.isValid(negativeSource));
    }

    @Test
    @DisplayName("Verify empty and null serialization edge cases")
    void testEmptySerialization() {
        assertEquals("", HotbarSerializer.serialize(Map.of()));
        assertTrue(HotbarSerializer.deserialize(null).isEmpty());
        assertTrue(HotbarSerializer.deserialize("").isEmpty());
        assertTrue(HotbarSerializer.deserialize("   ").isEmpty());
        assertTrue(HotbarSerializer.deserialize("invalid:format:here").isEmpty());
    }
}
