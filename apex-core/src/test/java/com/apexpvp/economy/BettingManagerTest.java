package com.apexpvp.economy;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchParticipant;
import com.apexpvp.match.MatchState;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BettingManagerTest {

    private ApexPvPPlugin plugin;
    private Economy economy;
    private Server server;
    private FileConfiguration config;
    private BettingManager bettingManager;

    private Match match;
    private UUID matchId;

    private Player combatantA;
    private Player combatantB;
    private UUID combatantAUuid;
    private UUID combatantBUuid;

    private Player bettor1;
    private Player bettor2;
    private Player bettor3;
    private UUID b1Uuid;
    private UUID b2Uuid;
    private UUID b3Uuid;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        economy = mock(Economy.class);
        server = mock(Server.class);
        config = mock(FileConfiguration.class);

        when(plugin.getEconomy()).thenReturn(economy);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(config.getDouble("duels.betting.min-bet", 10.0)).thenReturn(10.0);
        when(config.getDouble("duels.betting.server-tax-percent", 5.0)).thenReturn(5.0);

        combatantAUuid = UUID.randomUUID();
        combatantBUuid = UUID.randomUUID();
        combatantA = mock(Player.class);
        combatantB = mock(Player.class);
        when(combatantA.getUniqueId()).thenReturn(combatantAUuid);
        when(combatantB.getUniqueId()).thenReturn(combatantBUuid);
        when(combatantA.getName()).thenReturn("CombatantA");
        when(combatantB.getName()).thenReturn("CombatantB");

        b1Uuid = UUID.randomUUID();
        b2Uuid = UUID.randomUUID();
        b3Uuid = UUID.randomUUID();
        bettor1 = mock(Player.class);
        bettor2 = mock(Player.class);
        bettor3 = mock(Player.class);
        when(bettor1.getUniqueId()).thenReturn(b1Uuid);
        when(bettor2.getUniqueId()).thenReturn(b2Uuid);
        when(bettor3.getUniqueId()).thenReturn(b3Uuid);
        when(bettor1.getName()).thenReturn("Bettor1");
        when(bettor2.getName()).thenReturn("Bettor2");
        when(bettor3.getName()).thenReturn("Bettor3");

        OfflinePlayer offB1 = mock(OfflinePlayer.class);
        OfflinePlayer offB2 = mock(OfflinePlayer.class);
        OfflinePlayer offB3 = mock(OfflinePlayer.class);
        when(server.getOfflinePlayer(b1Uuid)).thenReturn(offB1);
        when(server.getOfflinePlayer(b2Uuid)).thenReturn(offB2);
        when(server.getOfflinePlayer(b3Uuid)).thenReturn(offB3);

        matchId = UUID.randomUUID();
        match = mock(Match.class);
        when(match.getMatchId()).thenReturn(matchId);
        when(match.getState()).thenReturn(MatchState.COUNTDOWN);

        MatchParticipant partA = new MatchParticipant(combatantAUuid, "CombatantA", 0);
        MatchParticipant partB = new MatchParticipant(combatantBUuid, "CombatantB", 1);
        when(match.getParticipants()).thenReturn(Arrays.asList(partA, partB));
        when(match.getParticipant(combatantAUuid)).thenReturn(partA);
        when(match.getParticipant(combatantBUuid)).thenReturn(partB);
        when(match.isParticipant(combatantAUuid)).thenReturn(true);
        when(match.isParticipant(combatantBUuid)).thenReturn(true);
        when(match.isParticipant(b1Uuid)).thenReturn(false);
        when(match.isParticipant(b2Uuid)).thenReturn(false);
        when(match.isParticipant(b3Uuid)).thenReturn(false);

        bettingManager = new BettingManager(plugin);
    }

    @Test
    @DisplayName("Verify successful bet placement and pot accumulation")
    void testPlaceBet() {
        when(economy.has(bettor1, 50.0)).thenReturn(true);
        when(economy.withdrawPlayer(bettor1, 50.0))
                .thenReturn(new EconomyResponse(50.0, 450.0, EconomyResponse.ResponseType.SUCCESS, ""));

        boolean placed = bettingManager.placeBet(bettor1, match, combatantAUuid, 50.0);
        assertTrue(placed);

        var pool = bettingManager.getPool(matchId);
        assertNotNull(pool);
        assertEquals(50.0, pool.getTotalPot());
        assertEquals(50.0, pool.getTotalBetsFor(combatantAUuid));
    }

    @Test
    @DisplayName("Verify rejection of bets below minimum or after match starts")
    void testBetRejections() {
        // Below min bet
        boolean lowBet = bettingManager.placeBet(bettor1, match, combatantAUuid, 5.0);
        assertFalse(lowBet);

        // After match active
        when(match.getState()).thenReturn(MatchState.ACTIVE);
        when(economy.has(bettor1, 50.0)).thenReturn(true);
        boolean lateBet = bettingManager.placeBet(bettor1, match, combatantAUuid, 50.0);
        assertFalse(lateBet);

        // Combatant betting on match
        when(match.getState()).thenReturn(MatchState.COUNTDOWN);
        when(match.isParticipant(bettor1.getUniqueId())).thenReturn(true);
        boolean combatantBet = bettingManager.placeBet(bettor1, match, combatantAUuid, 50.0);
        assertFalse(combatantBet);
    }

    @Test
    @DisplayName("Verify pari-mutuel dividend payout with server tax")
    void testPariMutuelPayout() {
        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));

        // Bettor 1 bets 100 on Combatant A
        bettingManager.placeBet(bettor1, match, combatantAUuid, 100.0);
        // Bettor 2 bets 200 on Combatant A
        bettingManager.placeBet(bettor2, match, combatantAUuid, 200.0);
        // Bettor 3 bets 300 on Combatant B
        bettingManager.placeBet(bettor3, match, combatantBUuid, 300.0);

        var pool = bettingManager.getPool(matchId);
        assertNotNull(pool);
        assertEquals(600.0, pool.getTotalPot()); // 100 + 200 + 300 = 600

        // Combatant A (Team 0) wins!
        // Total pot = 600.0. Tax = 5% (30.0). Net pool = 570.0.
        // Total bets on A = 300.0.
        // Bettor 1 share = 100/300 = 1/3 -> 190.0 payout.
        // Bettor 2 share = 200/300 = 2/3 -> 380.0 payout.
        bettingManager.onMatchEnd(match, 0);

        assertNull(bettingManager.getPool(matchId), "Pool must be removed after payout");
        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b1Uuid), 190.0);
        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b2Uuid), 380.0);
        verify(economy, never()).depositPlayer(eq(server.getOfflinePlayer(b3Uuid)), anyDouble());
    }

    @Test
    @DisplayName("Verify 100% refund on match draw or cancellation")
    void testRefundOnDraw() {
        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));

        bettingManager.placeBet(bettor1, match, combatantAUuid, 100.0);
        bettingManager.placeBet(bettor3, match, combatantBUuid, 300.0);

        // Draw (teamIndex -1)
        bettingManager.onMatchEnd(match, -1);

        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b1Uuid), 100.0);
        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b3Uuid), 300.0);
    }

    @Test
    @DisplayName("Team matches pay bettors of every winning-team member")
    void testTeamPayoutCoversAllWinners() {
        Match teamMatch = mock(Match.class);
        UUID teamMatchId = UUID.randomUUID();
        when(teamMatch.getMatchId()).thenReturn(teamMatchId);
        when(teamMatch.getState()).thenReturn(MatchState.COUNTDOWN);

        UUID winner1 = UUID.randomUUID();
        UUID winner2 = UUID.randomUUID();
        UUID loser1 = UUID.randomUUID();
        UUID loser2 = UUID.randomUUID();
        MatchParticipant w1 = new MatchParticipant(winner1, "Winner1", 0);
        MatchParticipant w2 = new MatchParticipant(winner2, "Winner2", 0);
        MatchParticipant l1 = new MatchParticipant(loser1, "Loser1", 1);
        MatchParticipant l2 = new MatchParticipant(loser2, "Loser2", 1);
        when(teamMatch.getParticipants()).thenReturn(Arrays.asList(w1, w2, l1, l2));
        when(teamMatch.getParticipant(winner1)).thenReturn(w1);
        when(teamMatch.getParticipant(winner2)).thenReturn(w2);
        when(teamMatch.getParticipant(loser1)).thenReturn(l1);
        when(teamMatch.getParticipant(loser2)).thenReturn(l2);
        when(teamMatch.isParticipant(winner1)).thenReturn(true);
        when(teamMatch.isParticipant(winner2)).thenReturn(true);
        when(teamMatch.isParticipant(loser1)).thenReturn(true);
        when(teamMatch.isParticipant(loser2)).thenReturn(true);

        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));

        bettingManager.placeBet(bettor1, teamMatch, winner1, 100.0);
        bettingManager.placeBet(bettor2, teamMatch, winner2, 100.0);
        bettingManager.placeBet(bettor3, teamMatch, loser1, 200.0);

        // Pool 400, winning bets 200, tax 5% -> net 380; each 100-stake bettor gets 190.
        bettingManager.onMatchEnd(teamMatch, 0);

        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b1Uuid), 190.0);
        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b2Uuid), 190.0);
        verify(economy, never()).depositPlayer(eq(server.getOfflinePlayer(b3Uuid)), anyDouble());
    }

    @Test
    @DisplayName("Bets on a non-first winning-team member are paid, not refunded")
    void testTeamPayoutWhenOnlySecondaryWinnerHasBets() {
        Match teamMatch = mock(Match.class);
        UUID teamMatchId = UUID.randomUUID();
        when(teamMatch.getMatchId()).thenReturn(teamMatchId);
        when(teamMatch.getState()).thenReturn(MatchState.COUNTDOWN);

        UUID winner1 = UUID.randomUUID();
        UUID winner2 = UUID.randomUUID();
        UUID loser1 = UUID.randomUUID();
        MatchParticipant w1 = new MatchParticipant(winner1, "Winner1", 0);
        MatchParticipant w2 = new MatchParticipant(winner2, "Winner2", 0);
        MatchParticipant l1 = new MatchParticipant(loser1, "Loser1", 1);
        when(teamMatch.getParticipants()).thenReturn(Arrays.asList(w1, w2, l1));
        when(teamMatch.getParticipant(winner1)).thenReturn(w1);
        when(teamMatch.getParticipant(winner2)).thenReturn(w2);
        when(teamMatch.getParticipant(loser1)).thenReturn(l1);
        when(teamMatch.isParticipant(winner1)).thenReturn(true);
        when(teamMatch.isParticipant(winner2)).thenReturn(true);
        when(teamMatch.isParticipant(loser1)).thenReturn(true);

        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100.0, 900.0, EconomyResponse.ResponseType.SUCCESS, ""));

        bettingManager.placeBet(bettor1, teamMatch, winner2, 100.0);
        bettingManager.placeBet(bettor3, teamMatch, loser1, 100.0);

        // Pool 200, tax 5% -> net 190 to the only winning bettor (no 200 refund).
        bettingManager.onMatchEnd(teamMatch, 0);

        verify(economy, times(1)).depositPlayer(server.getOfflinePlayer(b1Uuid), 190.0);
        verify(economy, never()).depositPlayer(eq(server.getOfflinePlayer(b3Uuid)), anyDouble());
    }
}
