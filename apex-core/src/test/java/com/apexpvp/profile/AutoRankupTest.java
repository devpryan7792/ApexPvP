package com.apexpvp.profile;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.command.RankupCommand;
import com.apexpvp.database.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Automatic Rank Promotion Tests")
public class AutoRankupTest {

    private ApexPvPPlugin plugin;
    private DatabaseManager databaseManager;
    private ProfileManager profileManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ApexPvP-Test"));
        databaseManager = mock(DatabaseManager.class);
        profileManager = new ProfileManager(plugin, databaseManager);
        when(plugin.getProfileManager()).thenReturn(profileManager);
    }

    @Test
    @DisplayName("Verify automatic promotion when ELO crosses into next rank tier")
    void testAutoRankupWhenEloExceedsThreshold() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Steve", Rank.COPPER_1, 800, 0.0);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(mock(org.bukkit.Location.class));

        // Advance ELO to 1150 (Iron 1 threshold is 1100)
        profile.modifyElo(350);
        assertEquals(1150, profile.getElo());
        assertEquals(Rank.COPPER_1, profile.getRank()); // Rank not yet updated

        boolean promoted = profileManager.checkAndApplyAutoRankup(profile, player);
        assertTrue(promoted, "Player should be automatically promoted");
        assertEquals(Rank.IRON_1, profile.getRank(), "Profile rank should now be IRON_1");

        // Verify title shown and message sent
        verify(player).showTitle(any(net.kyori.adventure.title.Title.class));
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify no promotion when ELO remains in the same tier")
    void testNoRankupWhenEloInSameTier() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Steve", Rank.COPPER_1, 800, 0.0);
        profile.modifyElo(50); // 850 (still COPPER_1, COPPER_2 requires 900)

        boolean promoted = profileManager.checkAndApplyAutoRankup(profile, null);
        assertFalse(promoted, "Player should not be promoted");
        assertEquals(Rank.COPPER_1, profile.getRank());
    }

    @Test
    @DisplayName("Verify /rankup command informs player of remaining ELO when not yet eligible")
    void testRankupCommandInformsRemainingElo() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Steve", Rank.COPPER_1, 800, 0.0);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("apexpvp.rankup")).thenReturn(true);

        profileManager.registerProfile(profile);

        RankupCommand cmd = new RankupCommand(plugin);
        Command mockCmd = mock(Command.class);

        boolean result = cmd.onCommand(player, mockCmd, "rankup", new String[0]);
        assertTrue(result);
        assertEquals(Rank.COPPER_1, profile.getRank());
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify /rankup triggers auto-promotion if player has pending eligible ELO")
    void testRankupCommandPromotesEligiblePlayer() {
        UUID uuid = UUID.randomUUID();
        // Player has 1450 ELO (Gold 1 threshold is 1400), but currently marked COPPER_1
        PlayerProfile profile = new PlayerProfile(uuid, "Steve", Rank.COPPER_1, 1450, 0.0);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("apexpvp.rankup")).thenReturn(true);
        when(player.getLocation()).thenReturn(mock(org.bukkit.Location.class));

        profileManager.registerProfile(profile);

        RankupCommand cmd = new RankupCommand(plugin);
        Command mockCmd = mock(Command.class);

        boolean result = cmd.onCommand(player, mockCmd, "rankup", new String[0]);
        assertTrue(result);
        assertEquals(Rank.GOLD_1, profile.getRank(), "Player should be promoted to GOLD_1");
    }
}
