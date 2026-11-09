package com.apexpvp.ui.gui.admin;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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

/**
 * Detailed In-Game Arena Editor GUI.
 * Lets administrators configure maintenance, regeneration, teleport locations,
 * spawns (A, B, FFA, Spectator), cloning, and deletion with single clicks.
 */
public class ArenaDetailEditorGUI implements Listener {

    public static final class ArenaDetailHolder implements InventoryHolder {
        private final String arenaId;
        private Inventory inventory;

        public ArenaDetailHolder(@NotNull String arenaId) {
            this.arenaId = arenaId;
        }

        public String getArenaId() {
            return arenaId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final ApexPvPPlugin plugin;

    public ArenaDetailEditorGUI(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void open(@NotNull Player player, @NotNull String arenaId) {
        if (!player.hasPermission("apexpvp.arena.admin")) {
            TextUtil.sendPrefixed(player, "<red>You lack permissions to configure arenas.");
            return;
        }

        ArenaTemplate template = plugin.getArenaManager().getTemplate(arenaId);
        if (template == null) {
            TextUtil.sendPrefixed(player, "<red>Arena '<gold>" + arenaId + "</gold>' not found.");
            return;
        }

        if (Bukkit.getServer() == null) return;

        ArenaDetailHolder holder = new ArenaDetailHolder(arenaId);
        Inventory inv = Bukkit.createInventory(holder, 36, TextUtil.parse("<dark_gray>Editor » " + template.getDisplayName()));
        holder.setInventory(inv);

        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.empty());
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Slot 10: Maintenance Mode (Lime / Red Wool)
        boolean enabled = template.isEnabled();
        ItemStack mItem = new ItemStack(enabled ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta mMeta = mItem.getItemMeta();
        if (mMeta != null) {
            mMeta.displayName(TextUtil.parse(enabled
                    ? "<green><bold>Status: ENABLED</bold></green>"
                    : "<red><bold>Status: MAINTENANCE (DISABLED)</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Type: " + (template.isPrebuilt() ? "<gradient:#FFAA00:#FF5555>Prebuilt Arena</gradient>" : "<gradient:#00FFAA:#00AAFF>Custom Map</gradient>")));
            lore.add(TextUtil.parse("<gray>When disabled, players cannot select</gray>"));
            lore.add(TextUtil.parse("<gray>or queue inside this arena.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to toggle status</yellow>"));
            mMeta.lore(lore);
            mItem.setItemMeta(mMeta);
        }
        inv.setItem(10, mItem);

        // Slot 11: Regeneration (Structure Block)
        boolean regen = template.isRegeneration();
        ItemStack rItem = new ItemStack(Material.STRUCTURE_BLOCK);
        ItemMeta rMeta = rItem.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse(regen
                    ? "<green><bold>Arena Regeneration: ON</bold></green>"
                    : "<red><bold>Arena Regeneration: OFF</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Allows blocks to be placed and broken</gray>"));
            lore.add(TextUtil.parse("<gray>during battle, automatically restoring</gray>"));
            lore.add(TextUtil.parse("<gray>the arena clean after match end.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to toggle rollback regeneration</yellow>"));
            rMeta.lore(lore);
            rItem.setItemMeta(rMeta);
        }
        inv.setItem(11, rItem);

        // Slot 12: Teleport Post-Match (Ender Pearl)
        boolean toLobby = template.isTeleportToLobby();
        ItemStack tpItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpMeta = tpItem.getItemMeta();
        if (tpMeta != null) {
            tpMeta.displayName(TextUtil.parse(toLobby
                    ? "<aqua><bold>Post-Match: RETURN TO LOBBY</bold></aqua>"
                    : "<yellow><bold>Post-Match: RETURN TO JOIN LOCATION</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Where combatants are teleported</gray>"));
            lore.add(TextUtil.parse("<gray>when the match finishes.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to toggle return target</yellow>"));
            tpMeta.lore(lore);
            tpItem.setItemMeta(tpMeta);
        }
        inv.setItem(12, tpItem);

        // Slot 13: Allowed Modes (Book)
        ItemStack modesItem = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta moMeta = modesItem.getItemMeta();
        if (moMeta != null) {
            moMeta.displayName(TextUtil.parse("<gradient:#FFAA00:#FF5555><bold>Allowed Game Modes</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            String modes = template.getAllowedModes().isEmpty() ? "ALL MODES ALLOWED" : String.join(", ", template.getAllowedModes());
            lore.add(TextUtil.parse("<gray>Active:</gray> <yellow>" + modes + "</yellow>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to cycle / reset allowed modes</yellow>"));
            moMeta.lore(lore);
            modesItem.setItemMeta(moMeta);
        }
        inv.setItem(13, modesItem);

        // Slot 14: Spawn A (Red Bed)
        ItemStack spawnAItem = new ItemStack(Material.RED_BED);
        ItemMeta saMeta = spawnAItem.getItemMeta();
        if (saMeta != null) {
            saMeta.displayName(TextUtil.parse("<red><bold>Team 1 / Spawn A</bold></red>"));
            List<Component> lore = new ArrayList<>();
            ArenaTemplate.SpawnPoint sa = template.getRelativeSpawnA();
            lore.add(TextUtil.parse("<gray>Rel Pos: <white>X:" + sa.x() + " Y:" + sa.y() + " Z:" + sa.z() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Teleport to Spawn A</white>"));
            lore.add(TextUtil.parse("<gold>▶ Shift-Left-Click:</gold> <white>Set to your current location</white>"));
            saMeta.lore(lore);
            spawnAItem.setItemMeta(saMeta);
        }
        inv.setItem(14, spawnAItem);

        // Slot 15: Spawn B (Blue Bed)
        ItemStack spawnBItem = new ItemStack(Material.BLUE_BED);
        ItemMeta sbMeta = spawnBItem.getItemMeta();
        if (sbMeta != null) {
            sbMeta.displayName(TextUtil.parse("<blue><bold>Team 2 / Spawn B</bold></blue>"));
            List<Component> lore = new ArrayList<>();
            ArenaTemplate.SpawnPoint sb = template.getRelativeSpawnB();
            lore.add(TextUtil.parse("<gray>Rel Pos: <white>X:" + sb.x() + " Y:" + sb.y() + " Z:" + sb.z() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Teleport to Spawn B</white>"));
            lore.add(TextUtil.parse("<gold>▶ Shift-Left-Click:</gold> <white>Set to your current location</white>"));
            sbMeta.lore(lore);
            spawnBItem.setItemMeta(sbMeta);
        }
        inv.setItem(15, spawnBItem);

        // Slot 16: FFA Spawn (Yellow Bed)
        ItemStack ffaItem = new ItemStack(Material.YELLOW_BED);
        ItemMeta ffaMeta = ffaItem.getItemMeta();
        if (ffaMeta != null) {
            ffaMeta.displayName(TextUtil.parse("<yellow><bold>Free-For-All (FFA) Spawn</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            ArenaTemplate.SpawnPoint ffa = template.getRelativeFfaSpawn();
            lore.add(TextUtil.parse("<gray>Rel Pos: <white>X:" + ffa.x() + " Y:" + ffa.y() + " Z:" + ffa.z() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Teleport to FFA Spawn</white>"));
            lore.add(TextUtil.parse("<gold>▶ Shift-Left-Click:</gold> <white>Set to your current location</white>"));
            ffaMeta.lore(lore);
            ffaItem.setItemMeta(ffaMeta);
        }
        inv.setItem(16, ffaItem);

        // Slot 19: Spectator Spawn (Ender Eye)
        ItemStack specItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta specMeta = specItem.getItemMeta();
        if (specMeta != null) {
            specMeta.displayName(TextUtil.parse("<light_purple><bold>Spectator Spawn</bold></light_purple>"));
            List<Component> lore = new ArrayList<>();
            ArenaTemplate.SpawnPoint spec = template.getRelativeSpectatorSpawn();
            lore.add(TextUtil.parse("<gray>Rel Pos: <white>X:" + spec.x() + " Y:" + spec.y() + " Z:" + spec.z() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Teleport to Spectator Spawn</white>"));
            lore.add(TextUtil.parse("<gold>▶ Shift-Left-Click:</gold> <white>Set to your current location</white>"));
            specMeta.lore(lore);
            specItem.setItemMeta(specMeta);
        }
        inv.setItem(19, specItem);

        // Slot 20: Map Display Icon
        Material iconMat = null;
        if (template.getIconMaterial() != null) {
            iconMat = Material.matchMaterial(template.getIconMaterial());
        }
        if (iconMat == null || iconMat.isAir()) {
            iconMat = Material.FILLED_MAP;
        }
        ItemStack iconItem = new ItemStack(iconMat);
        ItemMeta icMeta = iconItem.getItemMeta();
        if (icMeta != null) {
            icMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Map Display Icon</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            String current = (template.getIconMaterial() != null) ? template.getIconMaterial() : "<italic>Default Theme Material</italic>";
            lore.add(TextUtil.parse("<gray>Current Icon: <yellow>" + current + "</yellow></gray>"));
            lore.add(TextUtil.parse("<gray>This icon represents this map inside all</gray>"));
            lore.add(TextUtil.parse("<gray>duel and party map selector menus.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Left-Click:</yellow> <white>Set to item held in main hand</white>"));
            lore.add(TextUtil.parse("<gold>▶ Right-Click:</gold> <white>Cycle aesthetic materials</white>"));
            lore.add(TextUtil.parse("<red>▶ Shift-Right-Click:</red> <white>Reset to Default Theme Material</white>"));
            icMeta.lore(lore);
            iconItem.setItemMeta(icMeta);
        }
        inv.setItem(20, iconItem);

        // Slot 22: Clone Arena (Repeater)
        ItemStack cloneItem = new ItemStack(Material.REPEATER);
        ItemMeta clMeta = cloneItem.getItemMeta();
        if (clMeta != null) {
            clMeta.displayName(TextUtil.parse("<gradient:#00FFAA:#00AAFF><bold>Clone Arena</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Duplicates arena configuration,</gray>"));
            lore.add(TextUtil.parse("<gray>spawns, and allocates a new instance.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>▶ Click to clone as " + template.getId() + "_2</yellow>"));
            clMeta.lore(lore);
            cloneItem.setItemMeta(clMeta);
        }
        inv.setItem(22, cloneItem);

        // Slot 25: Delete Arena (Barrier)
        ItemStack delItem = new ItemStack(Material.BARRIER);
        ItemMeta dMeta = delItem.getItemMeta();
        if (dMeta != null) {
            dMeta.displayName(TextUtil.parse("<red><bold>Delete Arena</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Removes template and instances permanently.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_red>▶ Shift-Click to confirm deletion</dark_red>"));
            dMeta.lore(lore);
            delItem.setItemMeta(dMeta);
        }
        inv.setItem(25, delItem);

        // Slot 31: Back to Arenas List (Arrow)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<yellow>« Back to Arenas List</yellow>"));
            back.setItemMeta(bMeta);
        }
        inv.setItem(31, back);

        player.openInventory(inv);
        try {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ArenaDetailHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String arenaId = holder.getArenaId();
        ArenaTemplate template = plugin.getArenaManager().getTemplate(arenaId);
        if (template == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        switch (slot) {
            case 10 -> {
                // Toggle Maintenance
                boolean next = !template.isEnabled();
                plugin.getArenaManager().setArenaEnabled(arenaId, next);
                playSuccessSound(player);
                TextUtil.sendPrefixed(player, next
                        ? "<green>Arena '<gold>" + template.getDisplayName() + "</gold>' is now <bold>ENABLED</bold>."
                        : "<yellow>Arena '<gold>" + template.getDisplayName() + "</gold>' is now in <bold>MAINTENANCE</bold>.");
                open(player, arenaId);
            }
            case 11 -> {
                // Toggle Regeneration
                boolean next = !template.isRegeneration();
                plugin.getArenaManager().setArenaRegeneration(arenaId, next);
                playSuccessSound(player);
                TextUtil.sendPrefixed(player, next
                        ? "<green>Regeneration enabled for <gold>" + template.getDisplayName() + "</gold>."
                        : "<yellow>Regeneration disabled for <gold>" + template.getDisplayName() + "</gold>.");
                open(player, arenaId);
            }
            case 12 -> {
                // Toggle Teleport
                boolean next = !template.isTeleportToLobby();
                plugin.getArenaManager().setArenaTeleportToLobby(arenaId, next);
                playSuccessSound(player);
                TextUtil.sendPrefixed(player, next
                        ? "<green>Post-match returns players to <aqua>Lobby</aqua>."
                        : "<green>Post-match returns players to <yellow>Pre-Match Location</yellow>.");
                open(player, arenaId);
            }
            case 13 -> {
                // Toggle Allowed Modes
                List<String> cur = template.getAllowedModes();
                if (cur.isEmpty()) {
                    plugin.getArenaManager().setArenaAllowedModes(arenaId, List.of("spear", "crystal", "nodebuff"));
                    TextUtil.sendPrefixed(player, "<green>Locked <gold>" + template.getDisplayName() + "</gold> to: <yellow>spear, crystal, nodebuff</yellow>.");
                } else {
                    plugin.getArenaManager().setArenaAllowedModes(arenaId, List.of());
                    TextUtil.sendPrefixed(player, "<green>Reset <gold>" + template.getDisplayName() + "</gold> to: <yellow>ALL MODES ALLOWED</yellow>.");
                }
                playSuccessSound(player);
                open(player, arenaId);
            }
            case 14 -> {
                // Spawn A
                handleSpawnAction(player, arenaId, "a", click);
            }
            case 15 -> {
                // Spawn B
                handleSpawnAction(player, arenaId, "b", click);
            }
            case 16 -> {
                // FFA Spawn
                handleSpawnAction(player, arenaId, "ffa", click);
            }
            case 19 -> {
                // Spectator Spawn
                handleSpawnAction(player, arenaId, "spec", click);
            }
            case 20 -> {
                // Map Display Icon
                if (event.isShiftClick() && event.isRightClick()) {
                    // Reset to default theme material
                    plugin.getArenaManager().setArenaIcon(arenaId, null);
                    playSuccessSound(player);
                    TextUtil.sendPrefixed(player, "<yellow>Reset map icon for <gold>" + arenaId + "</gold> to default theme material.");
                    open(player, arenaId);
                    return;
                }
                if (event.isRightClick()) {
                    // Cycle aesthetic materials
                    List<String> aesthetic = List.of(
                            "GRASS_BLOCK", "SAND", "SNOW_BLOCK", "NETHERRACK", "END_STONE",
                            "OBSIDIAN", "AMETHYST_BLOCK", "PRISMARINE", "DEEPSLATE_BRICKS",
                            "DIAMOND_BLOCK", "GOLD_BLOCK", "IRON_BLOCK", "EMERALD_BLOCK",
                            "CHERRY_SAPLING", "SEA_LANTERN", "CRYING_OBSIDIAN"
                    );
                    String current = template.getIconMaterial();
                    int nextIdx = 0;
                    if (current != null) {
                        int idx = aesthetic.indexOf(current.toUpperCase());
                        if (idx >= 0) {
                            nextIdx = (idx + 1) % aesthetic.size();
                        }
                    }
                    String chosen = aesthetic.get(nextIdx);
                    plugin.getArenaManager().setArenaIcon(arenaId, chosen);
                    playSuccessSound(player);
                    TextUtil.sendPrefixed(player, "<green>Cycled map icon for <gold>" + arenaId + "</gold> to <aqua>" + chosen + "</aqua>!");
                    open(player, arenaId);
                    return;
                }
                // Left-Click: Set to item held in main hand
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                    TextUtil.sendPrefixed(player, "<red>Hold the item in your main hand that you want as the map icon, then click this button!");
                    return;
                }
                String matName = hand.getType().name();
                plugin.getArenaManager().setArenaIcon(arenaId, matName);
                playSuccessSound(player);
                TextUtil.sendPrefixed(player, "<green>Set map icon for arena <gold>" + arenaId + "</gold> to <aqua>" + matName + "</aqua> from held item!");
                open(player, arenaId);
            }
            case 22 -> {
                // Clone Arena
                String newId = arenaId + "_2";
                String newName = template.getDisplayName() + " 2";
                boolean success = plugin.getArenaManager().cloneArena(arenaId, newId, newName);
                if (success) {
                    playSuccessSound(player);
                    TextUtil.sendPrefixed(player, "<gradient:#00FFAA:#00AAFF><bold>CLONED ARENA!</bold></gradient> Successfully cloned to <gold>" + newId + "</gold>!");
                    plugin.getAdminArenaListGUI().open(player);
                } else {
                    TextUtil.sendPrefixed(player, "<red>Failed to clone arena. An arena with ID '<gold>" + newId + "</gold>' already exists.");
                }
            }
            case 25 -> {
                // Delete Arena
                if (click.isShiftClick()) {
                    plugin.getArenaManager().deleteArena(arenaId);
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                    TextUtil.sendPrefixed(player, "<red>Arena '<gold>" + arenaId + "</gold>' was permanently deleted.");
                    plugin.getAdminArenaListGUI().open(player);
                } else {
                    TextUtil.sendPrefixed(player, "<yellow>Please <red>Shift-Click</red> the Barrier to confirm arena deletion.");
                }
            }
            case 31 -> {
                // Back
                plugin.getAdminArenaListGUI().open(player);
            }
        }
    }

    private void handleSpawnAction(@NotNull Player player, @NotNull String arenaId, @NotNull String spawnType, @NotNull ClickType click) {
        if (click.isShiftClick()) {
            // Set spawn at player's location
            boolean success = switch (spawnType) {
                case "a" -> plugin.getArenaManager().setSpawnA(arenaId, player.getLocation());
                case "b" -> plugin.getArenaManager().setSpawnB(arenaId, player.getLocation());
                case "spec" -> plugin.getArenaManager().setSpectatorSpawn(arenaId, player.getLocation());
                case "ffa" -> plugin.getArenaManager().setFfaSpawn(arenaId, player.getLocation());
                default -> false;
            };

            if (success) {
                playSuccessSound(player);
                TextUtil.sendPrefixed(player, "<green>Updated <gold>" + spawnType.toUpperCase() + "</gold> spawn for arena <gold>" + arenaId + "</gold> at your current position!");
                open(player, arenaId);
            }
        } else {
            // Teleport to spawn
            player.closeInventory();
            ArenaInstance inst = plugin.getArenaManager().getInstance(arenaId, 0);
            if (inst == null) {
                TextUtil.sendPrefixed(player, "<red>No active instance found for arena '<gold>" + arenaId + "</gold>'.");
                return;
            }
            Location target = switch (spawnType) {
                case "a" -> inst.getSpawnA();
                case "b" -> inst.getSpawnB();
                case "spec" -> inst.getSpectatorSpawn();
                case "ffa" -> inst.getFfaSpawn();
                default -> inst.getSpectatorSpawn();
            };
            player.teleportAsync(target);
            TextUtil.sendPrefixed(player, "<green>Teleported to <gold>" + spawnType.toUpperCase() + "</gold> spawn of <gold>" + arenaId + "</gold>.");
        }
    }

    private void playSuccessSound(@NotNull Player player) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ArenaDetailHolder) {
            event.setCancelled(true);
        }
    }
}
