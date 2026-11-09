package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import com.apexpvp.party.Party;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Interactive chest GUI allowing party leaders to visually select which arena map
 * to battle on for Party Split scrimmages and Party Free-For-All deathmatches.
 */
public class PartyArenaSelectGUI implements Listener {

    public static final class PartyArenaSelectHolder implements InventoryHolder {
        private final PartyKitSelectGUI.PartyMatchType matchType;
        private final String kitId;
        private final int page;
        private Inventory inventory;

        public PartyArenaSelectHolder(@NotNull PartyKitSelectGUI.PartyMatchType matchType,
                                      @NotNull String kitId,
                                      int page) {
            this.matchType = Objects.requireNonNull(matchType, "matchType cannot be null");
            this.kitId = Objects.requireNonNull(kitId, "kitId cannot be null");
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        public PartyKitSelectGUI.PartyMatchType getMatchType() {
            return matchType;
        }

        @NotNull
        public String getKitId() {
            return kitId;
        }

        public int getPage() {
            return page;
        }
    }

    private static final int SLOTS_PER_PAGE = 28;
    private static final int[] PAGE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 49;
    private static final String RANDOM_ARENA = "__random__";
    private static final String PREV_PAGE = "__prev_page__";
    private static final String NEXT_PAGE = "__next_page__";
    private static final String BACK_TO_KITS = "__back_to_kits__";

    private final ApexPvPPlugin plugin;
    private final NamespacedKey arenaKey;

    public PartyArenaSelectGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaKey = new NamespacedKey(plugin, "party_arena_id");
    }

    public void open(@NotNull Player leader,
                     @NotNull PartyKitSelectGUI.PartyMatchType matchType,
                     @NotNull Kit kit) {
        openPage(leader, matchType, kit, 0);
    }

    private void openPage(@NotNull Player leader,
                          @NotNull PartyKitSelectGUI.PartyMatchType matchType,
                          @NotNull Kit kit,
                          int requestedPage) {
        Party party = plugin.getPartyManager().getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            TextUtil.sendPrefixed(leader, "<red>Only the party leader can configure match settings.");
            return;
        }

        List<ArenaTemplate> templates = plugin.getArenaManager().getAllTemplates().stream()
                .filter(ArenaTemplate::isEnabled)
                .filter(t -> t.isModeAllowed(kit.getId()))
                .toList();

