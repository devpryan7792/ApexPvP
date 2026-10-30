package com.apexpvp.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitApplyTest {

    @Test
    @DisplayName("Verify kit.apply() sets storage, armor, and offhand without wiping armor")
    void testKitApplyEquipsArmorAndOffhand() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());

        // Prepare test items
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack[] main = new ItemStack[36];
        main[0] = sword;

        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemStack[] armor = new ItemStack[]{boots, leggings, chestplate, helmet};

        ItemStack shield = new ItemStack(Material.SHIELD);

        KitRules rules = new KitRules(false, true, false, false);
        Kit kit = new Kit("testkit", "Test Kit", sword, main, armor, shield, Collections.emptyList(), rules);

        // Track what gets set on inventory
        AtomicReference<ItemStack[]> capturedStorage = new AtomicReference<>();
        AtomicReference<ItemStack[]> capturedArmor = new AtomicReference<>();
        AtomicReference<ItemStack> capturedOffhand = new AtomicReference<>();

        doAnswer(inv -> {
            capturedStorage.set((ItemStack[]) inv.getArgument(0));
            return null;
        }).when(inventory).setStorageContents(any(ItemStack[].class));

        doAnswer(inv -> {
            capturedArmor.set((ItemStack[]) inv.getArgument(0));
            return null;
        }).when(inventory).setArmorContents(any(ItemStack[].class));

        doAnswer(inv -> {
            capturedOffhand.set((ItemStack) inv.getArgument(0));
            return null;
        }).when(inventory).setItemInOffHand(any());

        // Apply the kit
        kit.apply(player, null);

        // 1. Verify setStorageContents was invoked and preserved main contents
        verify(inventory, atLeastOnce()).setStorageContents(any(ItemStack[].class));
        assertNotNull(capturedStorage.get(), "Storage contents must be set");
        assertEquals(36, capturedStorage.get().length, "Storage contents must be size 36");
        assertEquals(Material.DIAMOND_SWORD, capturedStorage.get()[0].getType());

        // 2. Verify setArmorContents was called with all 4 armor pieces
        verify(inventory, atLeastOnce()).setArmorContents(any(ItemStack[].class));
        assertNotNull(capturedArmor.get(), "Armor contents must be set");
        assertEquals(4, capturedArmor.get().length, "Armor contents must be size 4");
        assertEquals(Material.NETHERITE_BOOTS, capturedArmor.get()[0].getType());
        assertEquals(Material.NETHERITE_LEGGINGS, capturedArmor.get()[1].getType());
        assertEquals(Material.NETHERITE_CHESTPLATE, capturedArmor.get()[2].getType());
        assertEquals(Material.NETHERITE_HELMET, capturedArmor.get()[3].getType());

        // 3. Verify offhand was set
        verify(inventory, atLeastOnce()).setItemInOffHand(any());
        assertNotNull(capturedOffhand.get(), "Offhand must be set");
        assertEquals(Material.SHIELD, capturedOffhand.get().getType());

        // 4. Verify player.updateInventory() was called
        verify(player, atLeastOnce()).updateInventory();
    }
}
