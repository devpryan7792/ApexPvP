package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.FfaMatch;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.ProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FfaMatch Deathmatch Engine & Spawning Tests")
public class FfaMatchTest {

    private ApexPvPPlugin plugin;
    private ProfileManager profileManager;
    private FileConfiguration config;
    private Server server;
    private World world;

    private Player p1, p2, p3, p4;
    private UUID u1, u2, u3, u4;

    private ArenaInstance arena;
    private ArenaTemplate template;
    private Kit kit;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(Server.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(server.getScheduler()).thenReturn(scheduler);

        world = mock(World.class);
        when(server.getWorld("apex_arenas")).thenReturn(world);

        plugin = mock(ApexPvPPlugin.class);
        profileManager = mock(ProfileManager.class);
        config = mock(FileConfiguration.class);

        when(plugin.getProfileManager()).thenReturn(profileManager);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(config.getDouble(anyString(), anyDouble())).thenReturn(50.0);

        u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        u4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        p1 = mock(Player.class);
        p2 = mock(Player.class);
        p3 = mock(Player.class);
        p4 = mock(Player.class);

        when(p1.getUniqueId()).thenReturn(u1);
        when(p1.getName()).thenReturn("Alice");
        when(p1.isOnline()).thenReturn(true);

        when(p2.getUniqueId()).thenReturn(u2);
        when(p2.getName()).thenReturn("Bob");
        when(p2.isOnline()).thenReturn(true);

        when(p3.getUniqueId()).thenReturn(u3);
        when(p3.getName()).thenReturn("Charlie");
        when(p3.isOnline()).thenReturn(true);

        when(p4.getUniqueId()).thenReturn(u4);
        when(p4.getName()).thenReturn("Diana");
        when(p4.isOnline()).thenReturn(true);

        when(server.getPlayer(u1)).thenReturn(p1);
        when(server.getPlayer(u2)).thenReturn(p2);
        when(server.getPlayer(u3)).thenReturn(p3);
        when(server.getPlayer(u4)).thenReturn(p4);

        when(Bukkit.getPlayer(u1)).thenReturn(p1);
        when(Bukkit.getPlayer(u2)).thenReturn(p2);
        when(Bukkit.getPlayer(u3)).thenReturn(p3);
        when(Bukkit.getPlayer(u4)).thenReturn(p4);

        ArenaBounds bounds = new ArenaBounds(0, 50, 0, 100, 100, 100);
        template = new ArenaTemplate(1, "colosseum", "Colosseum", bounds,
                new ArenaTemplate.SpawnPoint(20, 64, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(80, 64, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(50, 80, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(50, 64, 50, 0, 0),
                true, true, 50, 90, null, true, true, false, List.of("all"));

        arena = new ArenaInstance(template, 0, world, 64, false);

        kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
        when(kit.getDisplayName()).thenReturn("Spear PvP");
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    @Test
    @DisplayName("FFA Assigns Distinct Team Index to Each Participant")
    void testParticipantDistinctTeams() {
        FfaMatch match = new FfaMatch(plugin, arena, kit, List.of(p1, p2, p3, p4));

        Set<Integer> teamIndexes = new HashSet<>();
        for (MatchParticipant part : match.getParticipants()) {
            teamIndexes.add(part.getTeamIndex());
        }

        assertEquals(4, teamIndexes.size(), "Each participant in an FFA match must have a distinct team index");
    }

    @Test
    @DisplayName("FFA Match Does Not End When Multiple Combatants Are Alive")
    void testWinConditionMultipleAlive() throws Exception {
        FfaMatch match = new FfaMatch(plugin, arena, kit, List.of(p1, p2, p3, p4));

        // Eliminate p4
        match.getParticipant(u4).setAlive(false);

        Method checkWinMethod = Match.class.getDeclaredMethod("checkWinCondition");
        checkWinMethod.setAccessible(true);
        checkWinMethod.invoke(match);

        assertNotEquals(MatchState.ENDING, match.getState(), "Match should remain active when 3 players are alive");
    }

    @Test
    @DisplayName("FFA Match Ends and Declares Winner When Only One Combatant Survives")
    void testWinConditionSingleSurvivor() throws Exception {
        FfaMatch match = new FfaMatch(plugin, arena, kit, List.of(p1, p2, p3, p4));

        PlayerProfile prof1 = new PlayerProfile(u1, "Alice");
        PlayerProfile prof2 = new PlayerProfile(u2, "Bob");
        PlayerProfile prof3 = new PlayerProfile(u3, "Charlie");
        PlayerProfile prof4 = new PlayerProfile(u4, "Diana");

        when(profileManager.getProfile(u1)).thenReturn(prof1);
        when(profileManager.getProfile(u2)).thenReturn(prof2);
        when(profileManager.getProfile(u3)).thenReturn(prof3);
        when(profileManager.getProfile(u4)).thenReturn(prof4);

        // Eliminate Bob, Charlie, Diana
        match.getParticipant(u2).setAlive(false);
        match.getParticipant(u3).setAlive(false);
        match.getParticipant(u4).setAlive(false);

        Method checkWinMethod = Match.class.getDeclaredMethod("checkWinCondition");
        checkWinMethod.setAccessible(true);
        checkWinMethod.invoke(match);

        assertEquals(MatchState.ENDING, match.getState(), "Match must transition to ENDING when 1 survivor remains");

        // Alice (p1) was winner -> stats incremented
        assertEquals(1, prof1.getOrCreateStats("spear").getWins());
        assertEquals(1, prof1.getCurrentStreak());
        assertEquals(1, prof2.getOrCreateStats("spear").getLosses());
        assertEquals(1, prof3.getOrCreateStats("spear").getLosses());
        assertEquals(1, prof4.getOrCreateStats("spear").getLosses());
    }

    @Test
    @DisplayName("FFA Match Ends in Draw When All Combatants Are Simultaneously Eliminated")
    void testWinConditionSimultaneousElimination() throws Exception {
        FfaMatch match = new FfaMatch(plugin, arena, kit, List.of(p1, p2, p3, p4));

        match.getParticipant(u1).setAlive(false);
        match.getParticipant(u2).setAlive(false);
        match.getParticipant(u3).setAlive(false);
        match.getParticipant(u4).setAlive(false);

        Method checkWinMethod = Match.class.getDeclaredMethod("checkWinCondition");
        checkWinMethod.setAccessible(true);
        checkWinMethod.invoke(match);

        assertEquals(MatchState.ENDING, match.getState(), "Match must transition to ENDING on simultaneous wipe");
    }

    @Test
    @DisplayName("FFA Radial Perimeter Spawning Places Combatants Equidistantly")
    void testRadialPerimeterSpawning() throws Exception {
        FfaMatch match = new FfaMatch(plugin, arena, kit, List.of(p1, p2, p3, p4));

        Method spawnMethod = Match.class.getDeclaredMethod("getSpawnForParticipant", MatchParticipant.class);
        spawnMethod.setAccessible(true);

        List<Location> spawns = new ArrayList<>();
        for (MatchParticipant part : match.getParticipants()) {
            Location loc = (Location) spawnMethod.invoke(match, part);
            assertNotNull(loc);
            spawns.add(loc);
        }

        assertEquals(4, spawns.size());

        // Check center distance is approximately equal for all spawns
        Location center = arena.getFfaSpawn();
        assertNotNull(center);

        double expectedRadius = -1.0;
        for (Location loc : spawns) {
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (expectedRadius < 0) {
                expectedRadius = dist;
            } else {
                assertEquals(expectedRadius, dist, 0.001, "All spawns in FFA must be equidistant from the center");
            }
        }

        // Verify no two spawns overlap
        for (int i = 0; i < spawns.size(); i++) {
            for (int j = i + 1; j < spawns.size(); j++) {
                double distBetween = spawns.get(i).distance(spawns.get(j));
                assertTrue(distBetween > 5.0, "FFA spawns must not collide or be too close (found: " + distBetween + ")");
            }
        }
    }
}
