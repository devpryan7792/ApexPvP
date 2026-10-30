package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.database.DatabaseManager;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.RankedTeamMatch;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RankedTeamMatch Competitive ELO & Progression Tests")
public class RankedTeamMatchTest {

    private ApexPvPPlugin plugin;
    private ProfileManager profileManager;
    private DatabaseManager databaseManager;
    private FileConfiguration config;
    private Server server;

    private Player p1, p2, p3, p4;
    private UUID u1, u2, u3, u4;

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

        when(config.getInt("ranked.k-factor", 32)).thenReturn(32);
        when(config.getBoolean("ranked.dynamic-k-factor", true)).thenReturn(true);
        when(config.getBoolean("ranked.economy.require-rankup-fee", false)).thenReturn(false);

        u1 = UUID.randomUUID();
        u2 = UUID.randomUUID();
        u3 = UUID.randomUUID();
        u4 = UUID.randomUUID();

        p1 = mock(Player.class);
        p2 = mock(Player.class);
        p3 = mock(Player.class);
        p4 = mock(Player.class);

        when(p1.getUniqueId()).thenReturn(u1);
        when(p2.getUniqueId()).thenReturn(u2);
        when(p3.getUniqueId()).thenReturn(u3);
        when(p4.getUniqueId()).thenReturn(u4);

        when(p1.getName()).thenReturn("Alpha1");
        when(p2.getName()).thenReturn("Alpha2");
        when(p3.getName()).thenReturn("Bravo1");
        when(p4.getName()).thenReturn("Bravo2");

        when(server.getPlayer(u1)).thenReturn(p1);
        when(server.getPlayer(u2)).thenReturn(p2);
        when(server.getPlayer(u3)).thenReturn(p3);
        when(server.getPlayer(u4)).thenReturn(p4);

        arena = mock(ArenaInstance.class);
        template = mock(ArenaTemplate.class);
        when(template.getId()).thenReturn("colosseum");
        when(arena.getTemplate()).thenReturn(template);

        kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
    }

    @AfterEach
    void tearDown() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
    }

    private void invokeProcessMatchResults(RankedTeamMatch match, int winningTeamIndex) throws Exception {
        Method method = RankedTeamMatch.class.getDeclaredMethod("processMatchResults", int.class);
        method.setAccessible(true);
        method.invoke(match, winningTeamIndex);
    }

    @Test
    @DisplayName("Verify team 0 victory increases team 0 ELO and decreases team 1 ELO")
    void testRankedTeamMatchTeam0Wins() throws Exception {
        PlayerProfile prof1 = new PlayerProfile(u1, "Alpha1", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile prof2 = new PlayerProfile(u2, "Alpha2", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile prof3 = new PlayerProfile(u3, "Bravo1", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile prof4 = new PlayerProfile(u4, "Bravo2", Rank.COPPER_1, 800, 100.0, 0, 0);

        when(profileManager.getProfile(u1)).thenReturn(prof1);
        when(profileManager.getProfile(u2)).thenReturn(prof2);
        when(profileManager.getProfile(u3)).thenReturn(prof3);
        when(profileManager.getProfile(u4)).thenReturn(prof4);

        RankedTeamMatch match = new RankedTeamMatch(
                plugin, arena, kit,
                List.of(p1, p2), List.of(p3, p4),
                "Team Alpha", "Team Bravo"
        );

        invokeProcessMatchResults(match, 0); // Team 0 (Alpha) wins

        assertTrue(prof1.getElo() > 800, "Alpha1 ELO should increase");
        assertTrue(prof2.getElo() > 800, "Alpha2 ELO should increase");
        assertTrue(prof3.getElo() < 800, "Bravo1 ELO should decrease");
        assertTrue(prof4.getElo() < 800, "Bravo2 ELO should decrease");

        assertEquals(1, prof1.getCurrentStreak());
        assertEquals(1, prof2.getCurrentStreak());
        assertEquals(0, prof3.getCurrentStreak());
        assertEquals(0, prof4.getCurrentStreak());

        verify(profileManager).saveProfile(prof1);
        verify(profileManager).saveProfile(prof2);
        verify(profileManager).saveProfile(prof3);
        verify(profileManager).saveProfile(prof4);

        verify(databaseManager).executeUpdateAsync(
                anyString(),
                eq(match.getMatchId().toString()),
                eq("TEAM_RANKED"),
                eq("spear"),
                eq("colosseum"),
                eq(u1.toString()),
                eq(u3.toString()),
                argThat(delta -> (Integer) delta > 0),
                eq(0.0),
                anyInt()
        );
    }

    @Test
    @DisplayName("Verify draw produces no ELO changes in ranked team match")
    void testRankedTeamMatchDraw() throws Exception {
        PlayerProfile prof1 = new PlayerProfile(u1, "Alpha1", Rank.COPPER_1, 800, 100.0, 0, 0);
        PlayerProfile prof3 = new PlayerProfile(u3, "Bravo1", Rank.COPPER_1, 800, 100.0, 0, 0);

        when(profileManager.getProfile(u1)).thenReturn(prof1);
        when(profileManager.getProfile(u3)).thenReturn(prof3);

        RankedTeamMatch match = new RankedTeamMatch(
                plugin, arena, kit,
                List.of(p1), List.of(p3),
                "Team Alpha", "Team Bravo"
        );

        invokeProcessMatchResults(match, -1); // Draw

        assertEquals(800, prof1.getElo());
        assertEquals(800, prof3.getElo());
        verify(databaseManager, never()).executeUpdateAsync(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
