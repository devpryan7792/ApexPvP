package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Party Free-For-All (FFA) Mode Tests")
public class PartyFfaTest {

    private ApexPvPPlugin plugin;
    private FileConfiguration config;
    private MatchManager matchManager;
    private ArenaManager arenaManager;
    private PartyManager partyManager;
    private Server server;
    private BukkitScheduler scheduler;
    private World world;

    private Player leader;
    private Player member1;
    private Player member2;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        world = mock(World.class);

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        when(server.getScheduler()).thenReturn(scheduler);

        plugin = mock(ApexPvPPlugin.class);
        config = mock(FileConfiguration.class);
        matchManager = mock(MatchManager.class);
        arenaManager = mock(ArenaManager.class);

        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("party.max-members", 16)).thenReturn(16);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getArenaManager()).thenReturn(arenaManager);
        when(plugin.getProfileManager()).thenReturn(mock(com.apexpvp.profile.ProfileManager.class));

        partyManager = new PartyManager(plugin);

        UUID uLeader = UUID.randomUUID();
        UUID uM1 = UUID.randomUUID();
        UUID uM2 = UUID.randomUUID();

        leader = mock(Player.class);
        when(leader.getUniqueId()).thenReturn(uLeader);
        when(leader.getName()).thenReturn("Leader");
        when(leader.isOnline()).thenReturn(true);

        member1 = mock(Player.class);
        when(member1.getUniqueId()).thenReturn(uM1);
        when(member1.getName()).thenReturn("Member1");
        when(member1.isOnline()).thenReturn(true);

        member2 = mock(Player.class);
        when(member2.getUniqueId()).thenReturn(uM2);
        when(member2.getName()).thenReturn("Member2");
        when(member2.isOnline()).thenReturn(true);

        when(server.getPlayer(uLeader)).thenReturn(leader);
        when(server.getPlayer(uM1)).thenReturn(member1);
        when(server.getPlayer(uM2)).thenReturn(member2);

        when(Bukkit.getPlayer(uLeader)).thenReturn(leader);
        when(Bukkit.getPlayer(uM1)).thenReturn(member1);
        when(Bukkit.getPlayer(uM2)).thenReturn(member2);

        setupPlayerVitals(leader);
        setupPlayerVitals(member1);
        setupPlayerVitals(member2);
    }

    private void setupPlayerVitals(Player player) {
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        when(inv.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getActivePotionEffects()).thenReturn(java.util.Collections.emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    @Test
    @DisplayName("Party FFA Fails When Party Size is Less Than 2")
    void testPartyFfaMinimumSize() {
        partyManager.createParty(leader);
        Kit kit = mock(Kit.class);

        boolean started = partyManager.startPartyFfa(leader, kit);
        assertFalse(started, "Party FFA must fail when only 1 member is in the party");
    }

    @Test
    @DisplayName("Party FFA Succeeds With Odd Number of Players (3 players)")
    void testPartyFfaOddPlayerCount() {
        partyManager.createParty(leader);
        Party party = partyManager.getParty(leader);
        assertNotNull(party);
        party.addMember(member1.getUniqueId());
        party.addMember(member2.getUniqueId());

        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");

        ArenaBounds bounds = new ArenaBounds(0, 50, 0, 100, 100, 100);
        ArenaTemplate template = new ArenaTemplate(1, "colosseum", "Colosseum", bounds,
                new ArenaTemplate.SpawnPoint(20, 64, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(80, 64, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(50, 80, 50, 0, 0),
                new ArenaTemplate.SpawnPoint(50, 64, 50, 0, 0),
                true, true, 50, 90, null, true, true, false, List.of("all"));

        when(arenaManager.getAllTemplates()).thenReturn(List.of(template));

        ArenaInstance arenaInstance = new ArenaInstance(template, 0, world, 64, false);
        when(arenaManager.getAvailableInstance(template)).thenReturn(arenaInstance);

        boolean started = partyManager.startPartyFfa(leader, kit);
        assertTrue(started, "Party FFA must allow 3-player (odd count) deathmatches");
        verify(matchManager, times(1)).registerMatch(any());
    }
}
