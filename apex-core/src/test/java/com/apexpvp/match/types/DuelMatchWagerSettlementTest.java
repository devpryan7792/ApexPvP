package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DuelMatch wager settlement")
class DuelMatchWagerSettlementTest {

    private ApexPvPPlugin plugin;
    private Server server;
    private ArenaInstance arena;
    private Kit kit;
    private Player player1;
    private Player player2;
    private PlayerInventory inventory1;
    private PlayerInventory inventory2;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        server = mock(Server.class);
        arena = mock(ArenaInstance.class);
        kit = mock(Kit.class);

        when(plugin.getServer()).thenReturn(server);
        when(kit.getId()).thenReturn("nodebuff");

        player1 = mock(Player.class);
        player2 = mock(Player.class);
        inventory1 = mock(PlayerInventory.class);
        inventory2 = mock(PlayerInventory.class);

        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        when(player1.getUniqueId()).thenReturn(uuid1);
        when(player2.getUniqueId()).thenReturn(uuid2);
        when(player1.getName()).thenReturn("PlayerOne");
        when(player2.getName()).thenReturn("PlayerTwo");
        when(player1.getInventory()).thenReturn(inventory1);
        when(player2.getInventory()).thenReturn(inventory2);
        when(player1.isOnline()).thenReturn(true);
        when(player2.isOnline()).thenReturn(true);

        when(server.getPlayer(uuid1)).thenReturn(player1);
        when(server.getPlayer(uuid2)).thenReturn(player2);

        when(inventory1.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(inventory2.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
    }

    @Test
    @DisplayName("Paying the pot then cleaning up must not refund it a second time")
    void settleThenCleanupDoesNotDuplicateItems() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, 8);

        DuelMatch match = new DuelMatch(
                plugin, arena, kit, player1, player2, 0.0,
                List.of(sword), List.of(apple), false);

        match.settleWagerPot(player1, "PlayerOne");

        // The winner claims the whole pot: both the sword and the apple.
        verify(inventory1, times(2)).addItem(any(ItemStack.class));
        verify(inventory2, never()).addItem(any(ItemStack.class));

        // cleanup()/refundStakes() runs after settlement on every normal match.
        match.refundStakes();
        match.refundStakes();

        verify(inventory1, times(2)).addItem(any(ItemStack.class));
        verify(inventory2, never()).addItem(any(ItemStack.class));
    }

    @Test
    @DisplayName("Aborting refunds each side's items to their original owner, once")
    void abortRefundIsOwnershipAwareAndIdempotent() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, 8);

        DuelMatch match = new DuelMatch(
                plugin, arena, kit, player1, player2, 0.0,
                List.of(sword), List.of(apple), false);

        match.refundStakes();
        match.refundStakes();

        verify(inventory1, times(1)).addItem(any(ItemStack.class));
        verify(inventory2, times(1)).addItem(any(ItemStack.class));
    }

    @Test
    @DisplayName("A zero-stake duel with no items settles nothing")
    void zeroStakeNoItemsIsSafe() {
        DuelMatch match = new DuelMatch(plugin, arena, kit, player1, player2, 0.0);

        match.refundStakes();

        verify(inventory1, never()).addItem(any(ItemStack.class));
        verify(inventory2, never()).addItem(any(ItemStack.class));
        verify(plugin, never()).getEscrowManager();
    }
}
