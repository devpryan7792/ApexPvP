package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.kit.Kit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

@DisplayName("KitEditorGUI extraction protection")
class KitEditorGUIProtectionTest {

    private KitEditorGUI gui;
    private Inventory topInventory;
    private Inventory bottomInventory;
    private InventoryView view;
    private Player player;

    @BeforeEach
    void setUp() {
        ApexPvPPlugin plugin = mock(ApexPvPPlugin.class);
        gui = new KitEditorGUI(plugin, new NamespacedKey("apexpvp", "kit_source_slot"));

        topInventory = mock(Inventory.class);
        bottomInventory = mock(Inventory.class);
        view = mock(InventoryView.class);
        when(view.getBottomInventory()).thenReturn(bottomInventory);

        KitEditorGUI.KitEditorGUIHolder holder =
                new KitEditorGUI.KitEditorGUIHolder(mock(Kit.class));
        when(topInventory.getHolder()).thenReturn(holder);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private InventoryClickEvent clickEvent(ClickType type, int rawSlot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(topInventory);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(type);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.isShiftClick()).thenReturn(type.isShiftClick());
        return event;
    }

    @Test
    @DisplayName("Number-key, drop, offhand, collect, and creative clicks are cancelled")
    void extractionClicksAreCancelled() {
        for (ClickType type : new ClickType[]{
                ClickType.NUMBER_KEY,
                ClickType.DROP,
                ClickType.CONTROL_DROP,
                ClickType.SWAP_OFFHAND,
                ClickType.DOUBLE_CLICK,
                ClickType.MIDDLE,
                ClickType.CREATIVE}) {
            InventoryClickEvent event = clickEvent(type, 10);
            gui.onInventoryClick(event);
            verify(event).setCancelled(true);
        }
    }

    @Test
    @DisplayName("Shift-click out of the editor is cancelled")
    void shiftClickIsCancelled() {
        InventoryClickEvent event = clickEvent(ClickType.SHIFT_LEFT, 10);
        gui.onInventoryClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("Left click inside an editable row is allowed")
    void editableLeftClickIsAllowed() {
        InventoryClickEvent event = clickEvent(ClickType.LEFT, 10);
        gui.onInventoryClick(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("Clicks in the player inventory and control rows are cancelled")
    void bottomAndControlClicksAreCancelled() {
        InventoryClickEvent bottom = clickEvent(ClickType.LEFT, 60);
        when(bottom.getClickedInventory()).thenReturn(bottomInventory);
        gui.onInventoryClick(bottom);
        verify(bottom).setCancelled(true);

        InventoryClickEvent control = clickEvent(ClickType.LEFT, 28);
        gui.onInventoryClick(control);
        verify(control).setCancelled(true);
    }

    @Test
    @DisplayName("Drags touching the player inventory or control rows are cancelled")
    void dragsOutsideEditableRowsAreCancelled() {
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getInventory()).thenReturn(topInventory);
        when(drag.getRawSlots()).thenReturn(Set.of(10, 60));

        gui.onInventoryDrag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    @DisplayName("Drags entirely inside editable rows are allowed")
    void dragInsideEditableRowsIsAllowed() {
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getInventory()).thenReturn(topInventory);
        when(drag.getRawSlots()).thenReturn(Set.of(10, 11, 40));

        gui.onInventoryDrag(drag);
        verify(drag, never()).setCancelled(true);
    }
}
