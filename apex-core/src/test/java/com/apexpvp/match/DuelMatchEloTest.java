package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.database.DatabaseManager;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.DuelMatch;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.ProfileManager;
import com.apexpvp.profile.Rank;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DuelMatch ELO Rating & Rank Progression Tests")
public class DuelMatchEloTest {

    private ApexPvPPlugin plugin;
    private ProfileManager profileManager;
    private DatabaseManager databaseManager;
    private FileConfiguration config;
    private Server server;

    private Player winnerPlayer;
    private Player loserPlayer;
    private UUID winnerUuid;
    private UUID loserUuid;

    private ArenaInstance arena;
    private ArenaTemplate template;
    private Kit kit;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(Server.class);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        plugin = mock(ApexPvPPlugin.class);
        profileManager = mock(ProfileManager.class);
        databaseManager = mock(DatabaseManager.class);
        config = mock(FileConfiguration.class);

        when(plugin.getProfileManager()).thenReturn(profileManager);
        when(plugin.getDatabaseManager()).thenReturn(databaseManager);
        when(plugin.getConfig()).thenReturn(config);

        when(config.getBoolean("duels.elo.enabled", true)).thenReturn(true);
        when(config.getInt("ranked.k-factor", 32)).thenReturn(32);
        when(config.getBoolean("ranked.dynamic-k-factor", true)).thenReturn(true);
        when(config.getBoolean("ranked.economy.require-rankup-fee", false)).thenReturn(false);

        winnerUuid = UUID.randomUUID();
        loserUuid = UUID.randomUUID();

        winnerPlayer = mock(Player.class);
        loserPlayer = mock(Player.class);

        when(winnerPlayer.getUniqueId()).thenReturn(winnerUuid);
        when(loserPlayer.getUniqueId()).thenReturn(loserUuid);
        when(winnerPlayer.getName()).thenReturn("WinnerUser");
        when(loserPlayer.getName()).thenReturn("LoserUser");

        when(server.getPlayer(winnerUuid)).thenReturn(winnerPlayer);
        when(server.getPlayer(loserUuid)).thenReturn(loserPlayer);

        arena = mock(ArenaInstance.class);
        template = mock(ArenaTemplate.class);
        when(template.getId()).thenReturn("classic_1v1");
        when(arena.getTemplate()).thenReturn(template);

        kit = mock(Kit.class);
        when(kit.getId()).thenReturn("nodebuff");
    }

    @AfterEach
    void tearDown() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
    }

    private void invokeProcessMatchResults(DuelMatch match, int winningTeamIndex) throws Exception {
        Method method = DuelMatch.class.getDeclaredMethod("processMatchResults", int.class);
        method.setAccessible(true);
        method.invoke(match, winningTeamIndex);
    }

    @Test
    @DisplayName("Verify winner gains ELO and loser loses ELO in 1v1 duel")
    void testDuelWinnerGainsEloAndLoserLosesElo() throws Exception {
        PlayerProfile winnerProfile = new PlayerProfile(winnerUuid, "WinnerUser", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile loserProfile = new PlayerProfile(loserUuid, "LoserUser", Rank.COPPER_1, 800, 100.0, 0, 0);

        when(profileManager.getProfile(winnerUuid)).thenReturn(winnerProfile);
        when(profileManager.getProfile(loserUuid)).thenReturn(loserProfile);

        DuelMatch match = new DuelMatch(plugin, arena, kit, winnerPlayer, loserPlayer, 0.0);
        invokeProcessMatchResults(match, 0);

        assertTrue(winnerProfile.getElo() > 800, "Winner ELO should have increased from 800");
        assertTrue(loserProfile.getElo() < 800, "Loser ELO should have decreased from 800");
        assertEquals(1, winnerProfile.getCurrentStreak(), "Winner streak should be 1");
        assertEquals(0, loserProfile.getCurrentStreak(), "Loser streak should be reset to 0");

        verify(profileManager).saveProfile(winnerProfile);
        verify(profileManager).saveProfile(loserProfile);
        verify(databaseManager).executeUpdateAsync(
                anyString(),
                eq(match.getMatchId().toString()),
                eq("DUEL"),
                eq("nodebuff"),
                eq("classic_1v1"),
                eq(winnerUuid.toString()),
                eq(loserUuid.toString()),
                argThat(delta -> (Integer) delta > 0),
                eq(0.0),
                anyInt()
        );
    }

    @Test
    @DisplayName("Verify automatic rank-up when winner reaches next rank threshold")
    void testDuelRankUpWhenThresholdPassed() throws Exception {
        // COPPER_1 threshold is 800, COPPER_2 threshold is 900
        PlayerProfile winnerProfile = new PlayerProfile(winnerUuid, "WinnerUser", Rank.COPPER_1, 890, 100.0, 0, 0);
        PlayerProfile loserProfile = new PlayerProfile(loserUuid, "LoserUser", Rank.COPPER_1, 890, 100.0, 0, 0);

        when(profileManager.getProfile(winnerUuid)).thenReturn(winnerProfile);
        when(profileManager.getProfile(loserUuid)).thenReturn(loserProfile);

        DuelMatch match = new DuelMatch(plugin, arena, kit, winnerPlayer, loserPlayer, 0.0);
        invokeProcessMatchResults(match, 0);

        assertTrue(winnerProfile.getElo() >= 900, "Winner ELO should reach >= 900");
        assertEquals(Rank.COPPER_2, winnerProfile.getRank(), "Winner rank should advance to COPPER_2");
    }

    @Test
    @DisplayName("Verify ELO is not modified when duels.elo.enabled is false")
    void testDuelDisabledEloConfig() throws Exception {
        when(config.getBoolean("duels.elo.enabled", true)).thenReturn(false);

        PlayerProfile winnerProfile = new PlayerProfile(winnerUuid, "WinnerUser", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile loserProfile = new PlayerProfile(loserUuid, "LoserUser", Rank.COPPER_1, 800, 100.0, 0, 0);

        when(profileManager.getProfile(winnerUuid)).thenReturn(winnerProfile);
        when(profileManager.getProfile(loserUuid)).thenReturn(loserProfile);

        DuelMatch match = new DuelMatch(plugin, arena, kit, winnerPlayer, loserPlayer, 0.0);
        invokeProcessMatchResults(match, 0);

        assertEquals(800, winnerProfile.getElo(), "Winner ELO should remain 800 when disabled");
        assertEquals(800, loserProfile.getElo(), "Loser ELO should remain 800 when disabled");

        verify(databaseManager).executeUpdateAsync(
                anyString(),
                eq(match.getMatchId().toString()),
                eq("DUEL"),
                eq("nodebuff"),
                eq("classic_1v1"),
                eq(winnerUuid.toString()),
                eq(loserUuid.toString()),
                eq(0),
                eq(0.0),
                anyInt()
        );
    }
}
