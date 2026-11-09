package com.apexpvp.ui.gui;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Interactive chest GUI presented when challenging a player to select which arena to battle in.
 */
public class DuelArenaGUI implements Listener {

    public static final class DuelArenaGUIHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private final String kitId;
        private final double stake;
        private final int page;
        private Inventory inventory;

        public DuelArenaGUIHolder(@NotNull UUID targetUuid, @NotNull String targetName, @NotNull String kitId, double stake) {
            this(targetUuid, targetName, kitId, stake, 0);
        }

        public DuelArenaGUIHolder(@NotNull UUID targetUuid, @NotNull String targetName, @NotNull String kitId, double stake, int page) {
            this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
            this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
            this.kitId = Objects.requireNonNull(kitId, "kitId cannot be null");
            this.stake = stake;
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
        public UUID getTargetUuid() {
            return targetUuid;
        }

        @NotNull
        public String getTargetName() {
            return targetName;
        }

        @NotNull
        public String getKitId() {
            return kitId;
        }

        public double getStake() {
            return stake;
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
    private static final int PAGE_INFO_SLOT = 49;
    private static final String RANDOM_ARENA = "__random__";
    private static final String PREV_PAGE = "__prev_page__";
    private static final String NEXT_PAGE = "__next_page__";

    private final ApexPvPPlugin plugin;
    private final NamespacedKey arenaKey;

    public DuelArenaGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaKey = new NamespacedKey(plugin, "duel_arena_id");
    }

    public void open(@NotNull Player challenger, @NotNull Player target, @NotNull Kit kit, double stake) {
        openPage(challenger, target, kit, stake, 0);
    }

    private void openPage(@NotNull Player challenger, @NotNull Player target, @NotNull Kit kit,
                          double stake, int requestedPage) {
        List<ArenaTemplate> templates = plugin.getArenaManager().getAllTemplates().stream()
                .filter(ArenaTemplate::isEnabled)
                .filter(t -> t.isModeAllowed(kit.getId()))
                .toList();

        boolean paginated = templates.size() > 13;
        int totalPages = paginatedPageCount(templates.size());
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int size = paginated ? 54 : (templates.size() > 6 ? 36 : 27);

        DuelArenaGUIHolder holder = new DuelArenaGUIHolder(target.getUniqueId(), target.getName(), kit.getId(), stake, page);
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.parse("<dark_gray>Duel <gold>" + target.getName() + "</gold> » Select Arena"));
        holder.setInventory(inv);

        // Fill background with subtle dark gray glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        // Layout arena template items
        int[] displaySlots = paginated
                ? PAGE_SLOTS
                : (size == 27)
                ? new int[]{10, 11, 12, 13, 14, 15, 16}
                : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        int slotIdx = 0;

        // 1. Random Arena Option (first slot of the first page only)
        if (page == 0 && slotIdx < displaySlots.length) {
            ItemStack randomItem = new ItemStack(Material.BEACON);
            ItemMeta rMeta = randomItem.getItemMeta();
            if (rMeta != null) {
                rMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Random Arena</bold></gradient>"));
                List<Component> rLore = new ArrayList<>();
                rLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                rLore.add(TextUtil.parse("<gray>Automatically choose a random</gray>"));
                rLore.add(TextUtil.parse("<gray>arena map for this match.</gray>"));
                rLore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                rLore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Challenge with Random Arena</yellow>"));
                rMeta.lore(rLore);
                rMeta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, RANDOM_ARENA);
                randomItem.setItemMeta(rMeta);
            }
            inv.setItem(displaySlots[slotIdx++], randomItem);
        }

        // 2. Themed Arena Options (this page)
        List<ArenaTemplate> pageTemplates;
        if (paginated) {
            // Page 0 keeps one slot for Random Arena; later pages use all 28 slots.
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
            if (iconMat == null) {
                iconMat = getArenaIcon(template.getId());
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

                int width = template.getRelativeBounds().getMaxX() - template.getRelativeBounds().getMinX();
                int length = template.getRelativeBounds().getMaxZ() - template.getRelativeBounds().getMinZ();
                String scaleTag;
                if (width <= 35 && length <= 35) {
                    scaleTag = "<aqua>Compact 1v1 Arena</aqua>";
                } else if (width <= 75 && length <= 75) {
                    scaleTag = "<yellow>Standard Arena</yellow>";
                } else {
                    scaleTag = "<light_purple>Grand Battleground</light_purple>";
                }
                lore.add(TextUtil.parse("<gray>Scale: " + scaleTag + " <dark_gray>(" + width + "x" + length + ")</dark_gray>"));

                String buildStatus = template.isAllowBuild() ? "<green>Allowed (Auto-Rollback)</green>" : "<red>Disabled</red>";
                lore.add(TextUtil.parse("<gray>Building: " + buildStatus));
                if (template.isAllowBuild()) {
                    lore.add(TextUtil.parse("<gray>Build Limits: <white>Y=" + template.getMinBuildY() + " <gray>to</gray> Y=" + template.getMaxBuildY() + "</white>"));
                }

                if (template.getSchematicFile() != null) {
                    lore.add(TextUtil.parse("<gray>Map Model: <dark_gray>" + template.getSchematicFile() + "</dark_gray>"));
                }
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Challenge in this Arena</yellow>"));

                meta.lore(lore);
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, template.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(targetSlot, item);
        }

        // 3. Pagination controls
        if (paginated) {
            if (page > 0) {
                inv.setItem(PREV_SLOT, navItem(Material.ARROW, "<yellow><bold>« Previous Page</bold></yellow>", PREV_PAGE));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_SLOT, navItem(Material.ARROW, "<yellow><bold>Next Page »</bold></yellow>", NEXT_PAGE));
            }
            inv.setItem(PAGE_INFO_SLOT, navItem(Material.PAPER,
                    "<aqua>Page <white>" + (page + 1) + "</white> / <white>" + totalPages + "</white></aqua>", null));
        }

