package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Match quit/rejoin detach behavior")
class MatchQuitRestoreTest {

    private ApexPvPPlugin plugin;
    private MatchManager matchManager;
    private Player player;
    private UUID uuid;
    private TestMatch match;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        matchManager = mock(MatchManager.class);
        when(plugin.getMatchManager()).thenReturn(matchManager);

        player = mock(Player.class);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Runner");

        match = new TestMatch(plugin, mock(ArenaInstance.class), mock(Kit.class));
    }

    @Test
    @DisplayName("Quitting detaches the participant and queues the snapshot for rejoin restore")
    void testQuitSavesPendingRestoreAndUnregisters() {
        match.addParticipant(player, 0);
        PlayerRestoreSnapshot snapshot = mock(PlayerRestoreSnapshot.class);
        match.snapshots.put(uuid, snapshot);

        match.onPlayerQuit(player);

        assertNull(match.getParticipant(uuid), "quitter must leave the match roster");
        assertFalse(match.snapshots.containsKey(uuid), "snapshot must move to pending restore");
        verify(matchManager).savePendingRestore(uuid, snapshot);
        verify(matchManager).unregisterParticipant(uuid);
    }

    @Test
    @DisplayName("Quitting without a snapshot still unregisters the participant")
    void testQuitWithoutSnapshotStillUnregisters() {
        match.addParticipant(player, 0);

        match.onPlayerQuit(player);

        assertNull(match.getParticipant(uuid));
        verify(matchManager, never()).savePendingRestore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(matchManager).unregisterParticipant(uuid);
    }

    private static final class TestMatch extends Match {
        private TestMatch(ApexPvPPlugin plugin, ArenaInstance arena, Kit kit) {
            super(plugin, arena, kit);
        }

        @Override
        protected void processMatchResults(int winningTeamIndex) {
        }
    }
}
