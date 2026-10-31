package com.apexpvp.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QueueTicketTest {

    @Test
    @DisplayName("Verify bidirectional ELO window matching logic")
    void testQueueMatching() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        QueueTicket ticket1 = new QueueTicket(p1, "nodebuff", 1000, 50);
        QueueTicket ticket2 = new QueueTicket(p2, "nodebuff", 1040, 50);
        QueueTicket ticket3 = new QueueTicket(p3, "nodebuff", 1120, 50);

        // 1000 vs 1040: delta is 40 <= 50 -> Match!
        assertTrue(ticket1.matches(ticket2));
        assertTrue(ticket2.matches(ticket1));

        // 1000 vs 1120: delta is 120 > 50 -> No match initially
        assertFalse(ticket1.matches(ticket3));
        assertFalse(ticket3.matches(ticket1));

        // Ticket 1 expands by 100 -> search range = 150
        ticket1.expandRange(100);
        assertEquals(150, ticket1.getCurrentSearchRange());

        // Delta is 120 <= 150 (ticket1), but ticket3 search range is still 50! (120 > 50)
        // Bidirectional safety prevents unfair pairing if opponent didn't expand
        assertFalse(ticket1.matches(ticket3), "Matching must be mutually agreed and bidirectional");

        // Now ticket 3 expands to 150 as well
        ticket3.expandRange(100);
        assertTrue(ticket1.matches(ticket3), "Matching succeeds once both search windows overlap");
    }

    @Test
    @DisplayName("Verify ticket cannot match itself or mismatched kits")
    void testSelfAndKitMismatch() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        QueueTicket ticketA = new QueueTicket(p1, "nodebuff", 1000, 50);
        QueueTicket ticketSelf = new QueueTicket(p1, "nodebuff", 1000, 50);
        QueueTicket ticketDifferentKit = new QueueTicket(p2, "combo", 1000, 50);

        assertFalse(ticketA.matches(ticketSelf), "Ticket must not match identical player UUID");
        assertFalse(ticketA.matches(ticketDifferentKit), "Ticket must not match across different kits");
    }

    @Test
    @DisplayName("Verify concurrent ticket expansions are thread-safe and monotonic")
    void testMonotonicExpansion() throws InterruptedException {
        QueueTicket ticket = new QueueTicket(UUID.randomUUID(), "nodebuff", 1200, 50);
        int threadCount = 10;
        int expansionsPerThread = 20;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < expansionsPerThread; j++) {
                        ticket.expandRange(25);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdown();

        // 50 + (10 * 20 * 25) = 50 + 5000 = 5050
        assertEquals(5050, ticket.getCurrentSearchRange());
    }

    @Test
    @DisplayName("Verify high-volume concurrent ticket pairwise matching")
    void testHighVolumePairwiseMatching() {
        int pairs = 50;
        java.util.List<QueueTicket> tickets = new java.util.ArrayList<>();

        for (int i = 0; i < pairs; i++) {
            int baseElo = 1000 + (i * 200); // Spaces out different pairs
            QueueTicket t1 = new QueueTicket(UUID.randomUUID(), "nodebuff", baseElo, 50);
            QueueTicket t2 = new QueueTicket(UUID.randomUUID(), "nodebuff", baseElo + 10, 50);
            tickets.add(t1);
            tickets.add(t2);
        }

        // Each ticket at index 2*i should match 2*i + 1
        for (int i = 0; i < pairs; i++) {
            QueueTicket t1 = tickets.get(2 * i);
            QueueTicket t2 = tickets.get(2 * i + 1);
            assertTrue(t1.matches(t2));
            assertTrue(t2.matches(t1));

            // Should NOT match adjacent pairs (which differ by ~190 ELO > 50 window)
            if (i > 0) {
                QueueTicket prev = tickets.get(2 * (i - 1));
                assertFalse(t1.matches(prev));
            }
        }
    }

    @Test
    @DisplayName("Verify 2v2, 3v3, and 4v4 team queue matching and team size isolation")
    void testTeamQueueMatching2v2_3v3_4v4() {
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        java.util.List<UUID> party1_2v2 = java.util.List.of(l1, UUID.randomUUID());
        java.util.List<UUID> party2_2v2 = java.util.List.of(l2, UUID.randomUUID());

        QueueTicket ticket2v2_A = new QueueTicket(l1, party1_2v2, 2, "nodebuff", 1000, 50);
        QueueTicket ticket2v2_B = new QueueTicket(l2, party2_2v2, 2, "nodebuff", 1030, 50);

        // 2v2 vs 2v2 matches within ELO window
        assertTrue(ticket2v2_A.matches(ticket2v2_B), "2v2 parties must match when ELO difference is within range");
        assertTrue(ticket2v2_B.matches(ticket2v2_A));

        // 3v3 vs 3v3 matches
        UUID l3 = UUID.randomUUID();
        UUID l4 = UUID.randomUUID();
        java.util.List<UUID> party3_3v3 = java.util.List.of(l3, UUID.randomUUID(), UUID.randomUUID());
        java.util.List<UUID> party4_3v3 = java.util.List.of(l4, UUID.randomUUID(), UUID.randomUUID());

        QueueTicket ticket3v3_A = new QueueTicket(l3, party3_3v3, 3, "nodebuff", 1000, 50);
        QueueTicket ticket3v3_B = new QueueTicket(l4, party4_3v3, 3, "nodebuff", 1020, 50);

        assertTrue(ticket3v3_A.matches(ticket3v3_B), "3v3 parties must match when within range");

        // 4v4 vs 4v4 matches
        UUID l5 = UUID.randomUUID();
        UUID l6 = UUID.randomUUID();
        java.util.List<UUID> party5_4v4 = java.util.List.of(l5, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        java.util.List<UUID> party6_4v4 = java.util.List.of(l6, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        QueueTicket ticket4v4_A = new QueueTicket(l5, party5_4v4, 4, "nodebuff", 1200, 50);
        QueueTicket ticket4v4_B = new QueueTicket(l6, party6_4v4, 4, "nodebuff", 1225, 50);

        assertTrue(ticket4v4_A.matches(ticket4v4_B), "4v4 parties must match when within range");

        // Team Size Isolation: 2v2 must NEVER match 3v3, 4v4, or 1v1 solo
        assertFalse(ticket2v2_A.matches(ticket3v3_A), "2v2 must not match 3v3");
        assertFalse(ticket2v2_A.matches(ticket4v4_A), "2v2 must not match 4v4");
        QueueTicket soloTicket = new QueueTicket(UUID.randomUUID(), "nodebuff", 1000, 50);
        assertFalse(ticket2v2_A.matches(soloTicket), "2v2 must not match 1v1 solo ticket");
    }

    @Test
    @DisplayName("Verify team ticket cannot match if overlapping players or same leader")
    void testOverlappingPartyMembersRejection() {
        UUID leader = UUID.randomUUID();
        UUID sharedMember = UUID.randomUUID();
        java.util.List<UUID> partyA = java.util.List.of(leader, sharedMember);
        java.util.List<UUID> partyB = java.util.List.of(UUID.randomUUID(), sharedMember);

        QueueTicket ticketA = new QueueTicket(leader, partyA, 2, "spear", 1000, 50);
        QueueTicket ticketB = new QueueTicket(partyB.get(0), partyB, 2, "spear", 1000, 50);

        assertFalse(ticketA.matches(ticketB), "Must reject matching if any party members overlap");
    }
}
