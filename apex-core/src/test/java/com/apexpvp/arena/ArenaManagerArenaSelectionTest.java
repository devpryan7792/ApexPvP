package com.apexpvp.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Arena selection and legacy prebuilt handling")
class ArenaManagerArenaSelectionTest {

    private ArenaTemplate template(String id, boolean enabled, boolean kitAllowed) {
        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getId()).thenReturn(id);
        when(template.isEnabled()).thenReturn(enabled);
        when(template.isModeAllowed("nodebuff")).thenReturn(kitAllowed);
        return template;
    }

    @Test
    @DisplayName("Removed pre-made arena ids are recognised as legacy")
    void testLegacyPrebuiltIds() {
        assertTrue(ArenaManager.isLegacyPrebuilt("celestial"));
        assertTrue(ArenaManager.isLegacyPrebuilt("GLACIEM"));
        assertTrue(ArenaManager.isLegacyPrebuilt("Spiritual"));
        assertFalse(ArenaManager.isLegacyPrebuilt("colosseum"));
        assertFalse(ArenaManager.isLegacyPrebuilt("temple"));
    }

    @Test
    @DisplayName("Only enabled and kit-compatible templates are eligible")
    void testEligibleFiltering() {
        ArenaTemplate colosseum = template("colosseum", true, true);
        ArenaTemplate temple = template("temple", true, true);
        ArenaTemplate disabled = template("disabled", false, true);
        ArenaTemplate wrongKit = template("sumo", true, false);

        List<ArenaTemplate> eligible = ArenaManager.eligibleTemplatesForKit(
                List.of(colosseum, temple, disabled, wrongKit), "nodebuff");

        assertEquals(2, eligible.size());
        assertTrue(eligible.contains(colosseum));
        assertTrue(eligible.contains(temple));
    }

    @Test
    @DisplayName("No eligible templates yields an empty list")
    void testNoEligibleTemplates() {
        assertTrue(ArenaManager.eligibleTemplatesForKit(
                List.of(template("disabled", false, true)), "nodebuff").isEmpty());
    }
}
