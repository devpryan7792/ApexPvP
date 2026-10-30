package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.duel.DuelRequest;
import com.apexpvp.kit.Kit;
import com.apexpvp.kit.KitRules;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Visual invitation preview GUI presented to the challenged player,
 * displaying challenged kit rules, chosen arena map, wager stakes,
 * and direct one-click Accept and Decline buttons.
 */
public class DuelPreviewGUI implements Listener {

    public static final class DuelPreviewGUIHolder implements InventoryHolder {
        private final UUID challengerUuid;
        private final String challengerName;
        private final Kit kit;
        private final ArenaTemplate template;
        private final double stake;
        private Inventory inventory;

        public DuelPreviewGUIHolder(@NotNull UUID challengerUuid,
                                   @NotNull String challengerName,
                                   @NotNull Kit kit,
                                   @NotNull ArenaTemplate template,
                                   double stake) {
            this.challengerUuid = Objects.requireNonNull(challengerUuid, "challengerUuid cannot be null");
            this.challengerName = Objects.requireNonNull(challengerName, "challengerName cannot be null");
            this.kit = Objects.requireNonNull(kit, "kit cannot be null");
            this.template = Objects.requireNonNull(template, "template cannot be null");
            this.stake = stake;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        public UUID getChallengerUuid() {
            return challengerUuid;
        }

        @NotNull
        public String getChallengerName() {
            return challengerName;
        }

        @NotNull
        public Kit getKit() {
            return kit;
        }

        @NotNull
        public ArenaTemplate getTemplate() {
            return template;
        }

        public double getStake() {
            return stake;
        }
    }

    private final ApexPvPPlugin plugin;

    public DuelPreviewGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player recipient, @NotNull Player challenger, @NotNull DuelRequest request) {
        DuelPreviewGUIHolder holder = new DuelPreviewGUIHolder(
                challenger.getUniqueId(),
                challenger.getName(),
                request.getKit(),
                request.getTemplate(),
                request.getStake()
        );

        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.parse("<dark_gray>Duel from <gold>" + challenger.getName() + "</gold>"));
        holder.setInventory(inv);

        // Fill background with subtle dark gray glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        Kit kit = request.getKit();
        ArenaTemplate template = request.getTemplate();
        double stake = request.getStake();

