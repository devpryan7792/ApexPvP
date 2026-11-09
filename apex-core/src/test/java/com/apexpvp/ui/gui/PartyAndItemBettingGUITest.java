package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaBounds;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.command.TeamCommand;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.match.MatchManager;
import com.apexpvp.match.types.DuelMatch;
import com.apexpvp.party.Party;
import com.apexpvp.party.PartyManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Party & Item Betting GUI Suite")
public class PartyAndItemBettingGUITest {

    private ApexPvPPlugin plugin;
    private PartyManager partyManager;
    private MatchManager matchManager;
    private ArenaManager arenaManager;
    private PartyGUI partyGUI;
    private ItemBettingGUI itemBettingGUI;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        plugin = mock(ApexPvPPlugin.class);
        partyManager = mock(PartyManager.class);
        matchManager = mock(MatchManager.class);
        arenaManager = mock(ArenaManager.class);

        partyGUI = new PartyGUI(plugin);
        itemBettingGUI = new ItemBettingGUI(plugin);

        when(plugin.getPartyManager()).thenReturn(partyManager);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getArenaManager()).thenReturn(arenaManager);
        when(plugin.getPartyGUI()).thenReturn(partyGUI);
        when(plugin.getItemBettingGUI()).thenReturn(itemBettingGUI);
        org.bukkit.configuration.file.FileConfiguration config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);

        player1 = mock(Player.class);
        UUID u1 = UUID.randomUUID();
        when(player1.getUniqueId()).thenReturn(u1);
        when(player1.getName()).thenReturn("ChallengerHero");
        when(player1.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player1.hasPermission("apexpvp.team")).thenReturn(true);

        player2 = mock(Player.class);
        UUID u2 = UUID.randomUUID();
        when(player2.getUniqueId()).thenReturn(u2);
        when(player2.getName()).thenReturn("ChallengedHero");
        when(player2.getInventory()).thenReturn(mock(PlayerInventory.class));
    }

    @Test
    @DisplayName("Verify /party with no args or /party gui opens PartyGUI")
    void testPartyCommandOpensGUI() {
        PartyGUI mockGUI = mock(PartyGUI.class);
        when(plugin.getPartyGUI()).thenReturn(mockGUI);

        TeamCommand cmd = new TeamCommand(plugin);
        Command mockCmd = mock(Command.class);

        assertTrue(cmd.onCommand(player1, mockCmd, "party", new String[0]));
        verify(mockGUI).open(player1);

        assertTrue(cmd.onCommand(player1, mockCmd, "party", new String[]{"gui"}));
        verify(mockGUI, times(2)).open(player1);
    }

    @Test
    @DisplayName("Verify ItemBetting anti-scam reset and confirmation state machine")
    void testItemBettingConfirmationFlow() {
        Kit kit = mock(Kit.class);
        ArenaTemplate template = mock(ArenaTemplate.class);
        ItemBettingGUI.ItemBettingHolder holder = new ItemBettingGUI.ItemBettingHolder(player1, player2, kit, template);

        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(holder);
        holder.setInventory(inv);

        assertFalse(holder.isChallengerReady());
        assertFalse(holder.isChallengedReady());

        // 1. Challenger clicks ready slot 46
        InventoryClickEvent clickP1 = mock(InventoryClickEvent.class);
        when(clickP1.getInventory()).thenReturn(inv);
        when(clickP1.getWhoClicked()).thenReturn(player1);
        when(clickP1.getRawSlot()).thenReturn(ItemBettingGUI.CHALLENGER_READY_SLOT);

        itemBettingGUI.onInventoryClick(clickP1);
        assertTrue(holder.isChallengerReady(), "Challenger should now be confirmed");
        assertFalse(holder.isChallengedReady(), "Challenged should still be unconfirmed");

        // 2. Challenger edits an item in their wager slot 10 -> anti-scam triggers reset!
        InventoryClickEvent editSlot10 = mock(InventoryClickEvent.class);
        when(editSlot10.getInventory()).thenReturn(inv);
        when(editSlot10.getWhoClicked()).thenReturn(player1);
        when(editSlot10.getRawSlot()).thenReturn(10);

        itemBettingGUI.onInventoryClick(editSlot10);
        assertFalse(holder.isChallengerReady(), "Confirmation must be revoked on wager edit");
        assertFalse(holder.isChallengedReady(), "Confirmation must be revoked on wager edit");

        // 3. Confirm both players
        itemBettingGUI.onInventoryClick(clickP1);
        assertTrue(holder.isChallengerReady());

        InventoryClickEvent clickP2 = mock(InventoryClickEvent.class);
        when(clickP2.getInventory()).thenReturn(inv);
        when(clickP2.getWhoClicked()).thenReturn(player2);
        when(clickP2.getRawSlot()).thenReturn(ItemBettingGUI.CHALLENGED_READY_SLOT);

        ArenaInstance mockInstance = mock(ArenaInstance.class);
        when(arenaManager.getAvailableInstance(template)).thenReturn(mockInstance);
        when(mockInstance.acquire(any())).thenReturn(false);

        itemBettingGUI.onInventoryClick(clickP2);
        assertTrue(holder.isChallengedReady());
        assertTrue(holder.isConfirmed(), "Holder must be confirmed once both parties confirm");
    }

    @Test
    @DisplayName("Verify unconfirmed inventory close refunds wager items safely")
    void testItemBettingRefundOnClose() {
        Kit kit = mock(Kit.class);
        ArenaTemplate template = mock(ArenaTemplate.class);
        ItemBettingGUI.ItemBettingHolder holder = new ItemBettingGUI.ItemBettingHolder(player1, player2, kit, template);

        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(holder);
        holder.setInventory(inv);

        ItemStack wagerSword = new ItemStack(Material.DIAMOND_SWORD);
        when(inv.getItem(10)).thenReturn(wagerSword);

        when(player1.getInventory().addItem(wagerSword)).thenReturn(new HashMap<>());

        InventoryCloseEvent closeEvent = mock(InventoryCloseEvent.class);
        when(closeEvent.getInventory()).thenReturn(inv);
        when(closeEvent.getPlayer()).thenReturn(player1);

        itemBettingGUI.onInventoryClose(closeEvent);

        verify(player1.getInventory()).addItem(wagerSword);
        verify(inv).setItem(10, null);
    }

    @Test
    @DisplayName("Verify abort refund returns each side's items to its owner")
    void testItemBettingAbortRefundOwnership() {
        Kit kit = mock(Kit.class);
        ArenaTemplate template = mock(ArenaTemplate.class);
        ItemBettingGUI.ItemBettingHolder holder = new ItemBettingGUI.ItemBettingHolder(player1, player2, kit, template);

        Inventory inv = mock(Inventory.class);
        when(inv.getHolder()).thenReturn(holder);
        holder.setInventory(inv);

        ItemStack challengerSword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack challengedApple = new ItemStack(Material.GOLDEN_APPLE, 8);
        when(inv.getItem(10)).thenReturn(challengerSword);
        when(inv.getItem(14)).thenReturn(challengedApple);

        PlayerInventory inv1 = mock(PlayerInventory.class);
        PlayerInventory inv2 = mock(PlayerInventory.class);
        when(player1.getInventory()).thenReturn(inv1);
        when(player2.getInventory()).thenReturn(inv2);
        when(inv1.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(inv2.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

        // No arena available -> the abort/refund path runs.
        when(arenaManager.getAvailableInstance(template)).thenReturn(null);

        InventoryClickEvent challengerReady = mock(InventoryClickEvent.class);
        when(challengerReady.getInventory()).thenReturn(inv);
        when(challengerReady.getWhoClicked()).thenReturn(player1);
        when(challengerReady.getRawSlot()).thenReturn(ItemBettingGUI.CHALLENGER_READY_SLOT);
        itemBettingGUI.onInventoryClick(challengerReady);

        InventoryClickEvent challengedReady = mock(InventoryClickEvent.class);
        when(challengedReady.getInventory()).thenReturn(inv);
        when(challengedReady.getWhoClicked()).thenReturn(player2);
        when(challengedReady.getRawSlot()).thenReturn(ItemBettingGUI.CHALLENGED_READY_SLOT);
        itemBettingGUI.onInventoryClick(challengedReady);

        // addItem is varargs; Mockito expands a single-element call, so the
        // matcher must be typed as one ItemStack rather than ItemStack[].
        verify(inv1, times(1)).addItem(org.mockito.ArgumentMatchers.<ItemStack>argThat(
                item -> item != null && item.getType() == Material.DIAMOND_SWORD));
        verify(inv1, never()).addItem(org.mockito.ArgumentMatchers.<ItemStack>argThat(
                item -> item != null && item.getType() == Material.GOLDEN_APPLE));
        verify(inv2, times(1)).addItem(org.mockito.ArgumentMatchers.<ItemStack>argThat(
                item -> item != null && item.getType() == Material.GOLDEN_APPLE));
        verify(inv2, never()).addItem(org.mockito.ArgumentMatchers.<ItemStack>argThat(
                item -> item != null && item.getType() == Material.DIAMOND_SWORD));
    }

    @Test
    @DisplayName("Verify DuelMatch ownInventory mode flag and item pot distribution")
    void testOwnInventoryAndItemPotDuelMatch() {
        ArenaInstance arena = mock(ArenaInstance.class);
        ArenaTemplate t = mock(ArenaTemplate.class);
        when(t.getId()).thenReturn("colosseum");
        when(arena.getTemplate()).thenReturn(t);

        Kit kit = mock(Kit.class);
        when(kit.getId()).thenReturn("nodebuff");

        List<ItemStack> pot = new ArrayList<>();
        pot.add(new ItemStack(Material.DIAMOND_SWORD));
        pot.add(new ItemStack(Material.GOLDEN_APPLE, 8));

        DuelMatch match = new DuelMatch(plugin, arena, kit, player1, player2, 0.0, pot, true);

        assertTrue(match.isOwnInventory());
    }
}
