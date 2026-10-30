package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitManager;
import com.apexpvp.queue.QueueManager;
import com.apexpvp.queue.QueueTicket;
import com.apexpvp.ui.gui.RankedMenuGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RankedCommand Tests")
public class RankedCommandTest {

    private ApexPvPPlugin plugin;
    private RankedMenuGUI menuGUI;
    private KitManager kitManager;
    private QueueManager queueManager;
    private RankedCommand cmd;
    private Player player;
    private Command mockCmd;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        menuGUI = mock(RankedMenuGUI.class);
        kitManager = mock(KitManager.class);
        queueManager = mock(QueueManager.class);

        when(plugin.getRankedMenuGUI()).thenReturn(menuGUI);
        when(plugin.getKitManager()).thenReturn(kitManager);
        when(plugin.getQueueManager()).thenReturn(queueManager);

        cmd = new RankedCommand(plugin);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");
        mockCmd = mock(Command.class);
    }

    @Test
    @DisplayName("Verify /ranked with no args opens RankedMenuGUI")
    void testRankedCommandOpensGUI_noArgs() {
        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[0]);
        assertTrue(result);
        verify(menuGUI).open(player);
    }

    @Test
    @DisplayName("Verify /ranked menu opens RankedMenuGUI")
    void testRankedCommandOpensGUI_menuArg() {
        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"menu"});
        assertTrue(result);
        verify(menuGUI).open(player);
    }

    @Test
    @DisplayName("Verify /ranked queue <kit> queues player with valid kit")
    void testRankedQueueKit() {
        Kit spearKit = mock(Kit.class);
        when(spearKit.getId()).thenReturn("spear");
        when(kitManager.getKit("spear")).thenReturn(spearKit);

        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"queue", "spear"});
        assertTrue(result);
        verify(queueManager).queuePlayer(player, spearKit);
    }

    @Test
    @DisplayName("Verify /ranked join <kit> queues player with valid kit")
    void testRankedJoinKit() {
        Kit nodebuffKit = mock(Kit.class);
        when(nodebuffKit.getId()).thenReturn("nodebuff");
        when(kitManager.getKit("nodebuff")).thenReturn(nodebuffKit);

        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"join", "nodebuff"});
        assertTrue(result);
        verify(queueManager).queuePlayer(player, nodebuffKit);
    }

    @Test
    @DisplayName("Verify /ranked <kit> direct shortcut queues player")
    void testRankedDirectKitName() {
        Kit spearKit = mock(Kit.class);
        when(spearKit.getId()).thenReturn("spear");
        when(kitManager.getKit("spear")).thenReturn(spearKit);

        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"spear"});
        assertTrue(result);
        verify(queueManager).queuePlayer(player, spearKit);
    }

    @Test
    @DisplayName("Verify /ranked leave exits the matchmaking queue")
    void testRankedLeave() {
        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"leave"});
        assertTrue(result);
        verify(queueManager).leaveQueue(player);
    }

    @Test
    @DisplayName("Verify /ranked status reports queue details when active ticket exists")
    void testRankedStatusInQueue() {
        QueueTicket ticket = new QueueTicket(player.getUniqueId(), "spear", 1000, 50);
        when(queueManager.getTicket(player.getUniqueId())).thenReturn(ticket);

        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"status"});
        assertTrue(result);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify /ranked status reports notice when no ticket exists")
    void testRankedStatusNotInQueue() {
        when(queueManager.getTicket(player.getUniqueId())).thenReturn(null);

        boolean result = cmd.onCommand(player, mockCmd, "ranked", new String[]{"status"});
        assertTrue(result);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify /queue <kit> alias delegates directly to matchmaking queue")
    void testLegacyQueueAlias() {
        Kit spearKit = mock(Kit.class);
        when(spearKit.getId()).thenReturn("spear");
        when(kitManager.getKit("spear")).thenReturn(spearKit);

        boolean result = cmd.onCommand(player, mockCmd, "queue", new String[]{"spear"});
        assertTrue(result);
        verify(queueManager).queuePlayer(player, spearKit);
    }

    @Test
    @DisplayName("Verify /leavequeue alias delegates directly to leaveQueue")
    void testLegacyLeaveQueueAlias() {
        boolean result = cmd.onCommand(player, mockCmd, "leavequeue", new String[0]);
        assertTrue(result);
        verify(queueManager).leaveQueue(player);
    }

    @Test
    @DisplayName("Verify non-player sender is rejected")
    void testNonPlayerSender() {
        CommandSender sender = mock(CommandSender.class);
        boolean result = cmd.onCommand(sender, mockCmd, "ranked", new String[0]);
        assertTrue(result);
        verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify tab completion for /ranked subcommands and kits")
    void testTabCompletion() {
        Kit spearKit = mock(Kit.class);
        when(spearKit.getId()).thenReturn("spear");
        when(kitManager.getAllKits()).thenReturn(Collections.singletonList(spearKit));

        List<String> completions = cmd.onTabComplete(player, mockCmd, "ranked", new String[]{""});
        assertTrue(completions != null && completions.contains("menu"));
        assertTrue(completions.contains("queue"));
        assertTrue(completions.contains("leave"));
        assertTrue(completions.contains("status"));
        assertTrue(completions.contains("spear"));

        List<String> queueCompletions = cmd.onTabComplete(player, mockCmd, "ranked", new String[]{"queue", ""});
        assertTrue(queueCompletions != null && queueCompletions.contains("spear"));
    }
}
