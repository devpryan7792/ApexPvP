package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.ui.gui.RankedMenuGUI;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("RankedCommand Tests")
public class RankedCommandTest {

    @Test
    @DisplayName("Verify /ranked opens RankedMenuGUI for player")
    void testRankedCommandOpensGUI() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        RankedMenuGUI menuGUI = mock(RankedMenuGUI.class);
        when(plugin.getRankedMenuGUI()).thenReturn(menuGUI);

        RankedCommand cmd = new RankedCommand(plugin);
        Player player = mock(Player.class);
        Command command = mock(Command.class);

        boolean result = cmd.onCommand(player, command, "ranked", new String[0]);
        assertTrue(result);
        verify(menuGUI).open(player);
    }
}
