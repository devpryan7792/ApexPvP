package com.apexpvp.queue;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.MatchManager;
import com.apexpvp.party.Party;
import com.apexpvp.party.PartyManager;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.ProfileManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Queue entry fee integrity")
class QueueManagerFeesTest {

    private ApexPvPPlugin plugin;
    private FileConfiguration config;
    private Economy economy;
    private MatchManager matchManager;
    private ProfileManager profileManager;
    private PartyManager partyManager;
    private QueueManager queueManager;

    private Kit kit;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        config = mock(FileConfiguration.class);
        economy = mock(Economy.class);
        matchManager = mock(MatchManager.class);
        profileManager = mock(ProfileManager.class);
        partyManager = mock(PartyManager.class);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getEconomy()).thenReturn(economy);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getProfileManager()).thenReturn(profileManager);
        when(plugin.getPartyManager()).thenReturn(partyManager);
        when(plugin.getServer()).thenReturn(mock(Server.class));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        when(config.getBoolean("ranked.economy.enabled", true)).thenReturn(true);
        when(config.getDouble("ranked.economy.entry-fee", 25.0)).thenReturn(25.0);
        when(config.getDouble("ffa.entry-fee", 10.0)).thenReturn(10.0);
        when(config.getInt("ffa.queue-size", 4)).thenReturn(4);

        kit = mock(Kit.class);
        when(kit.getId()).thenReturn("nodebuff");
        when(kit.getDisplayName()).thenReturn("NoDebuff");

        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(success(25.0));

        queueManager = new QueueManager(plugin);
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);

        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getElo()).thenReturn(1000);
        when(profileManager.getProfile(uuid)).thenReturn(profile);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        }
        return player;
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(amount, 1000.0, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Test
    @DisplayName("A player in the FFA queue cannot also join ranked")
    void ffaQueuedPlayerCannotJoinRanked() {
        Player player = player("FfaPlayer");

        assertTrue(queueManager.queueFfaPlayer(player, kit));
        assertFalse(queueManager.queuePlayer(player, kit));
        assertNull(queueManager.getTicket(player.getUniqueId()));

        // Leaving the FFA queue refunds exactly the FFA fee once.
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            assertTrue(queueManager.leaveQueue(player));
        }
        verify(economy, times(1)).depositPlayer(player, 10.0);
    }

    @Test
    @DisplayName("A party member already in FFA blocks the team queue")
    void ffaQueuedPartyMemberBlocksTeamQueue() {
        Player leader = player("Leader");
        Player member = player("Member");
        UUID leaderId = leader.getUniqueId();
        UUID memberId = member.getUniqueId();

        Party party = mock(Party.class);
        when(party.getSize()).thenReturn(2);
        when(party.getMembers()).thenReturn(new LinkedHashSet<>(List.of(leaderId, memberId)));
        when(party.isLeader(leaderId)).thenReturn(true);
        when(partyManager.getParty(leader)).thenReturn(party);

        assertTrue(queueManager.queueFfaPlayer(member, kit));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(leaderId)).thenReturn(leader);
            bukkit.when(() -> Bukkit.getPlayer(memberId)).thenReturn(member);
            assertFalse(queueManager.queueParty(leader, kit));
        }
        assertNull(queueManager.getTicket(leaderId));
    }

    @Test
    @DisplayName("Leaving ranked refunds the recorded fee exactly once")
    void soloLeaveRefundsOnce() {
        Player player = player("Solo");
        when(economy.withdrawPlayer(player, 25.0)).thenReturn(success(25.0));
        when(economy.has(player, 25.0)).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertTrue(queueManager.queuePlayer(player, kit));
            verify(economy).withdrawPlayer(player, 25.0);

            assertTrue(queueManager.leaveQueue(player));
            assertFalse(queueManager.leaveQueue(player));
        }

        verify(economy, times(1)).depositPlayer(player, 25.0);
    }

    @Test
    @DisplayName("A partial party withdrawal failure rolls back everyone and queues nobody")
    void partyWithdrawalFailureRollsBack() {
        Player leader = player("Leader");
        Player member = player("Member");
        UUID leaderId = leader.getUniqueId();
        UUID memberId = member.getUniqueId();

        Party party = mock(Party.class);
        when(party.getSize()).thenReturn(2);
        when(party.getMembers()).thenReturn(new LinkedHashSet<>(List.of(leaderId, memberId)));
        when(party.isLeader(leaderId)).thenReturn(true);
        when(partyManager.getParty(leader)).thenReturn(party);

        when(economy.has(any(Player.class), eq(25.0))).thenReturn(true);
        when(economy.withdrawPlayer(leader, 25.0)).thenReturn(success(25.0));
        when(economy.withdrawPlayer(member, 25.0))
                .thenReturn(new EconomyResponse(0.0, 1000.0, EconomyResponse.ResponseType.FAILURE, "no funds"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(leaderId)).thenReturn(leader);
            bukkit.when(() -> Bukkit.getPlayer(memberId)).thenReturn(member);

            assertFalse(queueManager.queueParty(leader, kit));
        }

        verify(economy).depositPlayer(leader, 25.0);
        verify(economy, never()).depositPlayer(member, 25.0);
        assertFalse(queueManager.isInQueue(leaderId));
        assertFalse(queueManager.isInQueue(memberId));
    }

    @Test
    @DisplayName("Shutdown refunds everyone still waiting in ranked and FFA")
    void stopRefundsQueuedPlayers() {
        Player ranked = player("Ranked");
        Player ffa = player("Ffa");

        when(economy.has(any(Player.class), anyDouble())).thenReturn(true);
        when(economy.withdrawPlayer(any(Player.class), anyDouble()))
                .thenReturn(success(25.0));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(ranked.getUniqueId())).thenReturn(ranked);
            bukkit.when(() -> Bukkit.getPlayer(ffa.getUniqueId())).thenReturn(ffa);

            assertTrue(queueManager.queuePlayer(ranked, kit));
            assertTrue(queueManager.queueFfaPlayer(ffa, kit));

            queueManager.stop();
        }

        verify(economy, times(1)).depositPlayer(ranked, 25.0);
        verify(economy, times(1)).depositPlayer(ffa, 10.0);
        assertFalse(queueManager.isInQueue(ranked.getUniqueId()));
        assertFalse(queueManager.isInQueue(ffa.getUniqueId()));
    }
}