        boolean paginated = templates.size() > 13;
        int totalPages = paginatedPageCount(templates.size());
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, totalPages - 1)));
        int size = paginated ? 54 : (templates.size() > 6 ? 36 : 27);

        PartyArenaSelectHolder holder = new PartyArenaSelectHolder(matchType, kit.getId(), page);
        Inventory inv = Bukkit.createInventory(holder, size,
                TextUtil.parse("<dark_gray>" + matchType.getDisplayName() + " » Select Map"));
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        int[] displaySlots = paginated
                ? PAGE_SLOTS
                : (size == 27)
                ? new int[]{10, 11, 12, 13, 14, 15, 16}
                : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        int slotIdx = 0;

        // 1. Random Arena Option (first slot of page 0)
        if (page == 0 && slotIdx < displaySlots.length) {
            ItemStack randomItem = new ItemStack(Material.BEACON);
            ItemMeta rMeta = randomItem.getItemMeta();
            if (rMeta != null) {
                rMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Random Arena Map</bold></gradient>"));
                List<Component> rLore = new ArrayList<>();
                rLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                rLore.add(TextUtil.parse("<gray>Automatically select a random eligible</gray>"));
                rLore.add(TextUtil.parse("<gray>map for this " + matchType.getDescription() + ".</gray>"));
                rLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                rLore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Launch with Random Map</yellow>"));
                rMeta.lore(rLore);
                rMeta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, RANDOM_ARENA);
                randomItem.setItemMeta(rMeta);
            }
            inv.setItem(displaySlots[slotIdx++], randomItem);
        }

        // 2. Specific Arena Options
        List<ArenaTemplate> pageTemplates;
        if (paginated) {
            int firstPageCount = SLOTS_PER_PAGE - 1;
            int start = page == 0 ? 0 : firstPageCount + (page - 1) * SLOTS_PER_PAGE;
            int end = Math.min(templates.size(), start + (page == 0 ? firstPageCount : SLOTS_PER_PAGE));
            pageTemplates = templates.subList(start, end);
        } else {
            pageTemplates = templates;
        }

        for (ArenaTemplate template : pageTemplates) {
            if (slotIdx >= displaySlots.length) break;
            int targetSlot = displaySlots[slotIdx++];

            Material iconMat = null;
            if (template.getIconMaterial() != null) {
                iconMat = Material.matchMaterial(template.getIconMaterial());
            }
            if (iconMat == null || iconMat.isAir()) {
                iconMat = getFallbackArenaIcon(template.getId());
            }

            ItemStack item = new ItemStack(iconMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse(template.getDisplayName()));

                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                String typeBadge = template.isPrebuilt()
                        ? "<gradient:#FFAA00:#FF5555>[Prebuilt Arena]</gradient>"
                        : "<gradient:#00FFAA:#00AAFF>[Custom Map]</gradient>";
                lore.add(TextUtil.parse(typeBadge + " <gray>Theme: <white>" + formatThemeName(template.getId()) + "</white>"));

                String regenBadge = template.isRegeneration()
                        ? "<green>Active (Auto-Restoring)</green>"
                        : "<red>Disabled</red>";
                lore.add(TextUtil.parse("<gray>Rollback Engine: " + regenBadge));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Launch " + matchType.getDisplayName() + " on this Map</yellow>"));

                meta.lore(lore);
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, template.getId());
                item.setItemMeta(meta);
            }

            inv.setItem(targetSlot, item);
        }

        // 3. Navigation controls
        if (paginated) {
            if (page > 0) {
                inv.setItem(PREV_SLOT, createControlItem(Material.ARROW, "<yellow>« Previous Page (" + page + "/" + totalPages + ")</yellow>", PREV_PAGE));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_SLOT, createControlItem(Material.ARROW, "<yellow>Next Page (" + (page + 2) + "/" + totalPages + ") »</yellow>", NEXT_PAGE));
            }
            inv.setItem(BACK_SLOT, createControlItem(Material.BARRIER, "<red>« Back to Kit Selection</red>", BACK_TO_KITS));
        } else {
            int backSlot = (size == 27) ? 22 : 31;
            inv.setItem(backSlot, createControlItem(Material.BARRIER, "<red>« Back to Kit Selection</red>", BACK_TO_KITS));
        }

        leader.openInventory(inv);
    }

    private static int paginatedPageCount(int totalTemplates) {
        if (totalTemplates <= 0) return 1;
        int firstPageCount = SLOTS_PER_PAGE - 1;
        if (totalTemplates <= firstPageCount) return 1;
        return 1 + (int) Math.ceil((totalTemplates - firstPageCount) / (double) SLOTS_PER_PAGE);
    }

    @NotNull
    private ItemStack createControlItem(@NotNull Material material, @NotNull String name, @NotNull String actionKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, actionKey);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getFallbackArenaIcon(@NotNull String templateId) {
        return switch (templateId.toLowerCase()) {
            case "colosseum" -> Material.SANDSTONE;
            case "temple" -> Material.QUARTZ_BLOCK;
            case "netherforge" -> Material.NETHER_BRICKS;
            case "cybervoid" -> Material.OBSIDIAN;
            case "desertdune" -> Material.RED_SANDSTONE;
            case "frostpeak" -> Material.BLUE_ICE;
            case "sumo" -> Material.OAK_PLANKS;
            case "celestial" -> Material.GOLD_BLOCK;
            case "glaciem" -> Material.PACKED_ICE;
            case "spiritual" -> Material.AMETHYST_BLOCK;
            default -> Material.IRON_BLOCK;
        };
    }

    private String formatThemeName(@NotNull String id) {
        return switch (id.toLowerCase()) {
            case "colosseum" -> "Gladiator Colosseum";
            case "temple" -> "Temple of Olympus";
            case "netherforge" -> "Infernal Nether Forge";
            case "cybervoid" -> "Futuristic Cyber Void";
            case "desertdune" -> "Arid Desert Dune";
            case "frostpeak" -> "Glacial Frost Peak";
            case "sumo" -> "Elevated Sumo Ring";
            case "celestial" -> "Celestial Bastion";
            case "glaciem" -> "Glaciem Arena";
            case "spiritual" -> "Spiritual Realm";
            default -> id;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PartyArenaSelectHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player leader)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String arenaId = meta.getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
        if (arenaId == null) {
            return;
        }

        Kit kit = plugin.getKitManager().getKit(holder.getKitId());
        if (kit == null) {
            leader.closeInventory();
            TextUtil.sendPrefixed(leader, "<red>Selected kit is no longer available.");
            return;
        }

        if (BACK_TO_KITS.equals(arenaId)) {
            leader.closeInventory();
            try {
                leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            } catch (Throwable ignored) {}
            plugin.getPartyKitSelectGUI().open(leader, holder.getMatchType());
            return;
        }

        if (PREV_PAGE.equals(arenaId) || NEXT_PAGE.equals(arenaId)) {
            int targetPage = PREV_PAGE.equals(arenaId) ? holder.getPage() - 1 : holder.getPage() + 1;
            try {
                leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            } catch (Throwable ignored) {}
            openPage(leader, holder.getMatchType(), kit, targetPage);
            return;
        }

        ArenaTemplate selectedTemplate = null;
        if (RANDOM_ARENA.equals(arenaId)) {
            List<ArenaTemplate> valid = plugin.getArenaManager().getAllTemplates().stream()
                    .filter(ArenaTemplate::isEnabled)
                    .filter(t -> t.isModeAllowed(kit.getId()))
                    .toList();
            if (!valid.isEmpty()) {
                selectedTemplate = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
            }
        } else {
            selectedTemplate = plugin.getArenaManager().getTemplate(arenaId);
        }

        leader.closeInventory();
        try {
            leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } catch (Throwable ignored) {}

        if (holder.getMatchType() == PartyKitSelectGUI.PartyMatchType.SPLIT) {
            plugin.getPartyManager().splitParty(leader, kit, selectedTemplate);
        } else {
            plugin.getPartyManager().startPartyFfa(leader, kit, selectedTemplate);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PartyArenaSelectHolder) {
            event.setCancelled(true);
        }
    }
}
