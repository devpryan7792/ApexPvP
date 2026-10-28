package com.apexpvp.kit;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KitItemParserTest {

    @Test
    @DisplayName("Verify PotionType resolver maps competitive potion names and aliases accurately")
    void testResolvePotionTypes() {
        assertEquals(PotionType.STRONG_HEALING, KitManager.resolvePotionType("STRONG_HEALING"));
        assertEquals(PotionType.STRONG_HEALING, KitManager.resolvePotionType("HEAL_2"));
        assertEquals(PotionType.STRONG_HEALING, KitManager.resolvePotionType("INSTANT_HEAL_2"));

        assertEquals(PotionType.STRONG_SWIFTNESS, KitManager.resolvePotionType("STRONG_SWIFTNESS"));
        assertEquals(PotionType.STRONG_SWIFTNESS, KitManager.resolvePotionType("SPEED_2"));

        assertEquals(PotionType.LONG_FIRE_RESISTANCE, KitManager.resolvePotionType("LONG_FIRE_RESISTANCE"));
        assertEquals(PotionType.LONG_FIRE_RESISTANCE, KitManager.resolvePotionType("FIRE_RES_8"));

        assertEquals(PotionType.STRONG_STRENGTH, KitManager.resolvePotionType("STRONG_STRENGTH"));
        assertEquals(PotionType.STRONG_STRENGTH, KitManager.resolvePotionType("STRENGTH_2"));

        assertEquals(PotionType.STRONG_POISON, KitManager.resolvePotionType("STRONG_POISON"));
        assertEquals(PotionType.STRONG_SLOWNESS, KitManager.resolvePotionType("STRONG_SLOWNESS"));

        assertNull(KitManager.resolvePotionType("NON_EXISTENT_POTION_XYZ"));
    }

    @Test
    @DisplayName("Verify basic item parser parses material and quantity")
    void testParseBasicItem() {
        var pearls = KitManager.parseItem("ENDER_PEARL:16");
        assertNotNull(pearls);
        assertEquals(Material.ENDER_PEARL, pearls.getType());
        assertEquals(16, pearls.getAmount());

        var steak = KitManager.parseItem("COOKED_BEEF:64");
        assertNotNull(steak);
        assertEquals(Material.COOKED_BEEF, steak.getType());
        assertEquals(64, steak.getAmount());

        var sword = KitManager.parseItem("DIAMOND_SWORD");
        assertNotNull(sword);
        assertEquals(Material.DIAMOND_SWORD, sword.getType());
        assertEquals(1, sword.getAmount());

        assertNull(KitManager.parseItem("INVALID_MATERIAL_12345:1"));
        assertNull(KitManager.parseItem(null));
        assertNull(KitManager.parseItem("   "));
    }

    @Test
    @DisplayName("Verify basic item serialization without meta")
    void testSerializeBasicItem() {
        var pearls = KitManager.parseItem("ENDER_PEARL:16");
        assertEquals("ENDER_PEARL:16", KitManager.serializeItem(pearls));

        var sword = KitManager.parseItem("DIAMOND_SWORD");
        assertEquals("DIAMOND_SWORD", KitManager.serializeItem(sword));

        assertNull(KitManager.serializeItem(null));
    }
}
