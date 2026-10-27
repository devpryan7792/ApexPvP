package com.apexpvp.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotbarSerializerTest {

    @Test
    @DisplayName("Verify serialization and deserialization roundtrip of slot mappings")
    void testSerializationRoundtrip() {
        Map<Integer, Integer> original = new HashMap<>();
        original.put(0, 3);
        original.put(1, 0);
        original.put(2, 1);
        original.put(3, 2);

        String serialized = HotbarSerializer.serialize(original);
        assertNotNull(serialized);
        assertFalse(serialized.isBlank());

        Map<Integer, Integer> deserialized = HotbarSerializer.deserialize(serialized);
        assertEquals(4, deserialized.size());
        assertEquals(3, deserialized.get(0));
        assertEquals(0, deserialized.get(1));
        assertEquals(1, deserialized.get(2));
        assertEquals(2, deserialized.get(3));
    }

    @Test
    @DisplayName("Verify handling of null, empty, or corrupted strings")
    void testCorruptedInput() {
        assertTrue(HotbarSerializer.deserialize(null).isEmpty());
        assertTrue(HotbarSerializer.deserialize("").isEmpty());
        assertTrue(HotbarSerializer.deserialize("   ").isEmpty());

        // Partially corrupted string
        Map<Integer, Integer> partial = HotbarSerializer.deserialize("0:1,invalid,2:5,99:99");
        assertEquals(2, partial.size());
        assertEquals(1, partial.get(0));
        assertEquals(5, partial.get(2));
        assertNull(partial.get(99), "Out of bounds target slot must be dropped");
    }
}