        if (templates.isEmpty()) {
            ItemStack noArenas = new ItemStack(Material.BARRIER);
            ItemMeta nMeta = noArenas.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(TextUtil.parse("<red><bold>No Arenas Available</bold></red>"));
                List<Component> nLore = new ArrayList<>();
                nLore.add(TextUtil.parse("<gray>All arenas for kit '<gold>" + kit.getId() + "</gold>' are</gray>"));
                nLore.add(TextUtil.parse("<gray>currently in <red>maintenance</red> or disabled.</gray>"));
                nMeta.lore(nLore);
                noArenas.setItemMeta(nMeta);
            }
            inv.setItem(13, noArenas);
        }

        challenger.openInventory(inv);
    }

    static int paginatedPageCount(int templateCount) {
        if (templateCount <= 13) return 1;
        int firstPageCapacity = SLOTS_PER_PAGE - 1;
        int remaining = Math.max(0, templateCount - firstPageCapacity);
        return 1 + (remaining + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
    }

    private ItemStack navItem(@NotNull Material material, @NotNull String name, @Nullable String action) {        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            if (action != null) {
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, action);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getArenaIcon(@NotNull String id) {
        return switch (id.toLowerCase()) {
            case "colosseum" -> Material.STONE_BRICKS;
            case "temple" -> Material.QUARTZ_PILLAR;
            case "netherforge" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "cybervoid" -> Material.CRYING_OBSIDIAN;
            case "desertdune" -> Material.CHISELED_SANDSTONE;
            case "frostpeak" -> Material.BLUE_ICE;
            case "sumo" -> Material.SLIME_BALL;
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
        if (event.getInventory().getHolder() instanceof DuelArenaGUIHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player challenger)) {
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
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

            if (PREV_PAGE.equals(arenaId) || NEXT_PAGE.equals(arenaId)) {
                Player target = Bukkit.getPlayer(holder.getTargetUuid());
                if (target == null || !target.isOnline()) {
                    challenger.closeInventory();
                    TextUtil.sendPrefixed(challenger, "<red>Player is no longer online.");
                    return;
                }
                Kit kit = plugin.getKitManager().getKit(holder.getKitId());
                if (kit == null) {
                    challenger.closeInventory();
                    TextUtil.sendPrefixed(challenger, "<red>Selected kit is no longer available.");
                    return;
                }
                int targetPage = PREV_PAGE.equals(arenaId) ? holder.getPage() - 1 : holder.getPage() + 1;
                challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                openPage(challenger, target, kit, holder.getStake(), targetPage);
                return;
            }

            Player target = Bukkit.getPlayer(holder.getTargetUuid());
            if (target == null || !target.isOnline()) {
                challenger.closeInventory();
                TextUtil.sendPrefixed(challenger, "<red>Player is no longer online.");
                return;
            }

            Kit kit = plugin.getKitManager().getKit(holder.getKitId());
            if (kit == null) {
                challenger.closeInventory();
                TextUtil.sendPrefixed(challenger, "<red>Selected kit is no longer available.");
                return;
            }

            ArenaTemplate template;
            if (arenaId.equals(RANDOM_ARENA)) {
                List<ArenaTemplate> valid = plugin.getArenaManager().getAllTemplates().stream()
                        .filter(ArenaTemplate::isEnabled)
                        .filter(t -> t.isModeAllowed(kit.getId()))
                        .toList();
                if (valid.isEmpty()) {
                    challenger.closeInventory();
                    TextUtil.sendPrefixed(challenger, "<red>No arenas are configured for this kit.");
                    return;
                }
                template = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
            } else {
                template = plugin.getArenaManager().getTemplate(arenaId);
                if (template == null || !template.isEnabled() || !template.isModeAllowed(kit.getId())) {
                    challenger.closeInventory();
                    TextUtil.sendPrefixed(challenger, "<red>Selected arena is not available for this kit.");
                    return;
                }
            }

            challenger.closeInventory();
            challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getDuelManager().sendChallenge(challenger, target, kit, template, holder.getStake());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DuelArenaGUIHolder) {
            event.setCancelled(true);
        }
    }
}
