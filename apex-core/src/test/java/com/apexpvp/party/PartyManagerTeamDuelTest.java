package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.MatchManager;
import com.apexpvp.queue.QueueManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Party Manager 2v2 - 4v4 Team Duel & Split Tests")
class PartyManagerTeamDuelTest {

    private ApexPvPPlugin plugin;
    private FileConfiguration config;
    private MatchManager matchManager;
    private ArenaManager arenaManager;
    private QueueManager queueManager;
    private PartyManager partyManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        config = mock(FileConfiguration.class);
        matchManager = mock(MatchManager.class);
        arenaManager = mock(ArenaManager.class);
        queueManager = mock(QueueManager.class);

        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("party.max-members", 16)).thenReturn(16);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getArenaManager()).thenReturn(arenaManager);
        when(plugin.getQueueManager()).thenReturn(queueManager);

        partyManager = new PartyManager(plugin);
    }

    private Player createMockPlayer(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test
    @DisplayName("Verify party duel requires min 2v2 and max 4v4 with matching sizes")
    void testPartyDuelSizeValidation() {
        Player leader1 = createMockPlayer(UUID.randomUUID(), "Leader1");
        Player leader2 = createMockPlayer(UUID.randomUUID(), "Leader2");
        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
        when(kit.getDisplayName()).thenReturn("Spear");

        partyManager.createParty(leader1);
        partyManager.createParty(leader2);

        // 1. Both parties only have 1 member (the leader) -> Must fail (min 2v2)
        assertFalse(partyManager.challengeParty(leader1, leader2, kit), "Party duel must reject parties with < 2 members");

        // 2. Add 1 member to Party 1 (now size 2), Party 2 still size 1 -> Mismatch & < 2
        Player p1_m1 = createMockPlayer(UUID.randomUUID(), "P1_M1");
        partyManager.getParty(leader1).addMember(p1_m1.getUniqueId());
        assertFalse(partyManager.challengeParty(leader1, leader2, kit), "Must reject when target party has < 2 members");

        // 3. Add 1 member to Party 2 (now both size 2 = 2v2) -> Must succeed!
        Player p2_m1 = createMockPlayer(UUID.randomUUID(), "P2_M1");
        partyManager.getParty(leader2).addMember(p2_m1.getUniqueId());
        assertTrue(partyManager.challengeParty(leader1, leader2, kit), "2v2 party duel challenge must succeed");

        // 4. Party 1 expands to 3, Party 2 remains 2 -> Mismatch (2v2 vs 3v3) must fail
        Player p1_m2 = createMockPlayer(UUID.randomUUID(), "P1_M2");
        partyManager.getParty(leader1).addMember(p1_m2.getUniqueId());
        assertFalse(partyManager.challengeParty(leader1, leader2, kit), "Must reject mismatched team sizes (3 vs 2)");

        // 5. Party 2 also expands to 3 (now both 3v3) -> Must succeed!
        Player p2_m2 = createMockPlayer(UUID.randomUUID(), "P2_M2");
        partyManager.getParty(leader2).addMember(p2_m2.getUniqueId());
        assertTrue(partyManager.challengeParty(leader1, leader2, kit), "3v3 party duel challenge must succeed");

        // 6. Both expand to 4 (now 4v4) -> Must succeed!
        Player p1_m3 = createMockPlayer(UUID.randomUUID(), "P1_M3");
        Player p2_m3 = createMockPlayer(UUID.randomUUID(), "P2_M3");
        partyManager.getParty(leader1).addMember(p1_m3.getUniqueId());
        partyManager.getParty(leader2).addMember(p2_m3.getUniqueId());
        assertTrue(partyManager.challengeParty(leader1, leader2, kit), "4v4 party duel challenge must succeed");

        // 7. Expand to 5 (5v5) -> Must fail (max is 4v4)
        Player p1_m4 = createMockPlayer(UUID.randomUUID(), "P1_M4");
        Player p2_m4 = createMockPlayer(UUID.randomUUID(), "P2_M4");
        partyManager.getParty(leader1).addMember(p1_m4.getUniqueId());
        partyManager.getParty(leader2).addMember(p2_m4.getUniqueId());
        assertFalse(partyManager.challengeParty(leader1, leader2, kit), "Must reject teams with > 4 members (max 4v4)");
    }

    @Test
    @DisplayName("Verify cannot duel own party")
    void testCannotDuelOwnParty() {
        Player leader = createMockPlayer(UUID.randomUUID(), "SoloLeader");
        Kit kit = mock(Kit.class);

        partyManager.createParty(leader);
        partyManager.getParty(leader).addMember(UUID.randomUUID());

        assertFalse(partyManager.challengeParty(leader, leader, kit), "Cannot duel own party");
    }

    @Test
    @DisplayName("Verify party split rejects odd player counts and sizes > 8")
    void testPartySplitValidation() {
        Player leader = createMockPlayer(UUID.randomUUID(), "SplitLeader");
        Kit kit = mock(Kit.class);

        partyManager.createParty(leader);

        // Size 1 -> fail
        assertFalse(partyManager.splitParty(leader, kit));

        // Size 3 -> odd, must fail
        partyManager.getParty(leader).addMember(UUID.randomUUID());
        partyManager.getParty(leader).addMember(UUID.randomUUID());
        assertFalse(partyManager.splitParty(leader, kit), "Odd member count must fail split");

        // Size 10 -> > 8 (max 4v4), must fail
        for (int i = 0; i < 7; i++) {
            partyManager.getParty(leader).addMember(UUID.randomUUID());
        }
        assertEquals(10, partyManager.getParty(leader).getSize());
        assertFalse(partyManager.splitParty(leader, kit), "More than 8 members must fail split");
    }
}
