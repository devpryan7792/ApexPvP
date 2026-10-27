package com.apexpvp.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable blueprint defining an arena's geometry, spawn positions, and interaction rules.
 */
public class ArenaTemplate {

    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        @NotNull
        public Location toLocation(@NotNull World world, int originX, int originY, int originZ) {
            return new Location(world, originX + x, originY + y, originZ + z, yaw, pitch);
        }
    }

    private final int templateId;
    private final String id;
    private final String displayName;
    private final ArenaBounds relativeBounds;
    private final SpawnPoint relativeSpawnA;
    private final SpawnPoint relativeSpawnB;
    private final SpawnPoint relativeSpectatorSpawn;

    private final boolean allowBuild;
    private final boolean allowBlockBreak;
    private final int minBuildY;
    private final int maxBuildY;
    private final String schematicFile;

    public ArenaTemplate(int templateId,
                         @NotNull String id,
                         @NotNull String displayName,
                         @NotNull ArenaBounds relativeBounds,
                         @NotNull SpawnPoint relativeSpawnA,
                         @NotNull SpawnPoint relativeSpawnB,
                         @NotNull SpawnPoint relativeSpectatorSpawn,
                         boolean allowBuild,
                         boolean allowBlockBreak,
                         int minBuildY,
                         int maxBuildY,
                         @Nullable String schematicFile) {
        this.templateId = templateId;
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.relativeBounds = Objects.requireNonNull(relativeBounds, "relativeBounds cannot be null");
        this.relativeSpawnA = Objects.requireNonNull(relativeSpawnA, "relativeSpawnA cannot be null");
        this.relativeSpawnB = Objects.requireNonNull(relativeSpawnB, "relativeSpawnB cannot be null");
        this.relativeSpectatorSpawn = Objects.requireNonNull(relativeSpectatorSpawn, "relativeSpectatorSpawn cannot be null");
        this.allowBuild = allowBuild;
        this.allowBlockBreak = allowBlockBreak;
        this.minBuildY = minBuildY;
        this.maxBuildY = maxBuildY;
        this.schematicFile = schematicFile;
    }

    public int getTemplateId() {
        return templateId;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public ArenaBounds getRelativeBounds() {
        return relativeBounds;
    }

    @NotNull
    public SpawnPoint getRelativeSpawnA() {
        return relativeSpawnA;
    }

    @NotNull
    public SpawnPoint getRelativeSpawnB() {
        return relativeSpawnB;
    }

    @NotNull
    public SpawnPoint getRelativeSpectatorSpawn() {
        return relativeSpectatorSpawn;
    }

    public boolean isAllowBuild() {
        return allowBuild;
    }

    public boolean isAllowBlockBreak() {
        return allowBlockBreak;
    }

    public int getMinBuildY() {
        return minBuildY;
    }

    public int getMaxBuildY() {
        return maxBuildY;
    }

    @Nullable
    public String getSchematicFile() {
        return schematicFile;
    }
}
