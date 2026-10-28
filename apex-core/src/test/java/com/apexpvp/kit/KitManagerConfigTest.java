package com.apexpvp.kit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class KitManagerConfigTest {

    @Test
    @DisplayName("Verify default kits.yml contains all competitive kits and correct ruleset flags")
    void testDefaultKitsConfig() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream, "kits.yml must exist in resources");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection section = config.getConfigurationSection("kits");
        assertNotNull(section, "kits section must exist");

        assertTrue(section.contains("nodebuff"), "NoDebuff kit must exist");
        assertTrue(section.contains("gapple"), "Gapple kit must exist");
        assertTrue(section.contains("combo"), "Combo kit must exist");
        assertTrue(section.contains("sumo"), "Sumo kit must exist");
        assertTrue(section.contains("builduhc"), "BuildUHC kit must exist");
        assertTrue(section.contains("debuff"), "Debuff kit must exist");
        assertTrue(section.contains("custom"), "Custom kit must exist");

        // Verify specific kit rules
        assertTrue(section.getBoolean("combo.rules.combo-mode"), "Combo kit must have combo-mode enabled");
        assertFalse(section.getBoolean("combo.rules.sumo-mode"));

        assertTrue(section.getBoolean("sumo.rules.sumo-mode"), "Sumo kit must have sumo-mode enabled");
        assertFalse(section.getBoolean("sumo.rules.allow-hunger"));

        assertTrue(section.getBoolean("builduhc.rules.allow-build"), "BuildUHC must allow building");
        assertFalse(section.getBoolean("nodebuff.rules.allow-build"));
    }

    @Test
    @DisplayName("Verify kit serialization and round-trip deserialization in YAML")
    void testKitYamlRoundTrip() {
        YamlConfiguration config = new YamlConfiguration();
        String path = "kits.custom_pvp";

        config.set(path + ".display-name", "<red>Custom PvP</red>");
        config.set(path + ".icon", "NETHERITE_SWORD");
        config.set(path + ".rules.allow-build", true);
        config.set(path + ".rules.allow-hunger", true);
        config.set(path + ".rules.combo-mode", false);
        config.set(path + ".rules.sumo-mode", false);
        config.set(path + ".armor.helmet", "NETHERITE_HELMET");
        config.set(path + ".armor.chestplate", "NETHERITE_CHESTPLATE");
        config.set(path + ".armor.leggings", "NETHERITE_LEGGINGS");
        config.set(path + ".armor.boots", "NETHERITE_BOOTS");
        config.set(path + ".offhand", "TOTEM_OF_UNDYING:1");
        config.set(path + ".main-items.0", "NETHERITE_SWORD");
        config.set(path + ".main-items.1", "GOLDEN_APPLE:16");

        String savedYaml = config.saveToString();

        // Reload from string
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new StringReader(savedYaml));
        ConfigurationSection kSec = loaded.getConfigurationSection("kits.custom_pvp");
        assertNotNull(kSec);

        assertEquals("<red>Custom PvP</red>", kSec.getString("display-name"));
        assertEquals("NETHERITE_SWORD", kSec.getString("icon"));
        assertTrue(kSec.getBoolean("rules.allow-build"));
        assertEquals("NETHERITE_HELMET", kSec.getString("armor.helmet"));
        assertEquals("TOTEM_OF_UNDYING:1", kSec.getString("offhand"));
        assertEquals("GOLDEN_APPLE:16", kSec.getString("main-items.1"));
    }
}
