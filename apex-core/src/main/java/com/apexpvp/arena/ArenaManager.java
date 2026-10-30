package com.apexpvp.arena;

import com.apexpvp.arena.listener.BlockTrackerListener;
import com.apexpvp.arena.scale.ArenaScaleSupervisor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Enterprise Multi-Arena Engine managing templates, mathematical grid instances,
 * dynamic auto-scaling, and lifecycle state transitions.
 */
public class ArenaManager {

    private final JavaPlugin plugin;
    private final String arenaWorldName;
    private World arenaWorld;

    private final Map<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<ArenaInstance>> instances = new ConcurrentHashMap<>();
    private final Set<String> scalingRequests = ConcurrentHashMap.newKeySet();

    private ArenaScaleSupervisor scaleSupervisor;
    private final int baselineY = 64;

    public ArenaManager(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaWorldName = plugin.getConfig().getString("arenas.world-name", "apex_arenas");
    }

    public void initialize() {
        plugin.getLogger().info("Initializing Multi-Arena Engine...");

        // 1. Initialize or Load Void World
        this.arenaWorld = loadOrCreateVoidWorld();

        // 2. Load Arena Templates from arenas.yml
        loadTemplates();

        // 3. Register Block Tracker Listener
        plugin.getServer().getPluginManager().registerEvents(new BlockTrackerListener(this), plugin);

        // 4. Initialize Standby Arena Instances (Instance #0 for each template)
        for (ArenaTemplate template : templates.values()) {
            allocateInitialInstance(template);
        }

        // 5. Start Auto-Scale Supervisor
        boolean autoScale = plugin.getConfig().getBoolean("arenas.scaling.auto-scale-enabled", true);
        if (autoScale) {
            int maxPerTemplate = plugin.getConfig().getInt("arenas.scaling.max-instances-per-template", 25);
            long idleTimeout = plugin.getConfig().getLong("arenas.scaling.idle-cleanup-timeout-minutes", 15);
            this.scaleSupervisor = new ArenaScaleSupervisor(plugin, this, maxPerTemplate, idleTimeout);
            this.scaleSupervisor.start();
        }

        plugin.getLogger().info("Multi-Arena Engine successfully loaded " + templates.size() +
                " templates and " + getTotalInstanceCount() + " active instances in " + arenaWorldName + ".");
    }

    private World loadOrCreateVoidWorld() {
        World existing = Bukkit.getWorld(arenaWorldName);
        if (existing != null) {
            configureWorldRules(existing);
            return existing;
        }

        plugin.getLogger().info("Generating dedicated void arena world [" + arenaWorldName + "]...");
        WorldCreator creator = new WorldCreator(arenaWorldName);
        creator.generator(new VoidWorldGenerator());
        creator.environment(World.Environment.NORMAL);

        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create void arena world: " + arenaWorldName);
        }

