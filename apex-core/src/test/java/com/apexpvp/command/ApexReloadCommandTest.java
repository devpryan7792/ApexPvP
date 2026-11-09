package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("/apex reload")
class ApexReloadCommandTest {

    @Test
    @DisplayName("Admin reload triggers the full reload pipeline")
    void reloadCallsFullPipeline() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.admin")).thenReturn(true);

        boolean result = new ApexCommand(plugin)
                .onCommand(sender, mock(Command.class), "apex", new String[]{"reload"});

        assertTrue(result);
        verify(plugin).reloadAllConfigs();
    }

    @Test
    @DisplayName("Non-admin reload is denied and does nothing")
    void reloadRequiresAdmin() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.admin")).thenReturn(false);

        boolean result = new ApexCommand(plugin)
                .onCommand(sender, mock(Command.class), "apex", new String[]{"reload"});

        assertTrue(result);
        verify(plugin, never()).reloadAllConfigs();
    }
}
