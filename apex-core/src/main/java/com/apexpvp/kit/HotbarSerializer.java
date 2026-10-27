package com.apexpvp.kit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializes and deserializes hotbar slot permutations.
 * Stores mappings: targetHotbarSlot (0-8) -> sourceKitSlot (0-35).
 */
public final class HotbarSerializer {

    private HotbarSerializer() {
        // Utility
    }

    /**
     * Serializes a slot mapping into a compact delimited string: "0:3,1:0,2:1,..."
     */
    @NotNull
    public static String serialize(@NotNull Map<Integer, Integer> slotMapping) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : slotMapping.entrySet()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Deserializes a delimited string back into a slot mapping.
     */
    @NotNull
    public static Map<Integer, Integer> deserialize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> map = new HashMap<>();
        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    int targetSlot = Integer.parseInt(kv[0].trim());
                    int sourceSlot = Integer.parseInt(kv[1].trim());
                    if (targetSlot >= 0 && targetSlot < 9 && sourceSlot >= 0 && sourceSlot < 36) {
                        map.put(targetSlot, sourceSlot);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
