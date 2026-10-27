package com.apexpvp.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotbarLayoutValidatorTest {

    @Test
    @DisplayName("Verify valid injective hotbar permutations are accepted")
    void testValidLayout() {
        Map<Integer, Integer> valid = new HashMap<>();
        valid.put(0, 0);
        valid.put(1, 1);
        valid.put(2, 2);
        valid.put(3, 3);
        valid.put(4, 4);

        assertTrue(HotbarLayoutValidator.isValid(valid));
    }

    @Test
    @DisplayName("Verify duplicate source kit slot (item duplication attempt) is rejected")
    void testRejectItemDuplication() {
        Map<Integer, Integer> duplicateSource = new HashMap<>();
        duplicateSource.put(0, 0); // Slot 0 has sword
        duplicateSource.put(1, 0); // Attempting to duplicate sword to slot 1

        assertFalse(HotbarLayoutValidator.isValid(duplicateSource), "Duplicate source kit slot must be rejected");
    }

    @Test
    @DisplayName("Verify out of bounds target slots (<0 or >8) are rejected")
    void testRejectOutOfBoundsTarget() {
        Map<Integer, Integer> outOfBoundsTarget = new HashMap<>();
        outOfBoundsTarget.put(9, 0); // Hotbar only spans 0-8

        assertFalse(HotbarLayoutValidator.isValid(outOfBoundsTarget));

        Map<Integer, Integer> negativeTarget = new HashMap<>();
        negativeTarget.put(-1, 0);
        assertFalse(HotbarLayoutValidator.isValid(negativeTarget));
    }

    @Test
    @DisplayName("Verify out of bounds source slots (<0 or >35) are rejected")
    void testRejectOutOfBoundsSource() {
        Map<Integer, Integer> outOfBoundsSource = new HashMap<>();
        outOfBoundsSource.put(0, 36); // Main inventory only spans 0-35

        assertFalse(HotbarLayoutValidator.isValid(outOfBoundsSource));
    }
}
