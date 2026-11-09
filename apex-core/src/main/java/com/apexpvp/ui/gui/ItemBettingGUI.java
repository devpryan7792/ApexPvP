package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.DuelMatch;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Real Physical Item Escrow Wagering GUI.
 * Allows 2 players to place inventory items into a shared pot before a duel.
 * Enforces strict anti-scam logic: any item alteration automatically revokes confirmations.
 */
public class ItemBettingGUI implements Listener {

    public static final Set<Integer> CHALLENGER_SLOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    )));

    public static final Set<Integer> CHALLENGED_SLOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            14, 15, 16,
            23, 24, 25,
            32, 33, 34
    )));

    public static final int CHALLENGER_READY_SLOT = 46;
    public static final int CHALLENGED_READY_SLOT = 52;
    public static final int STATUS_SLOT = 49;

    public static final class ItemBettingHolder implements InventoryHolder {
        private Inventory inventory;
        private final Player challenger;
        private final Player challenged;
        private final Kit kit;
        private final ArenaTemplate template;

        private boolean challengerReady = false;
        private boolean challengedReady = false;
        private boolean confirmed = false;

        public ItemBettingHolder(@NotNull Player challenger,
                                 @NotNull Player challenged,
                                 @NotNull Kit kit,
                                 @NotNull ArenaTemplate template) {
            this.challenger = challenger;
            this.challenged = challenged;
            this.kit = kit;
            this.template = template;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public Player getChallenger() {
            return challenger;
        }

        public Player getChallenged() {
            return challenged;
        }

        public Kit getKit() {
            return kit;
        }

        public ArenaTemplate getTemplate() {
            return template;
        }

        public boolean isChallengerReady() {
            return challengerReady;
        }

        public void setChallengerReady(boolean challengerReady) {
            this.challengerReady = challengerReady;
        }

        public boolean isChallengedReady() {
            return challengedReady;
        }

        public void setChallengedReady(boolean challengedReady) {
            this.challengedReady = challengedReady;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }
    }

    private final ApexPvPPlugin plugin;

    public ItemBettingGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player challenger,
                     @NotNull Player challenged,
                     @NotNull Kit kit,
                     @NotNull ArenaTemplate template) {
        if (Bukkit.getServer() == null) return;

        ItemBettingHolder holder = new ItemBettingHolder(challenger, challenged, kit, template);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.parse("<dark_gray>Item Wager Escrow » Pot"));
        holder.setInventory(inv);

        // Build separators and decorations
        ItemStack divider = new ItemStack(Material.IRON_BARS);
        ItemMeta dMeta = divider.getItemMeta();
        if (dMeta != null) {
            dMeta.displayName(TextUtil.parse("<gray>|</gray>"));
            divider.setItemMeta(dMeta);
        }
        int[] dividerSlots = {4, 13, 22, 31, 40};
        for (int s : dividerSlots) {
            inv.setItem(s, divider);
        }

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(Component.empty());
            border.setItemMeta(bMeta);
        }
        int[] borderSlots = {
                0, 1, 2, 3, 5, 6, 7, 8,
                9, 17, 18, 26, 27, 35, 36, 44,
                45, 47, 48, 50, 51, 53
        };
        for (int s : borderSlots) {
            inv.setItem(s, border);
        }

        // Ready buttons
        updateReadyButtons(inv, holder);

        challenger.openInventory(inv);
        challenged.openInventory(inv);
    }

    private void updateReadyButtons(@NotNull Inventory inv, @NotNull ItemBettingHolder holder) {
        // Challenger button
        ItemStack cBtn = new ItemStack(holder.isChallengerReady() ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta cMeta = cBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse(holder.isChallengerReady()
                    ? "<green><bold>" + holder.getChallenger().getName() + " CONFIRMED</bold></green>"
                    : "<red><bold>" + holder.getChallenger().getName() + ": Click to Confirm</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Lock your wagered items.</gray>"));
            lore.add(TextUtil.parse("<gray>Any inventory edit resets this state.</gray>"));
            cMeta.lore(lore);
            cBtn.setItemMeta(cMeta);
        }
        inv.setItem(CHALLENGER_READY_SLOT, cBtn);

        // Challenged button
        ItemStack tBtn = new ItemStack(holder.isChallengedReady() ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta tMeta = tBtn.getItemMeta();
        if (tMeta != null) {
            tMeta.displayName(TextUtil.parse(holder.isChallengedReady()
                    ? "<green><bold>" + holder.getChallenged().getName() + " CONFIRMED</bold></green>"
                    : "<red><bold>" + holder.getChallenged().getName() + ": Click to Confirm</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Lock your wagered items.</gray>"));
            lore.add(TextUtil.parse("<gray>Any inventory edit resets this state.</gray>"));
            tMeta.lore(lore);
            tBtn.setItemMeta(tMeta);
        }
        inv.setItem(CHALLENGED_READY_SLOT, tBtn);

        // Status star
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta sMeta = star.getItemMeta();
        if (sMeta != null) {
            boolean both = holder.isChallengerReady() && holder.isChallengedReady();
            sMeta.displayName(TextUtil.parse(both
                    ? "<green><bold>ALL PARTIES READY - STARTING DUEL!</bold></green>"
                    : "<yellow><bold>WAITING FOR BOTH PLAYERS TO CONFIRM</bold></yellow>"));
            star.setItemMeta(sMeta);
        }
        inv.setItem(STATUS_SLOT, star);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemBettingHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean isTopInventory = rawSlot >= 0 && rawSlot < 54;
        boolean isChallenger = clicker.equals(holder.getChallenger());
        boolean isChallenged = clicker.equals(holder.getChallenged());

        if (!isChallenger && !isChallenged) {
            event.setCancelled(true);
            return;
        }

        // Clicking ready button
        if (isChallenger && rawSlot == CHALLENGER_READY_SLOT) {
            event.setCancelled(true);
            holder.setChallengerReady(!holder.isChallengerReady());
            updateReadyButtons(event.getInventory(), holder);
            playClickSound(clicker);
            checkBothReady(holder);
            return;
        }

        if (isChallenged && rawSlot == CHALLENGED_READY_SLOT) {
            event.setCancelled(true);
            holder.setChallengedReady(!holder.isChallengedReady());
            updateReadyButtons(event.getInventory(), holder);
            playClickSound(clicker);
            checkBothReady(holder);
            return;
        }

        // Enforce slot ownership inside top inventory
        if (isTopInventory) {
            boolean allowed = (isChallenger && CHALLENGER_SLOTS.contains(rawSlot))
                    || (isChallenged && CHALLENGED_SLOTS.contains(rawSlot));

            if (!allowed) {
                event.setCancelled(true);
                return;
            }

            // Allowed to place/modify items in their side: reset ready states on any change!
            if (holder.isChallengerReady() || holder.isChallengedReady()) {
                holder.setChallengerReady(false);
                holder.setChallengedReady(false);
                updateReadyButtons(event.getInventory(), holder);
                TextUtil.sendPrefixed(holder.getChallenger(), "<yellow>Wager updated! Confirmations have been reset.</yellow>");
                TextUtil.sendPrefixed(holder.getChallenged(), "<yellow>Wager updated! Confirmations have been reset.</yellow>");
            }
        }
    }

    private void checkBothReady(@NotNull ItemBettingHolder holder) {
        if (!holder.isChallengerReady() || !holder.isChallengedReady()) {
            return;
        }

        holder.setConfirmed(true);

        // Collect wagered items per side so an abort cannot cross-refund items.
        List<ItemStack> challengerPot = new ArrayList<>();
        List<ItemStack> challengedPot = new ArrayList<>();
        Inventory inv = holder.getInventory();

        for (int s : CHALLENGER_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) {
                challengerPot.add(item.clone());
                inv.setItem(s, null);
            }
        }

        for (int s : CHALLENGED_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) {
                challengedPot.add(item.clone());
                inv.setItem(s, null);
            }
        }

        int totalPotItems = challengerPot.size() + challengedPot.size();

        holder.getChallenger().closeInventory();
        holder.getChallenged().closeInventory();

        // Launch duel with item wager pot
        ArenaInstance instance = plugin.getArenaManager().getAvailableInstance(holder.getTemplate());
        if (instance == null) {
            TextUtil.sendPrefixed(holder.getChallenger(), "<red>No arena instances available. Wager cancelled.");
            TextUtil.sendPrefixed(holder.getChallenged(), "<red>No arena instances available. Wager cancelled.");
            refundPot(holder.getChallenger(), holder.getChallenged(), challengerPot, challengedPot);
            return;
        }

        DuelMatch match = new DuelMatch(plugin, instance, holder.getKit(), holder.getChallenger(), holder.getChallenged(), 0.0, challengerPot, challengedPot, false);
        plugin.getMatchManager().registerMatch(match);
        match.start();

        TextUtil.sendPrefixed(holder.getChallenger(), "<gradient:#00FFAA:#00AAFF><bold>DUEL STARTED!</bold></gradient> <green>Wager pot of " + totalPotItems + " items is locked in escrow!</green>");
        TextUtil.sendPrefixed(holder.getChallenged(), "<gradient:#00FFAA:#00AAFF><bold>DUEL STARTED!</bold></gradient> <green>Wager pot of " + totalPotItems + " items is locked in escrow!</green>");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemBettingHolder holder)) {
            return;
        }

        if (holder.isConfirmed()) {
            return; // Handled cleanly by start
        }

        holder.setConfirmed(true); // Prevent re-entry

        // Safely refund any placed items to respective owners
        Inventory inv = event.getInventory();

        for (int s : CHALLENGER_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) {
                returnItemToPlayer(holder.getChallenger(), item);
                inv.setItem(s, null);
            }
        }

        for (int s : CHALLENGED_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) {
                returnItemToPlayer(holder.getChallenged(), item);
                inv.setItem(s, null);
            }
        }

        TextUtil.sendPrefixed(holder.getChallenger(), "<yellow>Item wager escrow closed. All wagered items returned to inventory.</yellow>");
        TextUtil.sendPrefixed(holder.getChallenged(), "<yellow>Item wager escrow closed. All wagered items returned to inventory.</yellow>");

        if (!event.getPlayer().equals(holder.getChallenger())) {
            holder.getChallenger().closeInventory();
        }
        if (!event.getPlayer().equals(holder.getChallenged())) {
            holder.getChallenged().closeInventory();
        }
    }

    private void returnItemToPlayer(@NotNull Player player, @NotNull ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void refundPot(@NotNull Player challenger,
                           @NotNull Player challenged,
                           @NotNull List<ItemStack> challengerItems,
                           @NotNull List<ItemStack> challengedItems) {
        for (ItemStack item : challengerItems) {
            returnItemToPlayer(challenger, item);
        }
        for (ItemStack item : challengedItems) {
            returnItemToPlayer(challenged, item);
        }
    }

    private void playClickSound(@NotNull Player player) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ItemBettingHolder) {
            event.setCancelled(true);
        }
    }
}
