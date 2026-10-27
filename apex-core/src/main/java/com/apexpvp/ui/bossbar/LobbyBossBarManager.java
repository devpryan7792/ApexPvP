package com.apexpvp.ui.bossbar;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.Rank;
import com.apexpvp.profile.RankTier;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Lobby BossBar displaying player's competitive rank badge, current ELO,
 * promotion deficit, and a 10-segmented tier progress meter while standing in the lobby.
 */
public class LobbyBossBarManager implements Listener {

    private final ApexPvPPlugin plugin;
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public LobbyBossBarManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void start() {
        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic sync task every 5 seconds (100 ticks)
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.getMatchManager().isInMatch(player)) {
                    update(player);
                } else {
                    hide(player);
                }
            }
        }, 60L, 100L);
    }

    public void stop() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        for (Map.Entry<UUID, BossBar> entry : activeBars.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.hideBossBar(entry.getValue());
            }
        }
        activeBars.clear();
    }

    public void update(@NotNull Player player) {
        if (plugin.getMatchManager().isInMatch(player)) {
            hide(player);
            return;
        }

        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }

        Rank rank = profile.getRank();
        Rank nextRank = rank.getNextRank();
        int elo = profile.getElo();

        // 1. Calculate proportional progress
        float progress;
        if (nextRank == null) {
            progress = 1.0f;
        } else {
            int lower = rank.getEloThreshold();
            int upper = nextRank.getEloThreshold();
            if (upper > lower) {
                float raw = (float) (elo - lower) / (float) (upper - lower);
                progress = Math.min(1.0f, Math.max(0.0f, raw));
            } else {
                progress = 1.0f;
            }
        }

        // 2. Select BossBar color matching competitive tier
        BossBar.Color color = resolveColor(rank.getTier());

        // 3. Format title
        Component title;
        if (nextRank == null) {
            title = TextUtil.parse(rank.getMiniMessageBadge() + " " + rank.getMiniMessageDisplay()
                    + " <dark_gray>•</dark_gray> <gold><bold>" + elo + "</bold> ELO</gold> <dark_gray>•</dark_gray> <gradient:#FFAA00:#FF5555><bold>PINNACLE RANK</bold></gradient>");
        } else {
            int needed = Math.max(0, nextRank.getEloThreshold() - elo);
            String neededText = (needed == 0)
                    ? "<green><bold>PROMOTION READY!</bold> (/rankup)</green>"
                    : "<yellow>" + needed + " ELO</yellow> <gray>to " + nextRank.getDisplayName() + "</gray>";

            title = TextUtil.parse(rank.getMiniMessageBadge() + " " + rank.getMiniMessageDisplay()
                    + " <dark_gray>•</dark_gray> <gold><bold>" + elo + "</bold> ELO</gold> <gray>(" + neededText + ")</gray>");
        }

        // 4. Update or show
        BossBar bar = activeBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar newBar = BossBar.bossBar(title, progress, color, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(newBar);
            return newBar;
        });

        bar.name(title);
        bar.progress(progress);
        bar.color(color);
        bar.overlay(BossBar.Overlay.NOTCHED_10);
    }

    public void hide(@NotNull Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    @NotNull
    public static BossBar.Color resolveColor(@NotNull RankTier tier) {
        return switch (tier) {
            case COPPER -> BossBar.Color.RED;
            case IRON -> BossBar.Color.WHITE;
            case GOLD -> BossBar.Color.YELLOW;
            case DIAMOND -> BossBar.Color.BLUE;
            case EMERALD -> BossBar.Color.GREEN;
            case NETHERITE -> BossBar.Color.PURPLE;
        };
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                update(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }
}
