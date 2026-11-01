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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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

    private final File mapsFolder;
    private final File schematicsFolder;

    public ArenaManager(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arenaWorldName = plugin.getConfig().getString("arenas.world-name", "apex_arenas");
        this.mapsFolder = new File(plugin.getDataFolder(), "maps");
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
    }

    public void initialize() {
        plugin.getLogger().info("Initializing Multi-Arena Engine...");

        // 0. Ensure maps/ and schematics/ directories and default schematics exist
        ensureDefaultDirectoriesAndSchematics();

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

    private void ensureDefaultDirectoriesAndSchematics() {
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs();
        }
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        File readme = new File(mapsFolder, "README.txt");
        if (!readme.exists()) {
            try {
                java.nio.file.Files.writeString(readme.toPath(),
                        "=== ApexPvP Maps & Schematics Directory ===\n" +
                        "Upload your custom arena schematics here (.schem or .schematic format)!\n\n" +
                        "How to import a map in-game:\n" +
                        "1. Drop your .schem or .schematic file into this 'maps/' folder (e.g. 'castle.schem').\n" +
                        "2. In-game as OP/admin, run:\n" +
                        "   /arena import castle.schem castle \"Castle Arena\"\n" +
                        "3. The plugin will automatically detect dimensions, register the arena template,\n" +
                        "   save it to arenas.yml, and paste instance #0.\n" +
                        "4. Fine-tune your spawn points:\n" +
                        "   - Stand at player 1 spawn: /arena setspawn castle a\n" +
                        "   - Stand at player 2 spawn: /arena setspawn castle b\n" +
                        "   - Fly to spectator spawn: /arena setspawn castle spec\n" +
                        "5. Your arena will immediately appear in the /duel arena selection menu!\n"
                );
            } catch (Exception ignored) {}
        }

        String[] defaultSchems = {"celestial.schem", "glaciem.schem", "spiritual.schematic"};
        for (String schem : defaultSchems) {
            File targetMaps = new File(mapsFolder, schem);
            if (!targetMaps.exists()) {
                try (InputStream in = plugin.getResource("schematics/" + schem)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, targetMaps.toPath());
                    }
                } catch (Exception ignored) {}
            }
            File targetSchems = new File(schematicsFolder, schem);
            if (!targetSchems.exists()) {
                try (InputStream in = plugin.getResource("schematics/" + schem)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, targetSchems.toPath());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @NotNull
    public File getMapsFolder() {
        return mapsFolder;
    }

    @NotNull
    public File getSchematicsFolder() {
        return schematicsFolder;
    }

    @Nullable
    public File getSchematicFile(@NotNull String fileName) {
        File f1 = new File(mapsFolder, fileName);
        if (f1.exists()) return f1;
        File f2 = new File(schematicsFolder, fileName);
        if (f2.exists()) return f2;
        return null;
    }

    public List<String> getAvailableSchematicFiles() {
        Set<String> files = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        File[] inMaps = mapsFolder.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (inMaps != null) {
            for (File f : inMaps) files.add(f.getName());
        }
        File[] inSchems = schematicsFolder.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (inSchems != null) {
            for (File f : inSchems) files.add(f.getName());
        }
        return new ArrayList<>(files);
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
                ArenaTemplate.SpawnPoint ffaSpawn = readSpawn(tSec.getConfigurationSection("spawns.ffa"), 0, 1, 0, 0, 0);

                boolean allowBuild = tSec.getBoolean("rules.allow-build", true);
                boolean allowBreak = tSec.getBoolean("rules.allow-block-break", true);
                int minBuildY = tSec.getInt("rules.min-build-y", 64);
                int maxBuildY = tSec.getInt("rules.max-build-y", 84);
                String schematic = tSec.getString("schematic");

                boolean enabled = tSec.getBoolean("enabled", true);
                boolean regeneration = tSec.getBoolean("regeneration", true);
                boolean teleportToLobby = tSec.getBoolean("teleport-to-lobby", true);
                List<String> allowedModes = tSec.getStringList("allowed-modes");

                ArenaTemplate template = new ArenaTemplate(
                        templateId, key, displayName, relativeBounds,
                        spawnA, spawnB, specSpawn, ffaSpawn,
                        allowBuild, allowBreak, minBuildY, maxBuildY, schematic,
                        enabled, regeneration, teleportToLobby, allowedModes
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
        buildArenaPlatform(instance);
        instances.get(template.getId()).add(instance);
    }

    @NotNull
    public ArenaInstance allocateDynamicInstance(@NotNull ArenaTemplate template) {
        List<ArenaInstance> list = instances.get(template.getId());
        int nextIndex = list.size();
        ArenaInstance dynamicInstance = new ArenaInstance(template, nextIndex, arenaWorld, baselineY, true);
        dynamicInstance.addChunkTickets(plugin);
        buildArenaPlatform(dynamicInstance);
        list.add(dynamicInstance);
        return dynamicInstance;
    }

    public void buildArenaPlatform(@NotNull ArenaInstance instance) {
        boolean pasted = false;
        String schemName = instance.getTemplate().getSchematicFile();
        if (schemName != null) {
            File schemFile = getSchematicFile(schemName);
            if (schemFile != null && com.apexpvp.integration.worldedit.WorldEditHook.isAvailable()) {
                int centerX = (instance.getBounds().getMinX() + instance.getBounds().getMaxX()) / 2;
                int centerZ = (instance.getBounds().getMinZ() + instance.getBounds().getMaxZ()) / 2;
                pasted = com.apexpvp.integration.worldedit.WorldEditHook.pasteSchematic(
                        schemFile, instance.getWorld(), centerX, instance.getOriginY(), centerZ
                );
                if (pasted) {
                    plugin.getLogger().info("Successfully pasted schematic [" + schemName + "] for instance " + instance.getInstanceIdentifier());
                }
            }
        }
        if (!pasted) {
            generateDefaultPlatform(instance);
        }
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
    public ArenaInstance findAvailableInstanceForKit(@NotNull String kitId) {
        for (ArenaTemplate template : templates.values()) {
            if (!template.isEnabled() || !template.isModeAllowed(kitId)) continue;
            ArenaInstance instance = getAvailableInstance(template);
            if (instance != null) {
                return instance;
            }
        }
        for (ArenaTemplate template : templates.values()) {
            if (template.isEnabled() && template.isModeAllowed(kitId)) {
                requestScaling(template.getId());
                break;
            }
        }
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

    @Nullable
    public ArenaInstance getInstance(@NotNull String templateId, int index) {
        List<ArenaInstance> list = getInstances(templateId);
        if (list.isEmpty()) return null;
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return list.get(0);
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
        return createTemplate(id, displayName, bounds, spawnA, spawnB, specSpawn, id + ".schem");
    }

    public boolean createTemplate(@NotNull String id, @NotNull String displayName, @NotNull ArenaBounds bounds,
                                  @NotNull ArenaTemplate.SpawnPoint spawnA,
                                  @NotNull ArenaTemplate.SpawnPoint spawnB,
                                  @NotNull ArenaTemplate.SpawnPoint specSpawn,
                                  @Nullable String schematicFileName) {
        String key = id.toLowerCase();
        if (templates.containsKey(key)) {
            return false;
        }

        int nextId = getNextAvailableTemplateId();
        ArenaTemplate template = new ArenaTemplate(
                nextId, key, displayName, bounds, spawnA, spawnB, specSpawn,
                true, true, 64, 84, schematicFileName
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

    public boolean setSpawnA(@NotNull String templateId, @NotNull Location loc) {
        ArenaTemplate template = getTemplate(templateId);
        if (template == null) return false;
        int originX = template.getTemplateId() * 1000;
        int originY = 64;
        int originZ = 0;
        if (arenaWorld != null && loc.getWorld() != null && loc.getWorld().equals(arenaWorld)) {
            ArenaInstance inside = findInstanceAt(loc);
            if (inside != null) {
                originX = inside.getOriginX();
                originY = inside.getOriginY();
                originZ = inside.getOriginZ();
            }
        }
        return updateSpawnA(templateId, new ArenaTemplate.SpawnPoint(
                loc.getX() - originX, loc.getY() - originY, loc.getZ() - originZ,
                loc.getYaw(), loc.getPitch()));
    }

    public boolean setSpawnB(@NotNull String templateId, @NotNull Location loc) {
        ArenaTemplate template = getTemplate(templateId);
        if (template == null) return false;
        int originX = template.getTemplateId() * 1000;
        int originY = 64;
        int originZ = 0;
        if (arenaWorld != null && loc.getWorld() != null && loc.getWorld().equals(arenaWorld)) {
            ArenaInstance inside = findInstanceAt(loc);
            if (inside != null) {
                originX = inside.getOriginX();
                originY = inside.getOriginY();
                originZ = inside.getOriginZ();
            }
        }
        return updateSpawnB(templateId, new ArenaTemplate.SpawnPoint(
                loc.getX() - originX, loc.getY() - originY, loc.getZ() - originZ,
                loc.getYaw(), loc.getPitch()));
    }

    public boolean setSpectatorSpawn(@NotNull String templateId, @NotNull Location loc) {
        ArenaTemplate template = getTemplate(templateId);
        if (template == null) return false;
        int originX = template.getTemplateId() * 1000;
        int originY = 64;
        int originZ = 0;
        if (arenaWorld != null && loc.getWorld() != null && loc.getWorld().equals(arenaWorld)) {
            ArenaInstance inside = findInstanceAt(loc);
            if (inside != null) {
                originX = inside.getOriginX();
                originY = inside.getOriginY();
                originZ = inside.getOriginZ();
            }
        }
        return updateSpectatorSpawn(templateId, new ArenaTemplate.SpawnPoint(
                loc.getX() - originX, loc.getY() - originY, loc.getZ() - originZ,
                loc.getYaw(), loc.getPitch()));
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

        ArenaTemplate.SpawnPoint ffa = template.getRelativeFfaSpawn();
        config.set(path + ".spawns.ffa.x", ffa.x());
        config.set(path + ".spawns.ffa.y", ffa.y());
        config.set(path + ".spawns.ffa.z", ffa.z());
        config.set(path + ".spawns.ffa.yaw", ffa.yaw());
        config.set(path + ".spawns.ffa.pitch", ffa.pitch());

        config.set(path + ".enabled", template.isEnabled());
        config.set(path + ".regeneration", template.isRegeneration());
        config.set(path + ".teleport-to-lobby", template.isTeleportToLobby());
        config.set(path + ".allowed-modes", template.getAllowedModes());

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

    public boolean setArenaEnabled(@NotNull String templateId, boolean enabled) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withEnabled(enabled);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean setArenaRegeneration(@NotNull String templateId, boolean regen) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withRegeneration(regen);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean setArenaTeleportToLobby(@NotNull String templateId, boolean toLobby) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withTeleportToLobby(toLobby);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean setArenaAllowedModes(@NotNull String templateId, @NotNull java.util.List<String> modes) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withAllowedModes(modes);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean setFfaSpawn(@NotNull String templateId, @NotNull ArenaTemplate.SpawnPoint ffaSpawn) {
        ArenaTemplate existing = templates.get(templateId.toLowerCase());
        if (existing == null) return false;
        ArenaTemplate updated = existing.withFfaSpawn(ffaSpawn);
        templates.put(templateId.toLowerCase(), updated);
        saveTemplateToConfig(updated);
        return true;
    }

    public boolean cloneArena(@NotNull String sourceId, @NotNull String newId, @NotNull String newDisplayName) {
        ArenaTemplate source = templates.get(sourceId.toLowerCase());
        if (source == null || templates.containsKey(newId.toLowerCase())) return false;
        int nextId = templates.values().stream().mapToInt(ArenaTemplate::getTemplateId).max().orElse(0) + 1;
        ArenaTemplate cloned = new ArenaTemplate(
                nextId, newId, newDisplayName, source.getRelativeBounds(),
                source.getRelativeSpawnA(), source.getRelativeSpawnB(), source.getRelativeSpectatorSpawn(),
                source.getRelativeFfaSpawn(), source.isAllowBuild(), source.isAllowBlockBreak(),
                source.getMinBuildY(), source.getMaxBuildY(), source.getSchematicFile(),
                source.isEnabled(), source.isRegeneration(), source.isTeleportToLobby(), source.getAllowedModes()
        );
        templates.put(newId.toLowerCase(), cloned);
        instances.put(newId.toLowerCase(), new java.util.concurrent.CopyOnWriteArrayList<>());
        saveTemplateToConfig(cloned);
        allocateInitialInstance(cloned);
        return true;
    }

    public boolean deleteArena(@NotNull String templateId) {
        String key = templateId.toLowerCase();
        if (!templates.containsKey(key)) return false;
        templates.remove(key);
        java.util.List<ArenaInstance> instList = instances.remove(key);
        if (instList != null) {
            instList.clear();
        }
        File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        config.set("templates." + key, null);
        try {
            config.save(arenasFile);
        } catch (Exception ignored) {}
        return true;
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
