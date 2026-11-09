package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.match.listener.MatchListener;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MatchListener Extended Combat Rules Tests")
public class MatchListenerTest {

    private MatchManager matchManager;
    private ApexPvPPlugin plugin;
    private MatchListener listener;
    private Player player;
    private PlayerInventory inventory;
    private Match match;
    private Kit kit;

    @BeforeEach
    void setUp() {
        matchManager = mock(MatchManager.class);
        plugin = mock(ApexPvPPlugin.class);
        listener = new MatchListener(matchManager, plugin);

        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("SoupMaster");

        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        AttributeInstance healthAttr = mock(AttributeInstance.class);
        when(healthAttr.getValue()).thenReturn(20.0);
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(healthAttr);

        match = mock(Match.class);
        kit = mock(Kit.class);
        when(match.getState()).thenReturn(MatchState.ACTIVE);
        when(match.getKit()).thenReturn(kit);
        when(matchManager.getMatch(player)).thenReturn(match);
    }

    @Test
    @DisplayName("Verify instant soup consumption heals 7 HP and turns into empty bowl")
    void testInstantSoupConsumption() {
        KitRules rules = new KitRules(false, false, false, false, true, false, false, 0, 0);
        when(kit.getRules()).thenReturn(rules);
        when(player.getHealth()).thenReturn(10.0);

        ItemStack soup = new ItemStack(Material.MUSHROOM_STEW);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.hasItem()).thenReturn(true);
        when(event.getItem()).thenReturn(soup);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);

        listener.onPlayerSoup(event);

        verify(event).setCancelled(true);
        verify(player).setHealth(17.0);
        verify(inventory).setItemInMainHand(argThat(item -> item.getType() == Material.BOWL));
    }

    @Test
    @DisplayName("Verify empty potion bottle is removed when removeEmptyBottles is true")
    void testPotionConsumeRemovesBottle() {
        KitRules rules = new KitRules(false, false, false, false, false, true, false, 0, 0);
        when(kit.getRules()).thenReturn(rules);

        ItemStack potion = new ItemStack(Material.POTION);
        PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(potion);

        // When plugin scheduler is null in simple test mode, removal happens directly
        MatchListener directListener = new MatchListener(matchManager, null);
        directListener.onPotionConsume(event);

        verify(inventory).remove(Material.GLASS_BOTTLE);
    }

    @Test
    @DisplayName("Verify pearl launch applies custom cooldown")
    void testPearlLaunchCooldown() {
        KitRules rules = new KitRules(false, false, false, false, false, false, false, 15, 0);
        when(kit.getRules()).thenReturn(rules);

        EnderPearl pearl = mock(EnderPearl.class);
        when(pearl.getShooter()).thenReturn(player);

        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(pearl);

        listener.onPearlLaunch(event);

        // 15 seconds * 20 ticks = 300 ticks
        verify(player).setCooldown(Material.ENDER_PEARL, 300);
    }

    @Test
    @DisplayName("Verify golden apple consumption applies custom cooldown")
    void testGappleCooldown() {
        KitRules rules = new KitRules(false, false, false, false, false, false, false, 0, 10);
        when(kit.getRules()).thenReturn(rules);

        ItemStack gapple = new ItemStack(Material.GOLDEN_APPLE);
        PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(gapple);

        listener.onPotionConsume(event);

        // 10 seconds * 20 ticks = 200 ticks
        verify(player).setCooldown(Material.GOLDEN_APPLE, 200);
    }

    @Test
    @DisplayName("Verify crafting grid and recipe crafting are blocked when disableCrafting is true")
    void testCraftingLockout() {
        KitRules rules = new KitRules(false, false, false, false, false, false, true, 0, 0);
        when(kit.getRules()).thenReturn(rules);

        CraftItemEvent craftEvent = mock(CraftItemEvent.class);
        when(craftEvent.getWhoClicked()).thenReturn(player);
        listener.onCraftItem(craftEvent);
        verify(craftEvent).setCancelled(true);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getSlotType()).thenReturn(InventoryType.SlotType.CRAFTING);
        listener.onCraftInventoryClick(clickEvent);
        verify(clickEvent).setCancelled(true);
    }

    @Test
    @DisplayName("Lethal damage keeps the hit alive so an offhand totem can resurrect the player")
    void testOffhandTotemPreventsEarlyElimination() {
        when(kit.getRules()).thenReturn(new KitRules(false, false, false, false, false, false, false, 0, 0));
        when(player.getHealth()).thenReturn(4.0);
        ItemStack totem = mock(ItemStack.class);
        when(totem.getType()).thenReturn(Material.TOTEM_OF_UNDYING);
        when(inventory.getItemInMainHand()).thenReturn(null);
        when(inventory.getItemInOffHand()).thenReturn(totem);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(10.0);

        listener.onEntityDamage(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).setHealth(20.0);
        verify(match, never()).onPlayerDeath(any(), any());
    }

    @Test
    @DisplayName("Lethal damage without a totem still ends the participant's life")
    void testLethalDamageWithoutTotemEliminates() {
        when(kit.getRules()).thenReturn(new KitRules(false, false, false, false, false, false, false, 0, 0));
        when(player.getHealth()).thenReturn(4.0);
        when(inventory.getItemInMainHand()).thenReturn(null);
        when(inventory.getItemInOffHand()).thenReturn(null);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(10.0);

        listener.onEntityDamage(event);

        verify(event).setCancelled(true);
        verify(player).setHealth(20.0);
        verify(match).onPlayerDeath(player, null);
    }
}
