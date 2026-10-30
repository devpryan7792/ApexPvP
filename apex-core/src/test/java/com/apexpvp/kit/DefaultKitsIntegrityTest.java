package com.apexpvp.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DefaultKitsIntegrityTest {

    @Test
    @DisplayName("Verify all kits in kits.yml parse cleanly into non-null ItemStacks")
    void testAllKitsParseValidItems() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream, "kits.yml must be present in resources");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection kitsSec = config.getConfigurationSection("kits");
        assertNotNull(kitsSec);

        String[] expectedKits = {"nodebuff", "gapple", "combo", "sumo", "builduhc", "debuff", "custom", "spear"};
        for (String kitId : expectedKits) {
            assertTrue(kitsSec.contains(kitId), "Kit " + kitId + " must exist in kits.yml");
            ConfigurationSection kSec = kitsSec.getConfigurationSection(kitId);
            assertNotNull(kSec);

            // Parse armor
            String helmetRaw = kSec.getString("armor.helmet");
            String chestRaw = kSec.getString("armor.chestplate");
            String legsRaw = kSec.getString("armor.leggings");
            String bootsRaw = kSec.getString("armor.boots");

            if (!kitId.equals("sumo")) {
                assertNotNull(KitManager.parseItem(helmetRaw), kitId + " helmet must parse");
                assertNotNull(KitManager.parseItem(chestRaw), kitId + " chestplate must parse");
                assertNotNull(KitManager.parseItem(legsRaw), kitId + " leggings must parse");
                assertNotNull(KitManager.parseItem(bootsRaw), kitId + " boots must parse");
            }

            // Parse main items
            ConfigurationSection mainSec = kSec.getConfigurationSection("main-items");
            if (mainSec != null) {
                for (String slotKey : mainSec.getKeys(false)) {
                    String raw = mainSec.getString(slotKey);
                    ItemStack parsed = KitManager.parseItem(raw);
                    assertNotNull(parsed, "Kit " + kitId + " slot " + slotKey + " raw '" + raw + "' failed to parse");
                    assertNotEquals(Material.AIR, parsed.getType());
                    assertTrue(parsed.getAmount() >= 1, "Amount must be at least 1");
                }
            }
        }
    }

    @Test
    @DisplayName("Verify NoDebuff kit composition contains 31 healing splash potions and full combat supplies")
    void testNoDebuffKitComposition() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection nodebuff = config.getConfigurationSection("kits.nodebuff");
        assertNotNull(nodebuff);

        assertEquals("POTION", nodebuff.getString("icon"));
        assertFalse(nodebuff.getBoolean("rules.allow-build"));
        assertTrue(nodebuff.getBoolean("rules.allow-hunger"));

        ConfigurationSection main = nodebuff.getConfigurationSection("main-items");
        assertNotNull(main);

        // Verify slot 0 is sword
        ItemStack sword = KitManager.parseItem(main.getString("0"));
        assertNotNull(sword);
        assertEquals(Material.DIAMOND_SWORD, sword.getType());

        // Verify slot 1 is pearls
        ItemStack pearls = KitManager.parseItem(main.getString("1"));
        assertNotNull(pearls);
        assertEquals(Material.ENDER_PEARL, pearls.getType());
        assertEquals(16, pearls.getAmount());

        // Verify slot 8 is food
        ItemStack beef = KitManager.parseItem(main.getString("8"));
        assertNotNull(beef);
        assertEquals(Material.COOKED_BEEF, beef.getType());
        assertEquals(64, beef.getAmount());

        // Count splash healing potions
        int splashPotCount = 0;
        for (String slot : main.getKeys(false)) {
            String raw = main.getString(slot);
            if (raw != null && raw.contains("SPLASH_POTION") && raw.contains("STRONG_HEALING")) {
                splashPotCount++;
            }
        }
        assertEquals(31, splashPotCount, "NoDebuff kit must contain exactly 31 Splash Potions of Healing II");
    }

    @Test
    @DisplayName("Verify BuildUHC kit composition contains water, lava, bow, apples, and blocks")
    void testBuildUHCComposition() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection builduhc = config.getConfigurationSection("kits.builduhc");
        assertNotNull(builduhc);

        assertTrue(builduhc.getBoolean("rules.allow-build"));
        ConfigurationSection main = builduhc.getConfigurationSection("main-items");
        assertNotNull(main);

        ItemStack sword = KitManager.parseItem(main.getString("0"));
        assertNotNull(sword);
        assertEquals(Material.DIAMOND_SWORD, sword.getType());

        ItemStack bow = KitManager.parseItem(main.getString("1"));
        assertNotNull(bow);
        assertEquals(Material.BOW, bow.getType());

        ItemStack cobble = KitManager.parseItem(main.getString("2"));
        assertNotNull(cobble);
        assertEquals(Material.COBBLESTONE, cobble.getType());
        assertEquals(64, cobble.getAmount());

        ItemStack water = KitManager.parseItem(main.getString("3"));
        assertNotNull(water);
        assertEquals(Material.WATER_BUCKET, water.getType());

        ItemStack lava = KitManager.parseItem(main.getString("4"));
        assertNotNull(lava);
        assertEquals(Material.LAVA_BUCKET, lava.getType());

        ItemStack gapples = KitManager.parseItem(main.getString("5"));
        assertNotNull(gapples);
        assertEquals(Material.GOLDEN_APPLE, gapples.getType());
        assertEquals(6, gapples.getAmount());
    }

    @Test
    @DisplayName("Verify Debuff kit contains slowness and poison potions")
    void testDebuffKitComposition() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection debuff = config.getConfigurationSection("kits.debuff");
        assertNotNull(debuff);

        ConfigurationSection main = debuff.getConfigurationSection("main-items");
        assertNotNull(main);

        String slot2 = main.getString("2");
        assertNotNull(slot2);
        assertTrue(slot2.contains("SPLASH_POTION") && slot2.contains("STRONG_SLOWNESS"));

        String slot3 = main.getString("3");
        assertNotNull(slot3);
        assertTrue(slot3.contains("SPLASH_POTION") && slot3.contains("STRONG_POISON"));
    }

    @Test
    @DisplayName("Verify Spear kit composition contains spear, 3 gapples, pearls, totem, harming and healing potions")
    void testSpearKitComposition() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("kits.yml");
        assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection spear = config.getConfigurationSection("kits.spear");
        assertNotNull(spear);

        assertEquals("NETHERITE_SPEAR", spear.getString("icon"));
        assertEquals("TOTEM_OF_UNDYING", spear.getString("offhand"));

        // Verify armor
        ItemStack helmet = KitManager.parseItem(spear.getString("armor.helmet"));
        ItemStack chest = KitManager.parseItem(spear.getString("armor.chestplate"));
        ItemStack legs = KitManager.parseItem(spear.getString("armor.leggings"));
        ItemStack boots = KitManager.parseItem(spear.getString("armor.boots"));
        assertNotNull(helmet);
        assertNotNull(chest);
        assertNotNull(legs);
        assertNotNull(boots);
        assertEquals(Material.NETHERITE_HELMET, helmet.getType());
        assertEquals(Material.NETHERITE_CHESTPLATE, chest.getType());
        assertEquals(Material.NETHERITE_LEGGINGS, legs.getType());
        assertEquals(Material.NETHERITE_BOOTS, boots.getType());

        ConfigurationSection main = spear.getConfigurationSection("main-items");
        assertNotNull(main);

        // Slot 0: Netherite Spear
        ItemStack spearItem = KitManager.parseItem(main.getString("0"));
        assertNotNull(spearItem);
        assertEquals(Material.NETHERITE_SPEAR, spearItem.getType());

        // Slot 1: Exactly 3 non-enchanted golden apples
        ItemStack gapples = KitManager.parseItem(main.getString("1"));
        assertNotNull(gapples);
        assertEquals(Material.GOLDEN_APPLE, gapples.getType());
        assertEquals(3, gapples.getAmount());

        // Slot 2: Ender pearls
        ItemStack pearls = KitManager.parseItem(main.getString("2"));
        assertNotNull(pearls);
        assertEquals(Material.ENDER_PEARL, pearls.getType());
        assertEquals(16, pearls.getAmount());

        // Check for harming and healing potions
        boolean hasHarming = false;
        boolean hasHealing = false;
        for (String key : main.getKeys(false)) {
            String val = main.getString(key);
            if (val != null && val.contains("STRONG_HARMING")) hasHarming = true;
            if (val != null && val.contains("STRONG_HEALING")) hasHealing = true;
        }
        assertTrue(hasHarming, "Spear kit must contain Strong Harming splash potions");
        assertTrue(hasHealing, "Spear kit must contain Strong Healing splash potions");
    }
}

