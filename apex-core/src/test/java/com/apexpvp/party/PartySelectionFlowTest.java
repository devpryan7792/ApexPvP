package com.apexpvp.party;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitManager;
import com.apexpvp.kit.KitRules;
import com.apexpvp.match.MatchManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartySelectionFlowTest {

    private ApexPvPPlugin plugin;
    private PartyManager partyManager;
    private ArenaManager arenaManager;
    private MatchManager matchManager;
    private org.bukkit.Server server;
    private Kit kit;
    private ArenaTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(org.bukkit.Server.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);

        java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);

        plugin = mock(ApexPvPPlugin.class);
        arenaManager = mock(ArenaManager.class);
        matchManager = mock(MatchManager.class);
        KitManager kitManager = mock(KitManager.class);
        org.bukkit.configuration.file.FileConfiguration config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(config.getInt("party.max-members", 16)).thenReturn(16);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getArenaManager()).thenReturn(arenaManager);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getKitManager()).thenReturn(kitManager);
        when(plugin.getProfileManager()).thenReturn(mock(com.apexpvp.profile.ProfileManager.class));

        partyManager = new PartyManager(plugin);

        kit = new Kit("nodebuff", "NoDebuff",
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD),
                new org.bukkit.inventory.ItemStack[36],
                new org.bukkit.inventory.ItemStack[4],
                null, Collections.emptyList(),
                new KitRules(false, true, false, false));

        template = new ArenaTemplate(1, "glaciem", "Glaciem Arena",
                new ArenaBounds(-20, 0, -20, 20, 30, 20),
                new ArenaTemplate.SpawnPoint(-10, 64, 0, 0, 0),
                new ArenaTemplate.SpawnPoint(10, 64, 0, 0, 0),
                new ArenaTemplate.SpawnPoint(0, 75, 0, 0, 0),
                new ArenaTemplate.SpawnPoint(0, 64, 0, 0, 0),
                true, true, 50, 90, null, 64, true, true, false, List.of("all"), false, "PACKED_ICE");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    @Test
    @DisplayName("Party Split accepts custom arena template chosen by leader")
    void testPartySplitWithCustomArena() {
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(p1.getUniqueId()).thenReturn(u1);
        when(p2.getUniqueId()).thenReturn(u2);
        when(p1.getName()).thenReturn("Player1");
        when(p2.getName()).thenReturn("Player2");
        when(p1.isOnline()).thenReturn(true);
        when(p2.isOnline()).thenReturn(true);
        when(server.getPlayer(u1)).thenReturn(p1);
        when(server.getPlayer(u2)).thenReturn(p2);

        World world = mock(World.class);
        setupPlayerVitals(p1, world);
        setupPlayerVitals(p2, world);

        ArenaInstance instance = new ArenaInstance(template, 0, world, 64, false);
        when(arenaManager.getAvailableInstance(template)).thenReturn(instance);

        partyManager.createParty(p1);
        Party party = partyManager.getParty(p1);
        assertNotNull(party);
        party.addMember(u2);

        boolean started = partyManager.splitParty(p1, kit, template);
        assertTrue(started, "Party Split should succeed when custom arena is specified");
        verify(matchManager, times(1)).registerMatch(any());
        verify(arenaManager, times(1)).getAvailableInstance(template);
    }

    @Test
    @DisplayName("Party FFA accepts custom arena template chosen by leader")
    void testPartyFfaWithCustomArena() {
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player p3 = mock(Player.class);
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        when(p1.getUniqueId()).thenReturn(u1);
        when(p2.getUniqueId()).thenReturn(u2);
        when(p3.getUniqueId()).thenReturn(u3);
        when(p1.getName()).thenReturn("Player1");
        when(p2.getName()).thenReturn("Player2");
        when(p3.getName()).thenReturn("Player3");
        when(p1.isOnline()).thenReturn(true);
        when(p2.isOnline()).thenReturn(true);
        when(p3.isOnline()).thenReturn(true);
        when(server.getPlayer(u1)).thenReturn(p1);
        when(server.getPlayer(u2)).thenReturn(p2);
        when(server.getPlayer(u3)).thenReturn(p3);

        World world = mock(World.class);
        setupPlayerVitals(p1, world);
        setupPlayerVitals(p2, world);
        setupPlayerVitals(p3, world);

        ArenaInstance instance = new ArenaInstance(template, 0, world, 64, false);
        when(arenaManager.getAvailableInstance(template)).thenReturn(instance);

        partyManager.createParty(p1);
        Party party = partyManager.getParty(p1);
        assertNotNull(party);
        party.addMember(u2);
        party.addMember(u3);

        boolean started = partyManager.startPartyFfa(p1, kit, template);
        assertTrue(started, "Party FFA should succeed when custom arena is specified");
        verify(matchManager, times(1)).registerMatch(any());
        verify(arenaManager, times(1)).getAvailableInstance(template);
    }

    private void setupPlayerVitals(Player player, World world) {
        org.bukkit.inventory.PlayerInventory inv = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        when(inv.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getActivePotionEffects()).thenReturn(java.util.Collections.emptyList());
    }
}
