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
    public void generateDefaultPlatform(@NotNull ArenaInstance instance) {
        World world = instance.getWorld();
        ArenaBounds bounds = instance.getBounds();
        int originY = instance.getOriginY();
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();

        plugin.getLogger().info("Generating platform for arena [" + instance.getInstanceIdentifier() + "] at Y=" + originY + "...");

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Bedrock foundation beneath the floor to prevent breaking through
                world.getBlockAt(x, originY - 1, z).setType(Material.BEDROCK, false);

                boolean isEdge = (x == minX || x == maxX || z == minZ || z == maxZ);
                if (isEdge) {
                    world.getBlockAt(x, originY, z).setType(Material.STONE_BRICKS, false);
                    for (int y = originY + 1; y <= originY + 4; y++) {
                        world.getBlockAt(x, y, z).setType(Material.TINTED_GLASS, false);
                    }
                } else {
                    Material mat = ((x + z) % 2 == 0) ? Material.SMOOTH_STONE : Material.STONE;
                    world.getBlockAt(x, originY, z).setType(mat, false);
                    for (int y = originY + 1; y <= originY + 4; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
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