        configureWorldRules(world);
        return world;
    }

    private void configureWorldRules(@NotNull World world) {
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(false);
        world.setTime(6000); // Eternal noon
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setPVP(true);
    }

    private void loadTemplates() {
        File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            plugin.saveResource("arenas.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection section = config.getConfigurationSection("templates");
        if (section == null) {
            plugin.getLogger().warning("No arena templates defined in arenas.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection tSec = section.getConfigurationSection(key);
            if (tSec == null) continue;

            try {
                int templateId = tSec.getInt("template-id");
                String displayName = tSec.getString("display-name", key);

                // Relative bounds
                int minX = tSec.getInt("bounds.min-x", -30);
                int minY = tSec.getInt("bounds.min-y", 0);
                int minZ = tSec.getInt("bounds.min-z", -30);
                int maxX = tSec.getInt("bounds.max-x", 30);
                int maxY = tSec.getInt("bounds.max-y", 40);
                int maxZ = tSec.getInt("bounds.max-z", 30);
                ArenaBounds relativeBounds = new ArenaBounds(minX, minY, minZ, maxX, maxY, maxZ);

                // Relative Spawns
                ArenaTemplate.SpawnPoint spawnA = readSpawn(tSec.getConfigurationSection("spawns.spawn-a"), -15, 1, 0, -90, 0);
                ArenaTemplate.SpawnPoint spawnB = readSpawn(tSec.getConfigurationSection("spawns.spawn-b"), 15, 1, 0, 90, 0);
                ArenaTemplate.SpawnPoint specSpawn = readSpawn(tSec.getConfigurationSection("spawns.spectator"), 0, 15, 0, 0, 45);

                boolean allowBuild = tSec.getBoolean("rules.allow-build", true);
                boolean allowBreak = tSec.getBoolean("rules.allow-block-break", true);
                int minBuildY = tSec.getInt("rules.min-build-y", 64);
                int maxBuildY = tSec.getInt("rules.max-build-y", 84);
                String schematic = tSec.getString("schematic");

                ArenaTemplate template = new ArenaTemplate(
                        templateId, key, displayName, relativeBounds,
                        spawnA, spawnB, specSpawn,
                        allowBuild, allowBreak, minBuildY, maxBuildY, schematic
                );

                templates.put(key.toLowerCase(), template);
                instances.put(key.toLowerCase(), new CopyOnWriteArrayList<>());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load arena template: " + key, e);
            }
        }
    }

    private ArenaTemplate.SpawnPoint readSpawn(@Nullable ConfigurationSection sec,
                                              double defX, double defY, double defZ,
                                              float defYaw, float defPitch) {
        if (sec == null) {
            return new ArenaTemplate.SpawnPoint(defX, defY, defZ, defYaw, defPitch);
        }
        return new ArenaTemplate.SpawnPoint(
                sec.getDouble("x", defX),
                sec.getDouble("y", defY),
                sec.getDouble("z", defZ),
                (float) sec.getDouble("yaw", defYaw),
                (float) sec.getDouble("pitch", defPitch)
        );
    }

    private void allocateInitialInstance(@NotNull ArenaTemplate template) {
        ArenaInstance instance = new ArenaInstance(template, 0, arenaWorld, baselineY, false);
        instance.addChunkTickets(plugin);
        generateDefaultPlatform(instance);
        instances.get(template.getId()).add(instance);
    }

    @NotNull
    public ArenaInstance allocateDynamicInstance(@NotNull ArenaTemplate template) {
        List<ArenaInstance> list = instances.get(template.getId());
        int nextIndex = list.size();
        ArenaInstance dynamicInstance = new ArenaInstance(template, nextIndex, arenaWorld, baselineY, true);
        dynamicInstance.addChunkTickets(plugin);
        generateDefaultPlatform(dynamicInstance);
        list.add(dynamicInstance);
        return dynamicInstance;
    }

    /**
     * Generates a solid arena combat platform with bedrock subfloor, checkered stone surface,
     * and perimeter glass boundary walls.
     */
    /**
     * Generates a high-aesthetic competitive combat platform with bedrock subfloor,
     * themed floor patterns, recessed glowing light channels, center combat medallion,
     * and architectural corner pylons.
     */
    public void generateDefaultPlatform(@NotNull ArenaInstance instance) {
        World world = instance.getWorld();
        ArenaBounds bounds = instance.getBounds();
        int originY = instance.getOriginY();
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        plugin.getLogger().info("Generating aesthetic platform for arena [" + instance.getInstanceIdentifier() + "] at Y=" + originY + "...");

        String theme = instance.getTemplate().getId().toLowerCase();

        // Special handling for Sumo ring
        if (theme.contains("sumo")) {
            int radius = Math.min((maxX - minX) / 2, (maxZ - minZ) / 2);
            if (radius > 10) radius = 8;
            int radiusSq = radius * radius;
            int outerBorderSq = (radius - 1) * (radius - 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int distSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                    if (distSq <= radiusSq) {
                        world.getBlockAt(x, originY - 1, z).setType(Material.BEDROCK, false);
                        if (distSq >= outerBorderSq) {
                            world.getBlockAt(x, originY, z).setType(Material.OAK_PLANKS, false);
                        } else if (distSq <= 4) {
                            world.getBlockAt(x, originY, z).setType(Material.RED_CONCRETE, false);
                        } else {
                            world.getBlockAt(x, originY, z).setType(((x + z) % 2 == 0) ? Material.WHITE_CONCRETE : Material.RED_CONCRETE, false);
                        }
                        for (int y = originY + 1; y <= originY + 8; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    } else {
                        for (int y = originY - 1; y <= originY + 8; y++) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    }
                }
            }
            return;
        }

        Material edgeMat;
        Material floorMat1;
        Material floorMat2;
        Material lightMat;
        Material glassMat;
        Material centerCore;
        Material pylonBase;
        Material pylonPillar;
        Material pylonTop;

        switch (theme) {
            case "temple" -> {
                edgeMat = Material.SMOOTH_QUARTZ;
                floorMat1 = Material.QUARTZ_BRICKS;
                floorMat2 = Material.SMOOTH_QUARTZ;
                lightMat = Material.SEA_LANTERN;
                glassMat = Material.WHITE_STAINED_GLASS;
                centerCore = Material.CHISELED_QUARTZ_BLOCK;
                pylonBase = Material.CHISELED_QUARTZ_BLOCK;
                pylonPillar = Material.QUARTZ_PILLAR;
                pylonTop = Material.SEA_LANTERN;
            }
            case "netherforge" -> {
                edgeMat = Material.POLISHED_BLACKSTONE;
                floorMat1 = Material.POLISHED_BLACKSTONE_BRICKS;
                floorMat2 = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                lightMat = Material.SHROOMLIGHT;
                glassMat = Material.RED_STAINED_GLASS;
                centerCore = Material.GILDED_BLACKSTONE;
                pylonBase = Material.CHISELED_NETHER_BRICKS;
                pylonPillar = Material.POLISHED_BLACKSTONE_BRICKS;
                pylonTop = Material.SOUL_LANTERN;
            }
            case "cybervoid" -> {
                edgeMat = Material.OBSIDIAN;
                floorMat1 = Material.POLISHED_BLACKSTONE_BRICKS;
                floorMat2 = Material.CRYING_OBSIDIAN;
                lightMat = Material.SEA_LANTERN;
                glassMat = Material.CYAN_STAINED_GLASS;
                centerCore = Material.LODESTONE;
                pylonBase = Material.CHISELED_POLISHED_BLACKSTONE;
                pylonPillar = Material.OBSIDIAN;
                pylonTop = Material.END_ROD;
            }
            case "desertdune" -> {
                edgeMat = Material.CUT_SANDSTONE;
                floorMat1 = Material.SMOOTH_SANDSTONE;
                floorMat2 = Material.ORANGE_TERRACOTTA;
                lightMat = Material.GLOWSTONE;
                glassMat = Material.ORANGE_STAINED_GLASS;
                centerCore = Material.CHISELED_SANDSTONE;
                pylonBase = Material.CUT_SANDSTONE;
                pylonPillar = Material.CHISELED_SANDSTONE;
                pylonTop = Material.LANTERN;
            }
            case "frostpeak" -> {
                edgeMat = Material.BLUE_ICE;
                floorMat1 = Material.PACKED_ICE;
                floorMat2 = Material.SNOW_BLOCK;
                lightMat = Material.SEA_LANTERN;
                glassMat = Material.LIGHT_BLUE_STAINED_GLASS;
                centerCore = Material.BLUE_ICE;
                pylonBase = Material.DARK_PRISMARINE;
                pylonPillar = Material.PACKED_ICE;
                pylonTop = Material.END_ROD;
            }
            default -> { // Colosseum and generic
                edgeMat = Material.STONE_BRICKS;
                floorMat1 = Material.SMOOTH_STONE;
                floorMat2 = Material.POLISHED_ANDESITE;
                lightMat = Material.GLOWSTONE;
                glassMat = Material.TINTED_GLASS;
                centerCore = Material.CHISELED_STONE_BRICKS;
                pylonBase = Material.CHISELED_STONE_BRICKS;
                pylonPillar = Material.STONE_BRICKS;
                pylonTop = Material.LANTERN;
            }
        }

        // Build combat arena
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Reinforced bedrock foundation (3 layers) to protect against Crystal PvP explosions
                world.getBlockAt(x, originY - 1, z).setType(Material.BEDROCK, false);
                world.getBlockAt(x, originY - 2, z).setType(Material.BEDROCK, false);
                world.getBlockAt(x, originY - 3, z).setType(Material.BEDROCK, false);

                int distEdge = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
                int distCenterSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);

                boolean isPerimeter = (distEdge == 0);
                boolean isLightChannel = (distEdge == 2);
                boolean isCorner = ((x == minX || x == minX + 1) || (x == maxX || x == maxX - 1)) &&
                                  ((z == minZ || z == minZ + 1) || (z == maxZ || z == maxZ - 1));

                if (isPerimeter) {
                    world.getBlockAt(x, originY, z).setType(edgeMat, false);
                    for (int y = originY + 1; y <= originY + 4; y++) {
                        world.getBlockAt(x, y, z).setType(glassMat, false);
                    }
                    for (int y = originY + 5; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                } else if (isLightChannel) {
                    // Recessed glowing light channel covered by theme glass
                    world.getBlockAt(x, originY - 1, z).setType(lightMat, false);
                    world.getBlockAt(x, originY, z).setType(glassMat, false);
                    for (int y = originY + 1; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                } else if (distCenterSq == 0) {
                    // Center combat medallion core
                    world.getBlockAt(x, originY, z).setType(centerCore, false);
                    for (int y = originY + 1; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                } else if (distCenterSq <= 2) {
                    // Center ring glow
                    world.getBlockAt(x, originY - 1, z).setType(lightMat, false);
                    world.getBlockAt(x, originY, z).setType(glassMat, false);
                    for (int y = originY + 1; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                } else if (distCenterSq <= 8) {
                    // Center medallion accent border
                    world.getBlockAt(x, originY, z).setType(edgeMat, false);
                    for (int y = originY + 1; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                } else {
                    // Combat floor pattern
                    Material mat = ((x + z) % 2 == 0) ? floorMat1 : floorMat2;
                    world.getBlockAt(x, originY, z).setType(mat, false);
                    for (int y = originY + 1; y <= originY + 10; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }

        // Construct 4 Thematic Corner Pylon Towers
        int[][] corners = new int[][]{
                {minX + 1, minZ + 1},
                {minX + 1, maxZ - 1},
                {maxX - 1, minZ + 1},
                {maxX - 1, maxZ - 1}
        };

        for (int[] corner : corners) {
            int cx = corner[0];
            int cz = corner[1];
            world.getBlockAt(cx, originY + 1, cz).setType(pylonBase, false);
            world.getBlockAt(cx, originY + 2, cz).setType(pylonPillar, false);
            world.getBlockAt(cx, originY + 3, cz).setType(pylonPillar, false);
            world.getBlockAt(cx, originY + 4, cz).setType(lightMat, false);
            world.getBlockAt(cx, originY + 5, cz).setType(pylonTop, false);
        }
    }

    public void deallocateDynamicInstance(@NotNull ArenaInstance instance) {
        List<ArenaInstance> list = instances.get(instance.getTemplate().getId());
        if (list != null) {
            list.remove(instance);
            instance.removeChunkTickets(plugin);
        }
    }

    @Nullable
    public ArenaInstance getAvailableInstance(@NotNull ArenaTemplate template) {
        return getAvailableInstance(template.getId());
    }

    @Nullable
    public ArenaInstance getAvailableInstance(@NotNull String templateId) {
        List<ArenaInstance> list = instances.get(templateId.toLowerCase());
        if (list == null) return null;

        for (ArenaInstance instance : list) {
            if (instance.getState() == ArenaState.AVAILABLE) {
                return instance;
            }
        }
        // Signal scale supervisor that this template is under pressure
        requestScaling(templateId);
        return null;
    }

    @Nullable
    public ArenaInstance findInstanceAt(@NotNull Location location) {
        if (arenaWorld == null || !arenaWorld.equals(location.getWorld())) {
            return null;
        }

        for (List<ArenaInstance> list : instances.values()) {
            for (ArenaInstance instance : list) {
                if (instance.getBounds().contains(location)) {
                    return instance;
                }
            }
        }
        return null;
    }

    public void requestScaling(@NotNull String templateId) {
        scalingRequests.add(templateId.toLowerCase());
    }

    public boolean isScalingRequested(@NotNull String templateId) {
        return scalingRequests.remove(templateId.toLowerCase());
    }

    @Nullable
    public ArenaTemplate getTemplate(@NotNull String id) {
        return templates.get(id.toLowerCase());
    }

    @NotNull
    public Collection<ArenaTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    @NotNull
    public List<ArenaInstance> getInstances(@NotNull String templateId) {
        List<ArenaInstance> list = instances.get(templateId.toLowerCase());
        return list != null ? list : Collections.emptyList();
    }

    public int getTotalInstanceCount() {
        int count = 0;
        for (List<ArenaInstance> list : instances.values()) {
            count += list.size();
        }
        return count;
    }

    public int getAvailableInstanceCount() {
        int count = 0;
        for (List<ArenaInstance> list : instances.values()) {
            for (ArenaInstance instance : list) {
                if (instance.getState() == ArenaState.AVAILABLE) {
                    count++;
                }
            }
        }
        return count;
    }

    @NotNull
    public World getArenaWorld() {
        return arenaWorld;
    }

    public int getNextAvailableTemplateId() {
        int maxId = 0;
        for (ArenaTemplate template : templates.values()) {
            if (template.getTemplateId() > maxId) {
                maxId = template.getTemplateId();
            }
        }
        return maxId + 1;
    }

    public boolean createTemplate(@NotNull String id, @NotNull String displayName, @NotNull ArenaBounds bounds,
                                  @NotNull ArenaTemplate.SpawnPoint spawnA,
                                  @NotNull ArenaTemplate.SpawnPoint spawnB,
                                  @NotNull ArenaTemplate.SpawnPoint specSpawn) {
        String key = id.toLowerCase();
        if (templates.containsKey(key)) {
            return false;
        }

        int nextId = getNextAvailableTemplateId();
        ArenaTemplate template = new ArenaTemplate(
                nextId, key, displayName, bounds, spawnA, spawnB, specSpawn,
                true, true, 64, 84, key + ".schem"
        );

        templates.put(key, template);
        instances.put(key, new CopyOnWriteArrayList<>());
        allocateInitialInstance(template);
        saveTemplateToConfig(template);
        return true;
    }

    public boolean updateSpawnA(@NotNull String templateId, @NotNull ArenaTemplate.SpawnPoint spawnA) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withSpawnA(spawnA);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean updateSpawnB(@NotNull String templateId, @NotNull ArenaTemplate.SpawnPoint spawnB) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withSpawnB(spawnB);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean updateSpectatorSpawn(@NotNull String templateId, @NotNull ArenaTemplate.SpawnPoint specSpawn) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withSpectatorSpawn(specSpawn);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public void saveTemplateToConfig(@NotNull ArenaTemplate template) {
        File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        String path = "templates." + template.getId();

        config.set(path + ".template-id", template.getTemplateId());
        config.set(path + ".display-name", template.getDisplayName());

        ArenaBounds b = template.getRelativeBounds();
        config.set(path + ".bounds.min-x", b.getMinX());
        config.set(path + ".bounds.min-y", b.getMinY());
        config.set(path + ".bounds.min-z", b.getMinZ());
        config.set(path + ".bounds.max-x", b.getMaxX());
        config.set(path + ".bounds.max-y", b.getMaxY());
        config.set(path + ".bounds.max-z", b.getMaxZ());

        ArenaTemplate.SpawnPoint sa = template.getRelativeSpawnA();
        config.set(path + ".spawns.spawn-a.x", sa.x());
        config.set(path + ".spawns.spawn-a.y", sa.y());
        config.set(path + ".spawns.spawn-a.z", sa.z());
        config.set(path + ".spawns.spawn-a.yaw", sa.yaw());
        config.set(path + ".spawns.spawn-a.pitch", sa.pitch());

        ArenaTemplate.SpawnPoint sb = template.getRelativeSpawnB();
        config.set(path + ".spawns.spawn-b.x", sb.x());
        config.set(path + ".spawns.spawn-b.y", sb.y());
        config.set(path + ".spawns.spawn-b.z", sb.z());
        config.set(path + ".spawns.spawn-b.yaw", sb.yaw());
        config.set(path + ".spawns.spawn-b.pitch", sb.pitch());

        ArenaTemplate.SpawnPoint spec = template.getRelativeSpectatorSpawn();
        config.set(path + ".spawns.spectator.x", spec.x());
        config.set(path + ".spawns.spectator.y", spec.y());
        config.set(path + ".spawns.spectator.z", spec.z());
        config.set(path + ".spawns.spectator.yaw", spec.yaw());
        config.set(path + ".spawns.spectator.pitch", spec.pitch());

        config.set(path + ".rules.allow-build", template.isAllowBuild());
        config.set(path + ".rules.allow-block-break", template.isAllowBlockBreak());
        config.set(path + ".rules.min-build-y", template.getMinBuildY());
        config.set(path + ".rules.max-build-y", template.getMaxBuildY());
        if (template.getSchematicFile() != null) {
            config.set(path + ".schematic", template.getSchematicFile());
        }

        try {
            config.save(arenasFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arena template " + template.getId() + " to arenas.yml", e);
        }
    }

    public void reloadTemplates() {
        loadTemplates();
        for (ArenaTemplate template : templates.values()) {
            instances.computeIfAbsent(template.getId(), k -> new CopyOnWriteArrayList<>());
            if (instances.get(template.getId()).isEmpty()) {
                allocateInitialInstance(template);
            }
        }
        plugin.getLogger().info("Reloaded arena templates. Active templates: " + templates.size());
    }

    public void shutdown() {
        if (scaleSupervisor != null) {
            scaleSupervisor.stop();
        }
        for (List<ArenaInstance> list : instances.values()) {
            for (ArenaInstance instance : list) {
                instance.removeChunkTickets(plugin);
            }
            list.clear();
        }
        templates.clear();
    }
}
