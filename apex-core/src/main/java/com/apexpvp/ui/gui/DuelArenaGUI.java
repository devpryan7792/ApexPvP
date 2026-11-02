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
        private Inventory inventory;

        public DuelArenaGUIHolder(@NotNull UUID targetUuid, @NotNull String targetName, @NotNull String kitId, double stake) {
            this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
            this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
            this.kitId = Objects.requireNonNull(kitId, "kitId cannot be null");
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
    }

    private final ApexPvPPlugin plugin;
    private final NamespacedKey arenaKey;

    public DuelArenaGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaKey = new NamespacedKey(plugin, "duel_arena_id");
    }

    public void open(@NotNull Player challenger, @NotNull Player target, @NotNull Kit kit, double stake) {
        List<ArenaTemplate> templates = plugin.getArenaManager().getAllTemplates().stream()
                .filter(ArenaTemplate::isEnabled)
                .filter(t -> t.isModeAllowed(kit.getId()))
                .toList();
        int size = templates.size() > 6 ? 36 : 27;

        DuelArenaGUIHolder holder = new DuelArenaGUIHolder(target.getUniqueId(), target.getName(), kit.getId(), stake);
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
        int[] displaySlots = (size == 27)
                ? new int[]{10, 11, 12, 13, 14, 15, 16}
                : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        int slotIdx = 0;

        // 1. Random Arena Option
        if (slotIdx < displaySlots.length) {
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
                rMeta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, "__random__");
                randomItem.setItemMeta(rMeta);
            }
            inv.setItem(displaySlots[slotIdx++], randomItem);
        }

        // 2. Themed Arena Options
        for (ArenaTemplate template : templates) {
            if (slotIdx >= displaySlots.length) break;
            int targetSlot = displaySlots[slotIdx++];

            Material iconMat = getArenaIcon(template.getId());
            ItemStack item = new ItemStack(iconMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse(template.getDisplayName()));

                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<gray>Theme: <white>" + formatThemeName(template.getId()) + "</white>"));
                String buildStatus = template.isAllowBuild() ? "<green>Allowed</green>" : "<red>Disabled</red>";
                lore.add(TextUtil.parse("<gray>Building: " + buildStatus));
                int width = template.getRelativeBounds().getMaxX() - template.getRelativeBounds().getMinX();
                int length = template.getRelativeBounds().getMaxZ() - template.getRelativeBounds().getMinZ();
                lore.add(TextUtil.parse("<gray>Dimensions: <white>" + width + "x" + length + "</white>"));
                lore.add(TextUtil.parse("<dark_gray>──────────────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<green><bold>CLICK</bold></green> <yellow>» Challenge in this Arena</yellow>"));

                meta.lore(lore);
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, template.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(targetSlot, item);
        }

        challenger.openInventory(inv);
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
            if (arenaId.equals("__random__")) {
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
