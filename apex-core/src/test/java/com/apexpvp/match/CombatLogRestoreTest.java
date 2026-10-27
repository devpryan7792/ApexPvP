package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Combat Log & Reconnect Inventory Restore Tests")
public class CombatLogRestoreTest {

    private MatchManager matchManager;

    @BeforeEach
    void setUp() {
        matchManager = new MatchManager(null);
    }

    @Test
    @DisplayName("Verify pending restore persistence and single-use consumption upon reconnect")
    void testPendingRestoreLifecycle() {
        UUID combatLogger = UUID.randomUUID();
        PlayerRestoreSnapshot mockSnapshot = Mockito.mock(PlayerRestoreSnapshot.class);

        // Disconnected mid-match: Snapshot saved as pending
        matchManager.savePendingRestore(combatLogger, mockSnapshot);
        assertTrue(matchManager.hasPendingRestore(combatLogger));

        // Reconnect: Player joins, pending restore is consumed
        PlayerRestoreSnapshot retrieved = matchManager.consumePendingRestore(combatLogger);
        assertNotNull(retrieved, "Pending restore should be retrieved on reconnect");
        assertSame(mockSnapshot, retrieved);

        // Verify consumption is atomic and single-use (cannot be restored twice)
        assertFalse(matchManager.hasPendingRestore(combatLogger));
        assertNull(matchManager.consumePendingRestore(combatLogger));
    }

    @Test
    @DisplayName("Verify multiple combat loggers are tracked independently without cross-talk")
    void testMultipleCombatLoggersIsolation() {
        UUID logger1 = UUID.randomUUID();
        UUID logger2 = UUID.randomUUID();

        PlayerRestoreSnapshot snapshot1 = Mockito.mock(PlayerRestoreSnapshot.class);
        PlayerRestoreSnapshot snapshot2 = Mockito.mock(PlayerRestoreSnapshot.class);

        matchManager.savePendingRestore(logger1, snapshot1);
        matchManager.savePendingRestore(logger2, snapshot2);

        assertTrue(matchManager.hasPendingRestore(logger1));
        assertTrue(matchManager.hasPendingRestore(logger2));

        assertSame(snapshot1, matchManager.consumePendingRestore(logger1));
        assertFalse(matchManager.hasPendingRestore(logger1));
        assertTrue(matchManager.hasPendingRestore(logger2));

        assertSame(snapshot2, matchManager.consumePendingRestore(logger2));
        assertFalse(matchManager.hasPendingRestore(logger2));
    }
}
