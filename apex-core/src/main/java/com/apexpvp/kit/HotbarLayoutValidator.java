package com.apexpvp.kit;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates hotbar slot permutations to prevent item duplication or illegal slot indices.
 */
public final class HotbarLayoutValidator {

    private HotbarLayoutValidator() {
        // Utility
    }

    /**
     * Verifies that the provided slot mapping:
     * 1. Only targets valid hotbar slots (0 - 8).
     * 2. Only pulls from valid kit inventory slots (0 - 35).
     * 3. Does not map the same source kit slot to multiple hotbar slots (no item duplication).
     *
     * @param mapping targetHotbarSlot -> sourceKitSlot
     * @return true if the layout is mathematically sound and exploit-free.
     */
    public static boolean isValid(@NotNull Map<Integer, Integer> mapping) {
        if (mapping.size() > 9) {
            return false;
        }

        Set<Integer> targetSlotsUsed = new HashSet<>();
        Set<Integer> sourceSlotsUsed = new HashSet<>();

        for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            int target = entry.getKey();
            int source = entry.getValue();

            // Validate slot bounds
            if (target < 0 || target > 8) return false;
            if (source < 0 || source > 35) return false;

            // Check for duplicate target or source assignments (duplication attempt)
            if (!targetSlotsUsed.add(target)) return false;
            if (!sourceSlotsUsed.add(source)) return false;
        }

        return true;
    }
}
