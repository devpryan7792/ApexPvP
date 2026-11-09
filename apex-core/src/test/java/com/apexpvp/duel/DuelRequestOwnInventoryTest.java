package com.apexpvp.duel;

import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Duel request own-inventory flag")
class DuelRequestOwnInventoryTest {

    private DuelRequest request(boolean ownInventory) {
        return new DuelRequest(UUID.randomUUID(), UUID.randomUUID(),
                mock(Kit.class), mock(ArenaTemplate.class), 100.0, ownInventory);
    }

    @Test
    @DisplayName("Default challenge uses kit inventory")
    void testDefaultIsKitInventory() {
        DuelRequest request = new DuelRequest(UUID.randomUUID(), UUID.randomUUID(),
                mock(Kit.class), mock(ArenaTemplate.class), 50.0);
        assertFalse(request.isOwnInventory());
        assertEquals(50.0, request.getStake());
    }

    @Test
    @DisplayName("Own-inventory challenges preserve the flag")
    void testOwnInventoryFlag() {
        assertTrue(request(true).isOwnInventory());
        assertFalse(request(false).isOwnInventory());
    }
}