        // 1. Kit Showcase Card (Slot 11)
        ItemStack kitCard = kit.getIcon().clone();
        ItemMeta kitMeta = kitCard.getItemMeta();
        if (kitMeta != null) {
            kitMeta.displayName(TextUtil.parse("<yellow><bold>Kit:</bold></yellow> " + kit.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            KitRules rules = kit.getRules();
            String modeName = rules.sumoMode() ? "<gradient:#FF55FF:#FFAAFF>Sumo Mode</gradient>"
                    : (rules.comboMode() ? "<gradient:#00FFAA:#00AAFF>Combo (1.8 No-Delay)</gradient>"
                    : "<yellow>Standard 1.21+ PvP</yellow>");
            lore.add(TextUtil.parse("<gray>Combat Style: " + modeName));
            lore.add(TextUtil.parse("<gray>Block Placement: " + (rules.allowBuild() ? "<green>Enabled</green>" : "<red>Disabled</red>")));
            lore.add(TextUtil.parse("<gray>Hunger Depletion: " + (rules.allowHunger() ? "<green>Active</green>" : "<red>Disabled</red>")));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            kitMeta.lore(lore);
            kitCard.setItemMeta(kitMeta);
        }
        inv.setItem(11, kitCard);

        // 2. Arena Showcase Card (Slot 13)
        Material arenaMat = switch (template.getId().toLowerCase()) {
            case "colosseum" -> Material.STONE_BRICKS;
            case "temple" -> Material.QUARTZ_PILLAR;
            case "netherforge" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "cybervoid" -> Material.CRYING_OBSIDIAN;
            case "desertdune" -> Material.CHISELED_SANDSTONE;
            case "frostpeak" -> Material.BLUE_ICE;
            case "sumo" -> Material.SLIME_BALL;
            default -> Material.IRON_BLOCK;
        };
        ItemStack arenaCard = new ItemStack(arenaMat);
        ItemMeta arenaMeta = arenaCard.getItemMeta();
        if (arenaMeta != null) {
            arenaMeta.displayName(TextUtil.parse("<aqua><bold>Arena:</bold></aqua> " + template.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            int width = template.getRelativeBounds().getMaxX() - template.getRelativeBounds().getMinX();
            int length = template.getRelativeBounds().getMaxZ() - template.getRelativeBounds().getMinZ();
            lore.add(TextUtil.parse("<gray>Size: <white>" + width + "x" + length + " Blocks</white>"));
            lore.add(TextUtil.parse("<gray>Building: " + (template.isAllowBuild() ? "<green>Allowed</green>" : "<red>Disabled</red>")));
            lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
            arenaMeta.lore(lore);
            arenaCard.setItemMeta(arenaMeta);
        }
        inv.setItem(13, arenaCard);

        // 3. Stakes Showcase Card (Slot 15)
        ItemStack stakeCard = new ItemStack(stake > 0.0 ? Material.GOLD_BLOCK : Material.EMERALD);
        ItemMeta stakeMeta = stakeCard.getItemMeta();
        if (stakeMeta != null) {
            if (stake > 0.0) {
                stakeMeta.displayName(TextUtil.parse("<gold><bold>Wager Pot:</bold></gold> <yellow>" + (stake * 2.0) + " coins</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<gray>Individual Entry: <gold>" + stake + " coins</gold>"));
                lore.add(TextUtil.parse("<gray>Total Winner Payout: <yellow>" + (stake * 2.0) + " coins</yellow>"));
                lore.add(TextUtil.parse("<gray>(Protected by Server Escrow Vault)</gray>"));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                stakeMeta.lore(lore);
            } else {
                stakeMeta.displayName(TextUtil.parse("<green><bold>Match Stakes:</bold></green> <white>Free Casual Duel</white>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<gray>Zero currency required.</gray>"));
                lore.add(TextUtil.parse("<gray>Casual friendly combat (No ELO change).</gray>"));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                stakeMeta.lore(lore);
            }
            stakeCard.setItemMeta(stakeMeta);
        }
        inv.setItem(15, stakeCard);

        // 4. Accept Action (Slot 20)
        ItemStack acceptBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta acceptMeta = acceptBtn.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.displayName(TextUtil.parse("<green><bold>ACCEPT DUEL</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Click to accept challenge and immediately</gray>"));
            lore.add(TextUtil.parse("<gray>teleport into the arena!</gray>"));
            acceptMeta.lore(lore);
            acceptBtn.setItemMeta(acceptMeta);
        }
        inv.setItem(20, acceptBtn);

        // 5. Decline Action (Slot 24)
        ItemStack declineBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta declineMeta = declineBtn.getItemMeta();
        if (declineMeta != null) {
            declineMeta.displayName(TextUtil.parse("<red><bold>DECLINE DUEL</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Click to dismiss this duel challenge.</gray>"));
            declineMeta.lore(lore);
            declineBtn.setItemMeta(declineMeta);
        }
        inv.setItem(24, declineBtn);

        recipient.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DuelPreviewGUIHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player recipient)) {
                return;
            }

            int slot = event.getRawSlot();
            Player challenger = Bukkit.getPlayer(holder.getChallengerUuid());

            if (slot == 20) { // Accept
                recipient.closeInventory();
                recipient.playSound(recipient.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                if (challenger == null || !challenger.isOnline()) {
                    TextUtil.sendPrefixed(recipient, "<red>Challenger is no longer online.");
                    return;
                }
                plugin.getDuelManager().acceptChallenge(recipient, challenger);
            } else if (slot == 24) { // Decline
                recipient.closeInventory();
                recipient.playSound(recipient.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                if (challenger == null || !challenger.isOnline()) {
                    TextUtil.sendPrefixed(recipient, "<red>Challenger is no longer online.");
                    return;
                }
                plugin.getDuelManager().declineChallenge(recipient, challenger);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DuelPreviewGUIHolder) {
            event.setCancelled(true);
        }
    }
}
