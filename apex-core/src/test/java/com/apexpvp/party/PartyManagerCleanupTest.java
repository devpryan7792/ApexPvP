package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.MatchManager;
import com.apexpvp.queue.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Party disconnect and kick cleanup")
class PartyManagerCleanupTest {

    private ApexPvPPlugin plugin;
    private QueueManager queueManager;
    private PartyManager partyManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        MatchManager matchManager = mock(MatchManager.class);
        queueManager = mock(QueueManager.class);

        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("party.max-members", 16)).thenReturn(16);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getQueueManager()).thenReturn(queueManager);

        partyManager = new PartyManager(plugin);
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private void join(Player leader, Player member) {
        assertTrue(partyManager.invitePlayer(leader, member));
        assertTrue(partyManager.acceptInvite(member, leader));
    }

    @Test
    @DisplayName("Leader disconnect transfers leadership and removes them from the map")
    void leaderDisconnectTransfersLeadership() {
        Player leader = player("Leader");
        Player member = player("Member");
        partyManager.createParty(leader);
        join(leader, member);

        partyManager.unregisterPlayerFromParty(leader.getUniqueId());

        Party party = partyManager.getParty(member.getUniqueId());
        assertNotNull(party);
        assertEquals(member.getUniqueId(), party.getLeader());
        assertNull(partyManager.getParty(leader.getUniqueId()));
        assertFalse(party.contains(leader.getUniqueId()));
    }

    @Test
    @DisplayName("Disconnect clears invites sent to and by the player")
    void disconnectClearsInvitesBothDirections() {
        Player leader = player("Leader");
        Player invited = player("Invited");
        partyManager.createParty(leader);

        assertTrue(partyManager.invitePlayer(leader, invited));
        // The invited player disconnects: the invite disappears.
        partyManager.unregisterPlayerFromParty(invited.getUniqueId());
        assertFalse(partyManager.acceptInvite(invited, leader));

        // A leader disconnects: invites they sent also disappear.
        Player second = player("Second");
        assertTrue(partyManager.invitePlayer(leader, second));
        partyManager.unregisterPlayerFromParty(leader.getUniqueId());
        assertFalse(partyManager.acceptInvite(second, leader));
    }

    @Test
    @DisplayName("Disconnect clears a pending party duel challenge")
    void disconnectClearsPendingChallenge() {
        Player leader1 = player("Leader1");
        Player member1 = player("Member1");
        Player leader2 = player("Leader2");
        Player member2 = player("Member2");

        partyManager.createParty(leader1);
        join(leader1, member1);
        partyManager.createParty(leader2);
        join(leader2, member2);

        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
        when(kit.getDisplayName()).thenReturn("Spear");

        assertTrue(partyManager.challengeParty(leader1, leader2, kit));

        partyManager.unregisterPlayerFromParty(leader2.getUniqueId());

        // The target no longer has a challenge to accept.
        assertFalse(partyManager.acceptPartyDuel(leader2, leader1));
    }

    @Test
    @DisplayName("Kicking/removing a queued member also removes them from the queue")
    void unregisterLeavesQueue() {
        Player leader = player("Leader");
        Player member = player("Member");
        partyManager.createParty(leader);
        join(leader, member);

        when(queueManager.isInQueue(member.getUniqueId())).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(member.getUniqueId())).thenReturn(member);
            partyManager.unregisterPlayerFromParty(member.getUniqueId());
        }

        verify(queueManager).leaveQueue(member);
        assertNull(partyManager.getParty(member.getUniqueId()));
        assertEquals(leader.getUniqueId(), partyManager.getParty(leader.getUniqueId()).getLeader());
    }

    @Test
    @DisplayName("Disband removes every member from the party map")
    void disbandClearsAllMembers() {
        Player leader = player("Leader");
        Player member = player("Member");
        partyManager.createParty(leader);
        join(leader, member);

        assertTrue(partyManager.disbandParty(leader));

        assertNull(partyManager.getParty(leader.getUniqueId()));
        assertNull(partyManager.getParty(member.getUniqueId()));
    }
}
