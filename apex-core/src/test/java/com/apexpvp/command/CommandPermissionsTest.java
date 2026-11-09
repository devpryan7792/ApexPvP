package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Command permission enforcement")
class CommandPermissionsTest {

    @Test
    @DisplayName("A sender with the permission passes")
    void allowWhenPermissionGranted() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.duel")).thenReturn(true);

        assertTrue(CommandPermissions.require(sender, "apexpvp.duel"));
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("A sender without the permission is denied and notified")
    void denyWhenPermissionMissing() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.duel")).thenReturn(false);

        assertFalse(CommandPermissions.require(sender, "apexpvp.duel"));
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("A denied /duel never opens the hub")
    void duelCommandRespectsPermission() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        Player player = mock(Player.class);
        when(player.hasPermission("apexpvp.duel")).thenReturn(false);

        DuelCommand cmd = new DuelCommand(plugin);
        assertTrue(cmd.onCommand(player, mock(Command.class), "duel", new String[0]));

        verify(plugin, never()).getDuelsMenuGUI();
    }

    @Test
    @DisplayName("A denied /queue never touches the queue manager")
    void rankedCommandRespectsPermission() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        Player player = mock(Player.class);
        when(player.hasPermission("apexpvp.queue")).thenReturn(false);

        RankedCommand cmd = new RankedCommand(plugin);
        assertTrue(cmd.onCommand(player, mock(Command.class), "queue", new String[]{"nodebuff"}));

        verify(plugin, never()).getQueueManager();
    }
}
