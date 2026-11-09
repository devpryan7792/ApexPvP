package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitManager;
import com.apexpvp.party.Party;
import com.apexpvp.party.PartyManager;
import com.apexpvp.queue.QueueManager;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FfaCommand /ffa Subcommands and Tab Completion Tests")
public class FfaCommandTest {

    private ApexPvPPlugin plugin;
    private KitManager kitManager;
    private QueueManager queueManager;
    private PartyManager partyManager;
    private FileConfiguration config;
    private Command command;
    private Player player;
    private FfaCommand ffaCommand;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        kitManager = mock(KitManager.class);
        queueManager = mock(QueueManager.class);
        partyManager = mock(PartyManager.class);
        config = mock(FileConfiguration.class);
        command = mock(Command.class);

        when(plugin.getKitManager()).thenReturn(kitManager);
        when(plugin.getQueueManager()).thenReturn(queueManager);
        when(plugin.getPartyManager()).thenReturn(partyManager);
        when(plugin.getConfig()).thenReturn(config);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestFighter");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("apexpvp.ffa")).thenReturn(true);

        ffaCommand = new FfaCommand(plugin);
    }

    @Test
    @DisplayName("/ffa queue <kit> queues player into FFA queue")
    void testFfaQueueCommand() {
        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
        when(kitManager.getKit("spear")).thenReturn(kit);

        boolean result = ffaCommand.onCommand(player, command, "ffa", new String[]{"queue", "spear"});
        assertTrue(result);
        verify(queueManager, times(1)).queueFfaPlayer(player, kit);
    }

    @Test
    @DisplayName("/ffa leave removes player from active FFA queue")
    void testFfaLeaveCommand() {
        when(queueManager.isInFfaQueue(player.getUniqueId())).thenReturn(true);

        boolean result = ffaCommand.onCommand(player, command, "ffa", new String[]{"leave"});
        assertTrue(result);
        verify(queueManager, times(1)).leaveFfaQueue(player);
    }

    @Test
    @DisplayName("/ffa with no args never starts a party FFA match")
    void testFfaNoArgsDoesNotAutoStart() {
        Party party = mock(Party.class);
        Kit kit = mock(Kit.class);
        when(party.isLeader(player.getUniqueId())).thenReturn(true);
        when(party.getSize()).thenReturn(3);
        when(partyManager.getParty(player)).thenReturn(party);
        when(kitManager.getAllKits()).thenReturn(List.of(kit));

        boolean result = ffaCommand.onCommand(player, command, "ffa", new String[0]);

        assertTrue(result);
        verify(partyManager, never()).startPartyFfa(any(), any());
    }

    @Test
    @DisplayName("/ffa <kit> queues instead of starting a party FFA match")
    void testFfaDirectKitQueues() {
        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("spear");
        when(kitManager.getKit("spear")).thenReturn(kit);

        Party party = mock(Party.class);
        when(party.isLeader(player.getUniqueId())).thenReturn(true);
        when(party.getSize()).thenReturn(3);
        when(partyManager.getParty(player)).thenReturn(party);

        boolean result = ffaCommand.onCommand(player, command, "ffa", new String[]{"spear"});

        assertTrue(result);
        verify(queueManager).queueFfaPlayer(player, kit);
        verify(partyManager, never()).startPartyFfa(any(), any());
    }

    @Test
    @DisplayName("/ffa tab completion suggests subcommands and kit IDs")
    void testFfaTabCompletion() {
        Kit kit1 = mock(Kit.class);
        when(kit1.getId()).thenReturn("spear");
        Kit kit2 = mock(Kit.class);
        when(kit2.getId()).thenReturn("nodebuff");
        when(kitManager.getAllKits()).thenReturn(List.of(kit1, kit2));

        List<String> completions = ffaCommand.onTabComplete(player, command, "ffa", new String[]{""});
        assertNotNull(completions);
        assertTrue(completions.contains("queue"));
        assertTrue(completions.contains("leave"));
        assertTrue(completions.contains("status"));
        assertTrue(completions.contains("spear"));
        assertTrue(completions.contains("nodebuff"));
    }
}
