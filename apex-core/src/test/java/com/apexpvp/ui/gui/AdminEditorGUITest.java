package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.command.ApexCommand;
import com.apexpvp.command.DuelCommand;
import com.apexpvp.ui.gui.admin.AdminArenaListGUI;
import com.apexpvp.ui.gui.admin.AdminEditorGUI;
import com.apexpvp.ui.gui.admin.ArenaDetailEditorGUI;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AdminEditorGUI & Visual Editor Tests")
public class AdminEditorGUITest {

    private ApexPvPPlugin plugin;
    private ArenaManager arenaManager;
    private AdminEditorGUI adminEditorGUI;
    private AdminArenaListGUI adminArenaListGUI;
    private ArenaDetailEditorGUI arenaDetailEditorGUI;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        arenaManager = mock(ArenaManager.class);
        adminEditorGUI = new AdminEditorGUI(plugin);
        adminArenaListGUI = mock(AdminArenaListGUI.class);
        arenaDetailEditorGUI = mock(ArenaDetailEditorGUI.class);

        when(plugin.getArenaManager()).thenReturn(arenaManager);
        when(plugin.getAdminEditorGUI()).thenReturn(adminEditorGUI);
        when(plugin.getAdminArenaListGUI()).thenReturn(adminArenaListGUI);
        when(plugin.getArenaDetailEditorGUI()).thenReturn(arenaDetailEditorGUI);

        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("AdminDev");
        when(player.hasPermission("apexpvp.admin")).thenReturn(true);
        when(player.hasPermission("apexpvp.duel")).thenReturn(true);
    }

    @Test
    @DisplayName("Verify /apex editor opens AdminEditorGUI")
    void testApexEditorCommand() {
        ApexCommand cmd = new ApexCommand(plugin);
        AdminEditorGUI mockGui = mock(AdminEditorGUI.class);
        when(plugin.getAdminEditorGUI()).thenReturn(mockGui);

        boolean result = cmd.onCommand(player, mock(Command.class), "apex", new String[]{"editor"});
        assertTrue(result);
        verify(mockGui).open(player);
    }

    @Test
    @DisplayName("Verify /duel editor routes to AdminEditorGUI for admins")
    void testDuelEditorCommand() {
        DuelCommand cmd = new DuelCommand(plugin);
        AdminEditorGUI mockGui = mock(AdminEditorGUI.class);
        when(plugin.getAdminEditorGUI()).thenReturn(mockGui);

        boolean result = cmd.onCommand(player, mock(Command.class), "duel", new String[]{"editor"});
        assertTrue(result);
        verify(mockGui).open(player);
    }

    @Test
    @DisplayName("Verify clicking Arenas button in AdminEditorGUI opens AdminArenaListGUI")
    void testClickArenasButton() {
        AdminEditorGUI.AdminEditorHolder holder = new AdminEditorGUI.AdminEditorHolder();
        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(holder);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inv);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(11); // Arenas slot

        adminEditorGUI.onInventoryClick(event);
        verify(event, atLeastOnce()).setCancelled(true);
        verify(adminArenaListGUI).open(player);
    }

    @Test
    @DisplayName("Verify ArenaTemplate cloning and functional toggle mutations")
    void testArenaTemplateMutations() {
        ArenaBounds bounds = new ArenaBounds(-20, 0, -20, 20, 40, 20);
        ArenaTemplate.SpawnPoint sa = new ArenaTemplate.SpawnPoint(-10, 1, 0, -90, 0);
        ArenaTemplate.SpawnPoint sb = new ArenaTemplate.SpawnPoint(10, 1, 0, 90, 0);
        ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0, 15, 0, 0, 45);
        ArenaTemplate.SpawnPoint ffa = new ArenaTemplate.SpawnPoint(0, 1, 0, 0, 0);

        ArenaTemplate template = new ArenaTemplate(
                1, "colosseum", "Colosseum", bounds, sa, sb, spec, ffa,
                false, false, 0, 40, "colosseum.schem",
                true, true, true, List.of("nodebuff", "gapple")
        );

        assertTrue(template.isEnabled());
        assertTrue(template.isRegeneration());
        assertTrue(template.isTeleportToLobby());
        assertTrue(template.getAllowedModes().contains("nodebuff"));

        // Toggle enabled
        ArenaTemplate disabled = template.withEnabled(false);
        assertFalse(disabled.isEnabled());
        assertTrue(disabled.isRegeneration());

        // Toggle regeneration
        ArenaTemplate noRegen = template.withRegeneration(false);
        assertFalse(noRegen.isRegeneration());

        // Toggle teleportToLobby
        ArenaTemplate noTp = template.withTeleportToLobby(false);
        assertFalse(noTp.isTeleportToLobby());

        // Clone template with new ID and display name
        ArenaTemplate cloned = template.withClonedId("colosseum_copy", "Colosseum (Copy)", 2);
        assertEquals("colosseum_copy", cloned.getId());
        assertEquals("Colosseum (Copy)", cloned.getDisplayName());
        assertEquals(2, cloned.getTemplateId());
        assertEquals(template.getRelativeBounds(), cloned.getRelativeBounds());
    }
}
