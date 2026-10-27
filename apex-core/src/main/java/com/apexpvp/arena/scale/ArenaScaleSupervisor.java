package com.apexpvp.arena.scale;

import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaManager;
import com.apexpvp.arena.ArenaState;
import com.apexpvp.arena.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Supervises the dynamic auto-scaling lifecycle of arena instances:
 * Expands instance pools on 100% capacity and purges idle dynamic instances after a timeout.
 */
public class ArenaScaleSupervisor implements Runnable {

    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final int maxInstancesPerTemplate;
    private final long idleTimeoutMillis;

    private BukkitTask supervisorTask;

    public ArenaScaleSupervisor(@NotNull Plugin plugin,
                                @NotNull ArenaManager arenaManager,
                                int maxInstancesPerTemplate,
                                long idleTimeoutMinutes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaManager = Objects.requireNonNull(arenaManager, "arenaManager cannot be null");
        this.maxInstancesPerTemplate = Math.max(1, maxInstancesPerTemplate);
        this.idleTimeoutMillis = TimeUnit.MINUTES.toMillis(Math.max(1, idleTimeoutMinutes));
    }

    public void start() {
        // Run every 10 seconds (200 ticks)
        this.supervisorTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 200L, 200L);
    }

    public void stop() {
        if (supervisorTask != null) {
            supervisorTask.cancel();
            supervisorTask = null;
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (ArenaTemplate template : arenaManager.getAllTemplates()) {
            List<ArenaInstance> instances = arenaManager.getInstances(template.getId());

            // 1. Evaluate Auto-Scaling Requirement
            boolean allInUse = true;
            for (ArenaInstance instance : instances) {
                if (instance.getState() == ArenaState.AVAILABLE) {
                    allInUse = false;
                    break;
                }
            }

            if (allInUse && instances.size() < maxInstancesPerTemplate && arenaManager.isScalingRequested(template.getId())) {
                try {
                    ArenaInstance allocated = arenaManager.allocateDynamicInstance(template);
                    plugin.getLogger().info("Auto-scaled new ArenaInstance [" + allocated.getInstanceIdentifier() + "] at Z=" + allocated.getOriginZ());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to auto-scale instance for template " + template.getId(), e);
                }
            }

            // 2. Evaluate Scale-Down for Idle Dynamic Instances
            // Keep at least 1 instance (or initial standby pool) intact
            if (instances.size() > 1) {
                for (int i = instances.size() - 1; i >= 1; i--) {
                    ArenaInstance instance = instances.get(i);
                    if (instance.isDynamic() && instance.getState() == ArenaState.AVAILABLE) {
                        if (now - instance.getLastUsedTimestamp() > idleTimeoutMillis) {
                            arenaManager.deallocateDynamicInstance(instance);
                            plugin.getLogger().info("Scale-down: Purged idle dynamic ArenaInstance [" + instance.getInstanceIdentifier() + "]");
                        }
                    }
                }
            }
        }
    }
}
