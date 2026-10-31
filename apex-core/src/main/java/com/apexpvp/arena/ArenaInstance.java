package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concrete physical instance of an arena placed in the dedicated void world along the mathematical grid.
 * Grid origin: X = templateId * 1000, Z = instanceIndex * 1000.
 */
public class ArenaInstance {

    private final ArenaTemplate template;
    private final int instanceIndex;
    private final World world;

    private final int originX;
    private final int originY;
    private final int originZ;

    private final ArenaBounds bounds;
    private final Location spawnA;
    private final Location spawnB;
    private final Location spectatorSpawn;

    private final AtomicReference<ArenaState> state;
    private final RollbackSnapshot rollbackSnapshot;
    private volatile long lastUsedTimestamp;
    private final boolean dynamic;

    public ArenaInstance(@NotNull ArenaTemplate template,
                         int instanceIndex,
                         @NotNull World world,
                         int originY,
                         boolean dynamic) {
        this.template = Objects.requireNonNull(template, "template cannot be null");
        this.instanceIndex = instanceIndex;
        this.world = Objects.requireNonNull(world, "world cannot be null");
        this.dynamic = dynamic;

        // Grid calculation: X = templateId * 1000, Z = instanceIndex * 1000
        this.originX = template.getTemplateId() * 1000;
        this.originY = originY;
        this.originZ = instanceIndex * 1000;

        // Translate relative geometry to absolute world positions
        this.bounds = template.getRelativeBounds().offset(originX, originY, originZ);
        this.spawnA = template.getRelativeSpawnA().toLocation(world, originX, originY, originZ);
        this.spawnB = template.getRelativeSpawnB().toLocation(world, originX, originY, originZ);
        this.spectatorSpawn = template.getRelativeSpectatorSpawn().toLocation(world, originX, originY, originZ);

        this.state = new AtomicReference<>(ArenaState.AVAILABLE);
        this.rollbackSnapshot = new RollbackSnapshot();
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public boolean acquire(@NotNull Plugin plugin) {
        if (state.compareAndSet(ArenaState.AVAILABLE, ArenaState.IN_USE)) {
            addChunkTickets(plugin);
            return true;
        }
        return false;
    }

    public void startRollback(@NotNull Plugin plugin, int blocksPerTick, @NotNull Runnable onComplete) {
        state.set(ArenaState.REGENERATING);
        new ArenaRollbackTask(plugin, this, blocksPerTick, () -> {
            rollbackSnapshot.clear();
            this.lastUsedTimestamp = System.currentTimeMillis();
            state.set(ArenaState.AVAILABLE);
            removeChunkTickets(plugin);
            onComplete.run();
        }).start();
    }

    public void addChunkTickets(@NotNull Plugin plugin) {
        for (long chunkKey : bounds.getChunkKeys()) {
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            world.addPluginChunkTicket(cx, cz, plugin);
        }
    }

    public void removeChunkTickets(@NotNull Plugin plugin) {
        for (long chunkKey : bounds.getChunkKeys()) {
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            world.removePluginChunkTicket(cx, cz, plugin);
        }
    }

    @NotNull
    public ArenaTemplate getTemplate() {
        return template;
    }

    public int getInstanceIndex() {
        return instanceIndex;
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    @NotNull
    public Location getOriginLocation() {
        return new Location(world, originX, originY, originZ);
    }

    @NotNull
    public ArenaBounds getBounds() {
        return bounds;
    }

    @NotNull
    public Location getSpawnA() {
        return spawnA.clone();
    }

    @NotNull
    public Location getSpawnB() {
        return spawnB.clone();
    }

    @NotNull
    public Location getSpectatorSpawn() {
        return spectatorSpawn.clone();
    }

    @NotNull
    public ArenaState getState() {
        return state.get();
    }

    public void setState(@NotNull ArenaState newState) {
        this.state.set(newState);
    }

    @NotNull
    public RollbackSnapshot getRollbackSnapshot() {
        return rollbackSnapshot;
    }

    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    public void updateLastUsed() {
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public boolean isDynamic() {
        return dynamic;
    }

    @NotNull
    public String getInstanceIdentifier() {
        return template.getId() + "#" + instanceIndex;
    }
}
