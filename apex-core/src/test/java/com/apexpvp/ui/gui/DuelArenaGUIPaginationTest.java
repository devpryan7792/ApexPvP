package com.apexpvp.ui.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Duel arena GUI pagination")
class DuelArenaGUIPaginationTest {

    @Test
    @DisplayName("Small arena sets use a single page")
    void testSinglePage() {
        assertEquals(1, DuelArenaGUI.paginatedPageCount(0));
        assertEquals(1, DuelArenaGUI.paginatedPageCount(6));
        assertEquals(1, DuelArenaGUI.paginatedPageCount(13));
    }

    @Test
    @DisplayName("First page holds 27 arenas plus the Random option")
    void testFirstPageCapacity() {
        assertEquals(1, DuelArenaGUI.paginatedPageCount(14));
        assertEquals(1, DuelArenaGUI.paginatedPageCount(27));
        assertEquals(2, DuelArenaGUI.paginatedPageCount(28));
    }

    @Test
    @DisplayName("Additional pages hold 28 arenas each")
    void testAdditionalPages() {
        assertEquals(2, DuelArenaGUI.paginatedPageCount(55));
        assertEquals(3, DuelArenaGUI.paginatedPageCount(56));
        assertEquals(3, DuelArenaGUI.paginatedPageCount(57));
    }

    @Test
    @DisplayName("Large imported arena collections are all reachable")
    void testLargeCollection() {
        assertEquals(11, DuelArenaGUI.paginatedPageCount(300));
    }
}
