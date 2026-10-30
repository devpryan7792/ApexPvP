package com.apexpvp.command;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ArenaCommand Administrative Tests")
public class ArenaCommandTest {

    private ApexPvPPlugin plugin;
    private ArenaManager arenaManager;
    private ArenaCommand command;
    private Command mockCmd;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        arenaManager = mock(ArenaManager.class);
        when(plugin.getArenaManager()).thenReturn(arenaManager);

        command = new ArenaCommand(plugin);
        mockCmd = mock(Command.class);
    }

    @Test
    @DisplayName("Verify permission rejection when sender lacks apexpvp.arena.admin")
    void testPermissionDenied() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.arena.admin")).thenReturn(false);

        boolean result = command.onCommand(sender, mockCmd, "arena", new String[]{"list"});
        assertTrue(result);
        verify(sender).sendMessage(argThat((net.kyori.adventure.text.Component c) -> true));
        verify(arenaManager, never()).getAllTemplates();
    }

    @Test
    @DisplayName("Verify /arena list formats templates correctly")
    void testArenaList() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.arena.admin")).thenReturn(true);

        ArenaBounds bounds = new ArenaBounds(-30, 0, -30, 30, 40, 30);
        ArenaTemplate.SpawnPoint sa = new ArenaTemplate.SpawnPoint(-18.5, 1.0, 0.5, -90.0f, 0.0f);
        ArenaTemplate.SpawnPoint sb = new ArenaTemplate.SpawnPoint(18.5, 1.0, 0.5, 90.0f, 0.0f);
        ArenaTemplate.SpawnPoint spec = new ArenaTemplate.SpawnPoint(0.5, 15.0, 0.5, 0.0f, 45.0f);

        ArenaTemplate template = new ArenaTemplate(1, "colosseum", "Colosseum", bounds, sa, sb, spec, true, true, 64, 84, "colosseum.schem");
        when(arenaManager.getAllTemplates()).thenReturn(List.of(template));
        when(arenaManager.getInstances("colosseum")).thenReturn(Collections.emptyList());

        boolean result = command.onCommand(sender, mockCmd, "arena", new String[]{"list"});
        assertTrue(result);
        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Verify /arena tp teleports admin into spectator spawn")
    void testArenaTeleport() {
        Player player = mock(Player.class);
        when(player.hasPermission("apexpvp.arena.admin")).thenReturn(true);

        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getId()).thenReturn("colosseum");
        when(template.getDisplayName()).thenReturn("Colosseum");
        when(arenaManager.getTemplate("colosseum")).thenReturn(template);

        ArenaInstance instance = mock(ArenaInstance.class);
        Location specLoc = new Location(mock(World.class), 1000.5, 79.0, 0.5);
        when(instance.getSpectatorSpawn()).thenReturn(specLoc);
        when(arenaManager.getInstances("colosseum")).thenReturn(List.of(instance));
        when(player.teleportAsync(specLoc)).thenReturn(CompletableFuture.completedFuture(true));

        boolean result = command.onCommand(player, mockCmd, "arena", new String[]{"tp", "colosseum"});
        assertTrue(result);
        verify(player).teleportAsync(specLoc);
    }

    @Test
    @DisplayName("Verify /arena reload triggers template reload")
    void testArenaReload() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.arena.admin")).thenReturn(true);

        boolean result = command.onCommand(sender, mockCmd, "arena", new String[]{"reload"});
        assertTrue(result);
        verify(arenaManager).reloadTemplates();
    }

    @Test
    @DisplayName("Verify tab completion returns subcommands and template IDs")
    void testTabCompletion() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("apexpvp.arena.admin")).thenReturn(true);

        ArenaTemplate template = mock(ArenaTemplate.class);
        when(template.getId()).thenReturn("colosseum");
        when(arenaManager.getAllTemplates()).thenReturn(List.of(template));

        List<String> subCompletions = command.onTabComplete(sender, mockCmd, "arena", new String[]{""});
        assertNotNull(subCompletions);
        assertTrue(subCompletions.contains("list"));
        assertTrue(subCompletions.contains("create"));
        assertTrue(subCompletions.contains("tp"));
        assertTrue(subCompletions.contains("setspawn"));
        assertTrue(subCompletions.contains("reload"));

        List<String> tpCompletions = command.onTabComplete(sender, mockCmd, "arena", new String[]{"tp", ""});
        assertNotNull(tpCompletions);
        assertTrue(tpCompletions.contains("colosseum"));

        List<String> spawnCompletions = command.onTabComplete(sender, mockCmd, "arena", new String[]{"setspawn", "colosseum", ""});
        assertNotNull(spawnCompletions);
        assertTrue(spawnCompletions.contains("a"));
        assertTrue(spawnCompletions.contains("b"));
        assertTrue(spawnCompletions.contains("spec"));
    }

    @Test
    @DisplayName("Verify /arena create registers new template with radius fallback")
    void testArenaCreateRadiusFallback() {
        Player player = mock(Player.class);
        when(player.hasPermission("apexpvp.arena.admin")).thenReturn(true);
        when(arenaManager.getTemplate("neon_city")).thenReturn(null);
        when(arenaManager.createTemplate(eq("neon_city"), eq("Neon City"), any(ArenaBounds.class), any(), any(), any()))
                .thenReturn(true);

        boolean result = command.onCommand(player, mockCmd, "arena", new String[]{"create", "neon_city", "Neon City", "35"});
        assertTrue(result);
        verify(arenaManager).createTemplate(eq("neon_city"), eq("Neon City"), any(ArenaBounds.class), any(), any(), any());
    }

    @Test
    @DisplayName("Verify /arena create rejects if template already exists")
    void testArenaCreateDuplicateRejection() {
        Player player = mock(Player.class);
        when(player.hasPermission("apexpvp.arena.admin")).thenReturn(true);
        ArenaTemplate existing = mock(ArenaTemplate.class);
        when(arenaManager.getTemplate("colosseum")).thenReturn(existing);

        boolean result = command.onCommand(player, mockCmd, "arena", new String[]{"create", "colosseum"});
        assertTrue(result);
        verify(arenaManager, never()).createTemplate(any(), any(), any(), any(), any(), any());
    }
}
