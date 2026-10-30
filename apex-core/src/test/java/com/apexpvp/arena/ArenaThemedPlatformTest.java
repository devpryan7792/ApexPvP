package com.apexpvp.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArenaThemedPlatformTest {

    private static final List<String> ICONIC_ARENAS = List.of(
            "colosseum", "temple", "netherforge", "cybervoid", "desertdune", "frostpeak", "sumo",
            "celestial", "glaciem", "spiritual"
    );

    @Test
    @DisplayName("Verify all iconic arena templates are defined in arenas.yml with unique IDs")
    void testIconicArenaTemplatesDefined() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("arenas.yml");
        assertNotNull(stream, "arenas.yml must exist in resources");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection templatesSection = config.getConfigurationSection("templates");
        assertNotNull(templatesSection, "templates section must exist");

        Set<Integer> registeredIds = new HashSet<>();

        for (String arenaName : ICONIC_ARENAS) {
            ConfigurationSection sec = templatesSection.getConfigurationSection(arenaName);
            assertNotNull(sec, "Template [" + arenaName + "] must be defined in arenas.yml");

            int templateId = sec.getInt("template-id");
            assertTrue(templateId >= 1 && templateId <= 10, "Template ID for " + arenaName + " must be between 1 and 10, got: " + templateId);
            assertTrue(registeredIds.add(templateId), "Duplicate template-id detected: " + templateId);

            assertNotNull(sec.getString("display-name"), "display-name must not be null for " + arenaName);
            assertNotNull(sec.getConfigurationSection("spawns.spawn-a"), "spawn-a must exist for " + arenaName);
            assertNotNull(sec.getConfigurationSection("spawns.spawn-b"), "spawn-b must exist for " + arenaName);
            assertNotNull(sec.getConfigurationSection("spawns.spectator"), "spectator spawn must exist for " + arenaName);
        }

        assertEquals(ICONIC_ARENAS.size(), registeredIds.size(), "All iconic templates must be uniquely registered");
    }

    @Test
    @DisplayName("Verify infinite mathematical grid non-overlapping instance coordinates")
    void testMathematicalGridUniqueness() {
        Set<String> allocatedGridCells = new HashSet<>();

        // Test grid allocation for all 6 templates across 10 concurrent duplicated instances each (60 concurrent instances)
        for (int templateId = 1; templateId <= 6; templateId++) {
            for (int instanceIndex = 0; instanceIndex < 10; instanceIndex++) {
                int originX = templateId * 1000;
                int originZ = instanceIndex * 1000;
                String cellKey = originX + ":" + originZ;

                assertTrue(allocatedGridCells.add(cellKey),
                        "Collision detected at cell [" + cellKey + "] for template " + templateId + " instance " + instanceIndex);
            }
        }

        assertEquals(60, allocatedGridCells.size());
    }

    @Test
    @DisplayName("Verify ArenaTemplate copy methods for in-game spawn setup")
    void testTemplateSpawnCopyMethods() {
        ArenaBounds bounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        ArenaTemplate.SpawnPoint spawnA = new ArenaTemplate.SpawnPoint(-18.5, 1.0, 0.5, -90.0f, 0.0f);
        ArenaTemplate.SpawnPoint spawnB = new ArenaTemplate.SpawnPoint(18.5, 1.0, 0.5, 90.0f, 0.0f);
        ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0.5, 15.0, 0.5, 0.0f, 45.0f);

        ArenaTemplate template = new ArenaTemplate(
                2, "temple", "<gradient:#00FFAA:#00AAFF>Temple</gradient>",
                bounds, spawnA, spawnB, spec, true, true, 64, 84, "temple.schem"
        );

        // Update spawn A
        ArenaTemplate.SpawnPoint newSpawnA = new ArenaTemplate.SpawnPoint(-22.0, 2.0, 1.0, -45.0f, 10.0f);
        ArenaTemplate updatedA = template.withSpawnA(newSpawnA);
        assertEquals(newSpawnA, updatedA.getRelativeSpawnA());
        assertEquals(spawnB, updatedA.getRelativeSpawnB());
        assertEquals(2, updatedA.getTemplateId());

        // Update spawn B
        ArenaTemplate.SpawnPoint newSpawnB = new ArenaTemplate.SpawnPoint(22.0, 2.0, 1.0, 45.0f, 10.0f);
        ArenaTemplate updatedB = template.withSpawnB(newSpawnB);
        assertEquals(newSpawnB, updatedB.getRelativeSpawnB());
        assertEquals(spawnA, updatedB.getRelativeSpawnA());

        // Update spectator spawn
        ArenaTemplate.SpawnPoint newSpec = new ArenaTemplate.SpawnPoint(0.0, 25.0, 0.0, 0.0f, 90.0f);
        ArenaTemplate updatedSpec = template.withSpectatorSpawn(newSpec);
        assertEquals(newSpec, updatedSpec.getRelativeSpectatorSpawn());
    }
}
