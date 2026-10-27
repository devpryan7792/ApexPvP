package com.apexpvp.integration.proxy;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaState;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Periodically reports arena load, queue sizes, and capacity to the Velocity proxy via Plugin Messaging.
 */
public class VelocityReporter implements Runnable {

    public static final String CHANNEL = "apex:sync";

    private final ApexPvPPlugin plugin;
    private final String serverId;
    private BukkitTask task;

    public VelocityReporter(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.serverId = plugin.getConfig().getString("proxy.server-id", "pvp-node-01");
    }

    public void start() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        // Run heartbeat every 5 seconds (100 ticks)
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 100L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void run() {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) return; // Plugin messages require at least one online player on Paper

        int activeMatches = plugin.getMatchManager().getActiveMatchCount();
        int totalArenas = plugin.getArenaManager().getTotalInstanceCount();
        int availableArenas = plugin.getArenaManager().getAvailableInstanceCount();

        @SuppressWarnings("UnstableApiUsage")
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("HEARTBEAT");
        out.writeUTF(serverId);
        out.writeInt(activeMatches);
        out.writeInt(totalArenas);
        out.writeInt(availableArenas);

        carrier.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
}
