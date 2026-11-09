package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitManager;
import com.apexpvp.kit.KitRules;
import com.apexpvp.ui.gui.admin.AdminKitEditorGUI;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminKitEditorTest {

    private ApexPvPPlugin plugin;
    private KitManager kitManager;
    private AdminKitEditorGUI editorGUI;
    private Server server;
    private Kit kit;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(Server.class);
        Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        plugin = mock(ApexPvPPlugin.class);
        kitManager = mock(KitManager.class);
        when(plugin.getKitManager()).thenReturn(kitManager);

        editorGUI = new AdminKitEditorGUI(plugin);

        kit = new Kit("crystal", "Crystal PvP",
                new ItemStack(Material.END_CRYSTAL),
                new ItemStack[36],
                new ItemStack[4],
                null, Collections.emptyList(),
                new KitRules(true, true, false, false));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    @Test
    @DisplayName("AdminKitEditor holder stores kit and kit icon independently")
    void testHolderInitialization() {
        AdminKitEditorGUI.AdminKitEditorHolder holder = new AdminKitEditorGUI.AdminKitEditorHolder(kit);
        assertEquals(kit, holder.getKit());
        assertEquals(Material.END_CRYSTAL, holder.getKitIcon().getType());

        ItemStack newIcon = new ItemStack(Material.TOTEM_OF_UNDYING);
        holder.setKitIcon(newIcon);
        assertEquals(Material.TOTEM_OF_UNDYING, holder.getKitIcon().getType());
    }

    @Test
    @DisplayName("AdminKitEditor open requires admin permission")
    void testPermissionCheck() {
        Player nonAdmin = mock(Player.class);
        when(nonAdmin.hasPermission("apexpvp.kit.admin")).thenReturn(false);

        editorGUI.open(nonAdmin, kit);
        verify(nonAdmin, never()).openInventory(any(Inventory.class));

        Player admin = mock(Player.class);
        when(admin.hasPermission("apexpvp.kit.admin")).thenReturn(true);
        when(server.createInventory(any(), eq(54), any(net.kyori.adventure.text.Component.class))).thenReturn(mock(Inventory.class));

        editorGUI.open(admin, kit);
        verify(admin, times(1)).openInventory(any(Inventory.class));
    }
}
