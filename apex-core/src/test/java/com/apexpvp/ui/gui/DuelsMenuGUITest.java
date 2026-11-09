package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.command.DuelCommand;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.ProfileManager;
import com.apexpvp.profile.Rank;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DuelsMenuGUI & Duel Privacy Tests")
public class DuelsMenuGUITest {

    private ApexPvPPlugin plugin;
    private ProfileManager profileManager;
    private DuelsMenuGUI gui;
    private Player player;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        profileManager = mock(ProfileManager.class);
        when(plugin.getProfileManager()).thenReturn(profileManager);

        gui = new DuelsMenuGUI(plugin);
        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestHero");
        when(player.getLocation()).thenReturn(mock(org.bukkit.Location.class));
        when(player.hasPermission("apexpvp.duel")).thenReturn(true);

        profile = new PlayerProfile(uuid, "TestHero", Rank.IRON_1, 1150, 250.0);
        when(profileManager.getProfile(uuid)).thenReturn(profile);
    }

    @Test
    @DisplayName("Verify /duel with no args opens DuelsMenuGUI")
    void testDuelCommandOpensDuelsMenuGUI() {
        DuelsMenuGUI mockGui = mock(DuelsMenuGUI.class);
        when(plugin.getDuelsMenuGUI()).thenReturn(mockGui);
        DuelCommand cmd = new DuelCommand(plugin);
        Command mockCmd = mock(Command.class);

        boolean result = cmd.onCommand(player, mockCmd, "duel", new String[0]);
        assertTrue(result);
        verify(mockGui).open(player);
    }

    @Test
    @DisplayName("Verify clicking Privacy Toggle in DuelsMenuGUI mutes/enables duel requests")
    void testTogglePrivacyClick() {
        assertTrue(profile.isAllowDuelRequests(), "Should default to allowing duel requests");

        DuelsMenuGUI.DuelsMenuHolder holder = new DuelsMenuGUI.DuelsMenuHolder();
        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(holder);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getInventory()).thenReturn(inv);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getRawSlot()).thenReturn(22);

        // Click when true -> should become false
        gui.onInventoryClick(clickEvent);
        assertFalse(profile.isAllowDuelRequests(), "Should toggle to false");
        verify(clickEvent, atLeastOnce()).setCancelled(true);

        // Click when false -> should become true
        gui.onInventoryClick(clickEvent);
        assertTrue(profile.isAllowDuelRequests(), "Should toggle back to true");
    }

    @Test
    @DisplayName("Verify /duel toggle command in chat toggles privacy status")
    void testDuelToggleCommand() {
        DuelCommand cmd = new DuelCommand(plugin);
        Command mockCmd = mock(Command.class);

        assertTrue(profile.isAllowDuelRequests());
        cmd.onCommand(player, mockCmd, "duel", new String[]{"toggle"});
        assertFalse(profile.isAllowDuelRequests());

        cmd.onCommand(player, mockCmd, "duel", new String[]{"privacy"});
        assertTrue(profile.isAllowDuelRequests());
    }

    @Test
    @DisplayName("Verify duel challenge is rejected when target player has muted duel requests")
    void testDuelChallengeRejectedWhenTargetMuted() {
        UUID targetUuid = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.isOnline()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("PeacefulPlayer");

        PlayerProfile targetProfile = new PlayerProfile(targetUuid, "PeacefulPlayer");
        targetProfile.setAllowDuelRequests(false); // Muted!
        when(profileManager.getProfile(targetUuid)).thenReturn(targetProfile);

        DuelCommand cmd = new DuelCommand(plugin);
        Command mockCmd = mock(Command.class);

        // Mock Bukkit.getPlayer("PeacefulPlayer")
        try (var mockedBukkit = mockStatic(org.bukkit.Bukkit.class)) {
            mockedBukkit.when(() -> org.bukkit.Bukkit.getPlayer("PeacefulPlayer")).thenReturn(target);

            com.apexpvp.match.MatchManager matchManager = mock(com.apexpvp.match.MatchManager.class);
            when(plugin.getMatchManager()).thenReturn(matchManager);

            boolean result = cmd.onCommand(player, mockCmd, "duel", new String[]{"PeacefulPlayer"});
            assertTrue(result);
            verify(player).sendMessage(argThat((net.kyori.adventure.text.Component c) -> true));
            verify(plugin, never()).getDuelKitGUI();
        }
    }
}
