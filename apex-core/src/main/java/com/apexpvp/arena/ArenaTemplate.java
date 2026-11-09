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
    private final int schematicFloorY;

    private final boolean enabled;
    private final boolean regeneration;
    private final boolean teleportToLobby;
    private final java.util.List<String> allowedModes;
    private final SpawnPoint relativeFfaSpawn;
    private final boolean prebuilt;
    private final String iconMaterial;

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
        this(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn,
                new SpawnPoint(0, 1, 0, 0, 0), allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile,
                0, true, true, true, java.util.Collections.emptyList(), false, null);
    }

    public ArenaTemplate(int templateId,
                         @NotNull String id,
                         @NotNull String displayName,
                         @NotNull ArenaBounds relativeBounds,
                         @NotNull SpawnPoint relativeSpawnA,
                         @NotNull SpawnPoint relativeSpawnB,
                         @NotNull SpawnPoint relativeSpectatorSpawn,
                         @NotNull SpawnPoint relativeFfaSpawn,
                         boolean allowBuild,
                         boolean allowBlockBreak,
                         int minBuildY,
                         int maxBuildY,
                         @Nullable String schematicFile,
                         boolean enabled,
                         boolean regeneration,
                         boolean teleportToLobby,
                         @NotNull java.util.List<String> allowedModes) {
        this(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn,
                relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile,
                0, enabled, regeneration, teleportToLobby, allowedModes, false, null);
    }

    public ArenaTemplate(int templateId,
                         @NotNull String id,
                         @NotNull String displayName,
                         @NotNull ArenaBounds relativeBounds,
                         @NotNull SpawnPoint relativeSpawnA,
                         @NotNull SpawnPoint relativeSpawnB,
                         @NotNull SpawnPoint relativeSpectatorSpawn,
                         @NotNull SpawnPoint relativeFfaSpawn,
                         boolean allowBuild,
                         boolean allowBlockBreak,
                         int minBuildY,
                         int maxBuildY,
                         @Nullable String schematicFile,
                         int schematicFloorY,
                         boolean enabled,
                         boolean regeneration,
                         boolean teleportToLobby,
                         @NotNull java.util.List<String> allowedModes) {
        this(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn,
                relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile,
                schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, false, null);
    }

    public ArenaTemplate(int templateId,
                         @NotNull String id,
                         @NotNull String displayName,
                         @NotNull ArenaBounds relativeBounds,
                         @NotNull SpawnPoint relativeSpawnA,
                         @NotNull SpawnPoint relativeSpawnB,
                         @NotNull SpawnPoint relativeSpectatorSpawn,
                         @NotNull SpawnPoint relativeFfaSpawn,
                         boolean allowBuild,
                         boolean allowBlockBreak,
                         int minBuildY,
                         int maxBuildY,
                         @Nullable String schematicFile,
                         int schematicFloorY,
                         boolean enabled,
                         boolean regeneration,
                         boolean teleportToLobby,
                         @NotNull java.util.List<String> allowedModes,
                         boolean prebuilt,
                         @Nullable String iconMaterial) {
        this.templateId = templateId;
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.relativeBounds = Objects.requireNonNull(relativeBounds, "relativeBounds cannot be null");
        this.relativeSpawnA = Objects.requireNonNull(relativeSpawnA, "relativeSpawnA cannot be null");
        this.relativeSpawnB = Objects.requireNonNull(relativeSpawnB, "relativeSpawnB cannot be null");
        this.relativeSpectatorSpawn = Objects.requireNonNull(relativeSpectatorSpawn, "relativeSpectatorSpawn cannot be null");
        this.relativeFfaSpawn = Objects.requireNonNull(relativeFfaSpawn, "relativeFfaSpawn cannot be null");
        this.allowBuild = allowBuild;
        this.allowBlockBreak = allowBlockBreak;
        this.minBuildY = minBuildY;
        this.maxBuildY = maxBuildY;
        this.schematicFile = schematicFile;
        this.schematicFloorY = schematicFloorY;
        this.enabled = enabled;
        this.regeneration = regeneration;
        this.teleportToLobby = teleportToLobby;
        this.allowedModes = java.util.List.copyOf(allowedModes);
        this.prebuilt = prebuilt;
        this.iconMaterial = iconMaterial;
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

    public int getSchematicFloorY() {
        return schematicFloorY;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRegeneration() {
        return regeneration;
    }

    public boolean isTeleportToLobby() {
        return teleportToLobby;
    }

    @NotNull
    public java.util.List<String> getAllowedModes() {
        return allowedModes;
    }

    public boolean isModeAllowed(@NotNull String kitId) {
        if (allowedModes.isEmpty()) return true;
        return allowedModes.contains(kitId.toLowerCase());
    }

    public boolean isPrebuilt() {
        return prebuilt;
    }

    @Nullable
    public String getIconMaterial() {
        return iconMaterial;
    }

    @NotNull
    public SpawnPoint getRelativeFfaSpawn() {
        return relativeFfaSpawn;
    }

    @NotNull
    public ArenaTemplate withSpawnA(@NotNull SpawnPoint newSpawnA) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, newSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withSpawnB(@NotNull SpawnPoint newSpawnB) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, newSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withSpectatorSpawn(@NotNull SpawnPoint newSpec) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, newSpec, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withFfaSpawn(@NotNull SpawnPoint newFfa) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, newFfa, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withEnabled(boolean newEnabled) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, newEnabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withRegeneration(boolean newRegen) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, newRegen, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withTeleportToLobby(boolean newToLobby) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, newToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withAllowedModes(@NotNull java.util.List<String> newAllowedModes) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, newAllowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withDisplayName(@NotNull String newDisplayName) {
        return new ArenaTemplate(templateId, id, newDisplayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withPrebuilt(boolean newPrebuilt) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, newPrebuilt, iconMaterial);
    }

    @NotNull
    public ArenaTemplate withIconMaterial(@Nullable String newIconMaterial) {
        return new ArenaTemplate(templateId, id, displayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, prebuilt, newIconMaterial);
    }

    @NotNull
    public ArenaTemplate withClonedId(@NotNull String newId, @NotNull String newDisplayName, int newTemplateId) {
        return new ArenaTemplate(newTemplateId, newId, newDisplayName, relativeBounds, relativeSpawnA, relativeSpawnB, relativeSpectatorSpawn, relativeFfaSpawn, allowBuild, allowBlockBreak, minBuildY, maxBuildY, schematicFile, schematicFloorY, enabled, regeneration, teleportToLobby, allowedModes, false, iconMaterial);
    }
}
