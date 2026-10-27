package com.apexpvp.party;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyTest {

    private Player createMockPlayer(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getName" -> name;
                case "equals" -> proxy == args[0];
                case "hashCode" -> uuid.hashCode();
                default -> null;
            }
        );
    }

    @Test
    @DisplayName("Verify party roster management, capacity limit, and leader reassignment")
    void testPartyOperations() {
        UUID leaderId = UUID.randomUUID();
        Player leader = createMockPlayer(leaderId, "LeaderSteve");

        Party party = new Party(leader, 3);
        assertEquals(1, party.getSize());
        assertTrue(party.isLeader(leaderId));
        assertTrue(party.contains(leaderId));

        UUID member1Id = UUID.randomUUID();
        UUID member2Id = UUID.randomUUID();
        UUID member3Id = UUID.randomUUID();

        assertTrue(party.addMember(member1Id));
        assertTrue(party.addMember(member2Id));
        assertEquals(3, party.getSize());

        // Capacity is 3, member 3 must be rejected
        assertFalse(party.addMember(member3Id), "Party must respect max-members limit");

        // Leader reassignment
        party.setLeader(member1Id);
        assertFalse(party.isLeader(leaderId));
        assertTrue(party.isLeader(member1Id));

        // Member removal
        assertTrue(party.removeMember(member2Id));
        assertEquals(2, party.getSize());
        assertFalse(party.contains(member2Id));
    }
}
