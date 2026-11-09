package com.apexpvp.economy;

import com.apexpvp.ApexPvPPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EscrowManagerTest {

    private ApexPvPPlugin plugin;
    private Economy economy;
    private Server server;
    private EscrowManager escrowManager;

    private Player player1;
    private Player player2;
    private UUID p1Uuid;
    private UUID p2Uuid;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        economy = mock(Economy.class);
        server = mock(Server.class);

        when(plugin.getEconomy()).thenReturn(economy);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        player1 = mock(Player.class);
        player2 = mock(Player.class);
        p1Uuid = UUID.randomUUID();
        p2Uuid = UUID.randomUUID();

        when(player1.getUniqueId()).thenReturn(p1Uuid);
        when(player2.getUniqueId()).thenReturn(p2Uuid);
        when(player1.getName()).thenReturn("Challenger");
        when(player2.getName()).thenReturn("Target");

        OfflinePlayer offline1 = mock(OfflinePlayer.class);
        OfflinePlayer offline2 = mock(OfflinePlayer.class);
        when(server.getOfflinePlayer(p1Uuid)).thenReturn(offline1);
        when(server.getOfflinePlayer(p2Uuid)).thenReturn(offline2);

        escrowManager = new EscrowManager(plugin);
    }

    @Test
    @DisplayName("Verify successful atomic stake locking from both participants")
    void testLockStakeSuccess() {
        UUID matchId = UUID.randomUUID();
        double stake = 100.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.has(player2, stake)).thenReturn(true);
        when(economy.withdrawPlayer(player1, stake))
                .thenReturn(new EconomyResponse(stake, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.withdrawPlayer(player2, stake))
                .thenReturn(new EconomyResponse(stake, 400.0, EconomyResponse.ResponseType.SUCCESS, ""));

        boolean locked = escrowManager.lockStake(matchId, player1, player2, stake);
        assertTrue(locked);

        verify(economy, times(1)).withdrawPlayer(player1, stake);
        verify(economy, times(1)).withdrawPlayer(player2, stake);
    }

    @Test
    @DisplayName("Verify rejection when a participant has insufficient funds")
    void testLockStakeInsufficientFunds() {
        UUID matchId = UUID.randomUUID();
        double stake = 500.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.has(player2, stake)).thenReturn(false);

        boolean locked = escrowManager.lockStake(matchId, player1, player2, stake);
        assertFalse(locked);

        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    @Test
    @DisplayName("Verify atomic rollback of player 1 stake if player 2 debit fails")
    void testLockStakeAtomicRollback() {
        UUID matchId = UUID.randomUUID();
        double stake = 200.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.has(player2, stake)).thenReturn(true);
        when(economy.withdrawPlayer(player1, stake))
                .thenReturn(new EconomyResponse(stake, 800.0, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.withdrawPlayer(player2, stake))
                .thenReturn(new EconomyResponse(0.0, 10.0, EconomyResponse.ResponseType.FAILURE, "Transaction rejected"));

        boolean locked = escrowManager.lockStake(matchId, player1, player2, stake);
        assertFalse(locked);

        // Player 1 must have been refunded immediately
        verify(economy, times(1)).depositPlayer(player1, stake);
    }

    @Test
    @DisplayName("Verify tax calculation and net payout to victor")
    void testDistributePayoutWithTax() {
        UUID matchId = UUID.randomUUID();
        double stake = 100.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.has(player2, stake)).thenReturn(true);
        when(economy.withdrawPlayer(eq(player1), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.withdrawPlayer(eq(player2), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 400.0, EconomyResponse.ResponseType.SUCCESS, ""));

        escrowManager.lockStake(matchId, player1, player2, stake);

        OfflinePlayer winner = mock(OfflinePlayer.class);
        when(winner.getName()).thenReturn("Challenger");
        when(winner.getUniqueId()).thenReturn(p1Uuid);

        // Total pot = 200.0, Tax = 5% (10.0), Net = 190.0
        escrowManager.distributePayout(matchId, winner, 5.0);

        verify(economy, times(1)).depositPlayer(winner, 190.0);
    }

    @Test
    @DisplayName("Verify refund returns identical stake to each participant")
    void testRefund() {
        UUID matchId = UUID.randomUUID();
        double stake = 75.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.has(player2, stake)).thenReturn(true);
        when(economy.withdrawPlayer(eq(player1), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.withdrawPlayer(eq(player2), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 400.0, EconomyResponse.ResponseType.SUCCESS, ""));

        escrowManager.lockStake(matchId, player1, player2, stake);
        escrowManager.refund(matchId);

        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(p1Uuid), 75.0);
        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(p2Uuid), 75.0);
    }

    @Test
    @DisplayName("Verify zero stake is treated as free match without economy calls")
    void testZeroStakeIsFreeMatch() {
        UUID matchId = UUID.randomUUID();
        boolean locked = escrowManager.lockStake(matchId, player1, player2, 0.0);
        assertTrue(locked);

        verify(economy, never()).has(any(Player.class), anyDouble());
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    @Test
    @DisplayName("Simulated win returns the single stake minus tax, never 2x")
    void testSimulatedPayoutIsSingleStake() {
        UUID matchId = UUID.randomUUID();
        UUID botUuid = UUID.randomUUID();
        double stake = 100.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.withdrawPlayer(eq(player1), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));

        assertTrue(escrowManager.lockSimulatedStake(matchId, player1, botUuid, stake));
        verify(economy, times(1)).withdrawPlayer(player1, stake);

        OfflinePlayer winner = mock(OfflinePlayer.class);
        when(winner.getName()).thenReturn("Challenger");

        escrowManager.distributePayout(matchId, winner, 5.0);

        // Pot = 100 (one contribution), tax = 5, net = 95. Paying 190 would mint coins.
        verify(economy, times(1)).depositPlayer(winner, 95.0);
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), eq(190.0));
    }

    @Test
    @DisplayName("Simulated loss consumes the stake without any payout")
    void testSimulatedLossConsumesStake() {
        UUID matchId = UUID.randomUUID();
        UUID botUuid = UUID.randomUUID();
        double stake = 50.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.withdrawPlayer(eq(player1), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 450.0, EconomyResponse.ResponseType.SUCCESS, ""));

        assertTrue(escrowManager.lockSimulatedStake(matchId, player1, botUuid, stake));
        escrowManager.consumeEscrow(matchId);

        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    @DisplayName("Simulated draw refunds the human stake once and never the bot")
    void testSimulatedRefundPaysOnlyHuman() {
        UUID matchId = UUID.randomUUID();
        UUID botUuid = UUID.randomUUID();
        double stake = 30.0;

        when(economy.has(player1, stake)).thenReturn(true);
        when(economy.withdrawPlayer(eq(player1), eq(stake)))
                .thenReturn(new EconomyResponse(stake, 470.0, EconomyResponse.ResponseType.SUCCESS, ""));

        assertTrue(escrowManager.lockSimulatedStake(matchId, player1, botUuid, stake));
        escrowManager.refund(matchId);

        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(p1Uuid), stake);
        verify(economy, never()).depositPlayer(server.getOfflinePlayer(botUuid), stake);
    }
}
