package com.apexpvp.match;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract Match state machine enforcing strict lifecycle transitions:
 * PREPARING -> COUNTDOWN -> ACTIVE -> ENDING -> CLEANUP.
 */
public abstract class Match {

    protected final UUID matchId;
    protected final ApexPvPPlugin plugin;
    protected final ArenaInstance arena;
    protected final Kit kit;

    protected final AtomicReference<MatchState> state = new AtomicReference<>(MatchState.PREPARING);
    protected final Map<UUID, PlayerRestoreSnapshot> snapshots = new ConcurrentHashMap<>();
    protected final Map<UUID, MatchParticipant> participants = new ConcurrentHashMap<>();

    protected long startTime;
    protected long endTime;
    private BukkitTask countdownTask;
    private BukkitTask matchTickerTask;
    private BukkitTask celebrationTask;
    private WorldBorder worldBorder;
    private double currentSafeRadius = -1.0;
    private boolean warningAnnounced = false;
    private boolean shrinkAnnounced = false;
    protected boolean ownInventory = false;

    public Match(@NotNull ApexPvPPlugin plugin,
                 @NotNull ArenaInstance arena,
                 @NotNull Kit kit) {
        this.matchId = UUID.randomUUID();
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.arena = Objects.requireNonNull(arena, "arena cannot be null");
        this.kit = Objects.requireNonNull(kit, "kit cannot be null");
    }

    public void addParticipant(@NotNull Player player, int teamIndex) {
        participants.put(player.getUniqueId(), new MatchParticipant(player.getUniqueId(), player.getName(), teamIndex));
    }

    public boolean isOwnInventory() {
        return ownInventory;
    }

    public void setOwnInventory(boolean ownInventory) {
        this.ownInventory = ownInventory;
    }

    /**
     * Phase 1: PREPARING
     * Acquires arena, takes player snapshots, teleports to spawns, applies kit, and freezes.
     */
    public void start() {
        if (!arena.acquire(plugin)) {
            broadcast("<red>Failed to allocate arena instance. Match aborted.");
            if (plugin.getMatchManager() != null) {
                plugin.getMatchManager().unregisterMatch(matchId);
            }
            return;
        }

        state.set(MatchState.PREPARING);

        for (MatchParticipant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.getUuid());
            if (player == null || !player.isOnline()) continue;

            if (plugin.getLobbyBossBarManager() != null) {
                plugin.getLobbyBossBarManager().hide(player);
            }

            // 1. Immutable pre-match snapshot
            snapshots.put(player.getUniqueId(), new PlayerRestoreSnapshot(player));

            // 2. Teleport to designated spawn (team base spawn or FFA radial spawn)
            Location spawn = getSpawnForParticipant(participant);
            if (spawn.getWorld() != null) {
                // Ensure spawn chunk is loaded synchronously so Paper does not drop or cancel the teleport
                int cx = spawn.getBlockX() >> 4;
                int cz = spawn.getBlockZ() >> 4;
                spawn.getWorld().getChunkAt(cx, cz);
            }

            // Reset flight, velocity and switch to SURVIVAL prior to teleport
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0.0f);

            boolean teleported = player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            if (!teleported) {
                player.teleportAsync(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            // 3. Apply Kit with custom hotbar layout (unless using own inventory)
            if (!ownInventory) {
                PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
                String customLayout = (profile != null) ? profile.getCustomHotbar(kit.getId()) : null;
                kit.apply(player, customLayout);
            }
        }

        // Initialize per-player Free Fire / Battle Royale style WorldBorder
        boolean borderEnabled = plugin.getConfig() != null ? plugin.getConfig().getBoolean("match.border.enabled", true) : true;
        if (borderEnabled && Bukkit.getServer() != null) {
            try {
                worldBorder = Bukkit.createWorldBorder();
                int centerX = (arena.getBounds().getMinX() + arena.getBounds().getMaxX()) / 2;
                int centerZ = (arena.getBounds().getMinZ() + arena.getBounds().getMaxZ()) / 2;
                int width = arena.getBounds().getMaxX() - arena.getBounds().getMinX();
                int length = arena.getBounds().getMaxZ() - arena.getBounds().getMinZ();
                double initialDiameter = Math.max(30.0, Math.max(width, length) + 4.0);

                worldBorder.setCenter(centerX, centerZ);
                worldBorder.setSize(initialDiameter);
                worldBorder.setDamageBuffer(0.0);
                worldBorder.setDamageAmount(0.0);
                worldBorder.setWarningDistance(5);

                for (MatchParticipant participant : participants.values()) {
                    Player p = Bukkit.getPlayer(participant.getUuid());
                    if (p != null && p.isOnline()) {
                        p.setWorldBorder(worldBorder);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Transition to COUNTDOWN
        enterCountdown();
    }

    /**
     * Resolves the designated spawn location for a match participant.
     * Can be overridden by subclasses (e.g. FfaMatch for radial perimeter spawning).
     */
    protected Location getSpawnForParticipant(@NotNull MatchParticipant participant) {
        Location baseSpawn = (participant.getTeamIndex() == 0) ? arena.getSpawnA() : arena.getSpawnB();
        Location spawn = (baseSpawn != null) ? baseSpawn.clone() : new Location(null, 0, 0, 0);
        if (spawn.getWorld() != null) {
            long teamMemberIndex = participants.values().stream()
                    .filter(p -> p.getTeamIndex() == participant.getTeamIndex() && p.getUuid().compareTo(participant.getUuid()) < 0)
                    .count();
            if (teamMemberIndex > 0) {
                double yawRad = Math.toRadians(spawn.getYaw() + 90.0);
                double offsetMultiplier = ((teamMemberIndex % 2 == 1) ? ((teamMemberIndex + 1) / 2.0) : -(teamMemberIndex / 2.0));
                spawn.add(offsetMultiplier * 1.5 * Math.cos(yawRad), 0, offsetMultiplier * 1.5 * Math.sin(yawRad));
            }
        }
        return spawn;
    }

    /**
     * Phase 2: COUNTDOWN (5-second countdown with titles and audio)
     */
    protected void enterCountdown() {
        state.set(MatchState.COUNTDOWN);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                if (secondsLeft > 0) {
                    Component title = TextUtil.parse("<yellow>" + secondsLeft);
                    Component subtitle = TextUtil.parse("<gray>Prepare to fight!");
                    Title adventureTitle = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100)));

                    for (MatchParticipant part : participants.values()) {
                        Player p = Bukkit.getPlayer(part.getUuid());
                        if (p != null) {
                            p.showTitle(adventureTitle);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                    secondsLeft--;
                } else {
                    countdownTask.cancel();
                    enterActive();
                }
            }
        }, 0L, 20L);
    }

    /**
     * Phase 3: ACTIVE (Unfreeze, allow damage, enable tracking)
     */
    protected void enterActive() {
        state.set(MatchState.ACTIVE);
        this.startTime = System.currentTimeMillis();

        Component title = TextUtil.parse("<green><bold>FIGHT!");
        Component subtitle = TextUtil.parse("<gold>" + kit.getDisplayName());
        Title fightTitle = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(200)));

        for (MatchParticipant part : participants.values()) {
            Player p = Bukkit.getPlayer(part.getUuid());
            if (p != null) {
                p.showTitle(fightTitle);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
            }
        }

        // Start Match & Safe Zone Ticker (runs once per second = 20 ticks)
        startMatchTicker();
    }

    protected void startMatchTicker() {
        boolean borderEnabled = plugin.getConfig() != null ? plugin.getConfig().getBoolean("match.border.enabled", true) : true;
        int shrinkDelaySec = plugin.getConfig() != null ? plugin.getConfig().getInt("match.border.shrink-delay-seconds", 180) : 180;
        int shrinkDurationSec = plugin.getConfig() != null ? plugin.getConfig().getInt("match.border.shrink-duration-seconds", 60) : 60;
        double minRadius = plugin.getConfig() != null ? plugin.getConfig().getDouble("match.border.minimum-radius", 6.0) : 6.0;
        double damagePerSec = plugin.getConfig() != null ? plugin.getConfig().getDouble("match.border.damage-per-second", 2.0) : 2.0;
        int maxDurationSec = plugin.getConfig() != null ? plugin.getConfig().getInt("match.max-match-duration-seconds", 600) : 600;

        int halfWidth = (arena.getBounds().getMaxX() - arena.getBounds().getMinX()) / 2;
        int halfLength = (arena.getBounds().getMaxZ() - arena.getBounds().getMinZ()) / 2;
        double initialRadius = Math.max(15.0, Math.min(halfWidth, halfLength));
        this.currentSafeRadius = initialRadius;

        int centerX = (arena.getBounds().getMinX() + arena.getBounds().getMaxX()) / 2;
        int centerZ = (arena.getBounds().getMinZ() + arena.getBounds().getMaxZ()) / 2;
        int floorY = arena.getOriginY();

        this.matchTickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.get() != MatchState.ACTIVE) {
                if (matchTickerTask != null) {
                    matchTickerTask.cancel();
                    matchTickerTask = null;
                }
                return;
            }

            long elapsedSec = (System.currentTimeMillis() - startTime) / 1000L;

            // 1. Hard match timeout (e.g. 10 minutes)
            if (elapsedSec >= maxDurationSec) {
                broadcast("<red><bold>MATCH TIMEOUT!</bold></red> <gray>The match exceeded maximum duration limit (" + (maxDurationSec / 60) + "m). Ending match...");
                enterEnding(-1);
                return;
            }

            if (!borderEnabled) return;

            // 2. Warning announcement 15 seconds before shrink begins
            if (elapsedSec >= (shrinkDelaySec - 15) && elapsedSec < shrinkDelaySec && !warningAnnounced) {
                warningAnnounced = true;
                Title warningTitle = Title.title(
                        TextUtil.parse("<yellow><bold>BORDER SHRINKING IN 15s!</bold></yellow>"),
                        TextUtil.parse("<gray>Move toward the center of the arena!</gray>"),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(200))
                );
                for (MatchParticipant part : participants.values()) {
                    Player p = Bukkit.getPlayer(part.getUuid());
                    if (p != null) {
                        p.showTitle(warningTitle);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    }
                }
            }

            // 3. Shrinking safe zone in progress
            if (elapsedSec >= shrinkDelaySec) {
                if (!shrinkAnnounced) {
                    shrinkAnnounced = true;
                    Title shrinkTitle = Title.title(
                            TextUtil.parse("<red><bold>SAFE ZONE SHRINKING!</bold></red>"),
                            TextUtil.parse("<gray>Outside borders take continuous damage!</gray>"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(200))
                    );
                    for (MatchParticipant part : participants.values()) {
                        Player p = Bukkit.getPlayer(part.getUuid());
                        if (p != null) {
                            p.showTitle(shrinkTitle);
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
                        }
                    }
                    if (worldBorder != null) {
                        worldBorder.setSize(minRadius * 2.0, java.util.concurrent.TimeUnit.SECONDS, (long) shrinkDurationSec);
                    }
                }

                long shrinkElapsed = elapsedSec - shrinkDelaySec;
                double progress = Math.min(1.0, (double) shrinkElapsed / Math.max(1, shrinkDurationSec));
                currentSafeRadius = initialRadius - (progress * (initialRadius - minRadius));

                World world = arena.getWorld();
                if (world != null) {
                    int particlePoints = (int) (currentSafeRadius * 2.5);
                    for (int i = 0; i < particlePoints; i++) {
                        double angle = 2 * Math.PI * i / particlePoints;
                        double px = centerX + currentSafeRadius * Math.cos(angle);
                        double pz = centerZ + currentSafeRadius * Math.sin(angle);
                        world.spawnParticle(Particle.FLAME, px, floorY + 1.0, pz, 1, 0, 0, 0, 0.0);
                    }
                }

                for (MatchParticipant part : participants.values()) {
                    if (!part.isAlive()) continue;
                    Player p = Bukkit.getPlayer(part.getUuid());
                    if (p == null || !p.isOnline() || p.isDead()) continue;

                    double dx = p.getLocation().getX() - centerX;
                    double dz = p.getLocation().getZ() - centerZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    boolean outside = (worldBorder != null) ? !worldBorder.isInside(p.getLocation()) : (dist > currentSafeRadius);

                    if (outside) {
                        p.damage(damagePerSec);
                        p.sendActionBar(TextUtil.parse("<red><bold>⚠ OUTSIDE SAFE ZONE!</bold></red> <gray>(-1 ❤ / sec)</gray>"));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
                    }
                }
            }
        }, 20L, 20L);
    }

    /**
     * Handles participant defeat during combat.
     */
    public synchronized void onPlayerDeath(@NotNull Player victim, @Nullable Player killer) {
        if (state.get() != MatchState.ACTIVE) return;

        MatchParticipant victimPart = participants.get(victim.getUniqueId());
        if (victimPart == null || !victimPart.isAlive()) return;

        victimPart.setAlive(false);
        victimPart.setSpectator(true);

        if (plugin.isCitizensAvailable() && net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(victim)) {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(victim);
            if (npc != null) {
                npc.despawn();
            }
        } else {
            if (ownInventory) {
                for (ItemStack drop : victim.getInventory().getContents()) {
                    if (drop != null && drop.getType() != Material.AIR) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), drop);
                    }
                }
                victim.getInventory().clear();
            }
            // Put into spectator mode inside arena
            victim.setGameMode(GameMode.SPECTATOR);
            victim.teleport(arena.getSpectatorSpawn());
        }

        if (killer == null) {
            for (MatchParticipant part : participants.values()) {
                if (part.getTeamIndex() != victimPart.getTeamIndex()) {
                    Player opp = Bukkit.getPlayer(part.getUuid());
                    if (opp != null) {
                        killer = opp;
                        break;
                    }
                }
            }
        }

        String killerName = (killer != null) ? killer.getName() : "environmental damage";
        broadcast("<gold>" + victim.getName() + " <gray>was defeated by <gold>" + killerName + "<gray>.");

        checkWinCondition();
    }

    /**
     * Handles combat logging or unexpected disconnects.
     */
    public synchronized void onPlayerQuit(@NotNull Player player) {
        MatchParticipant part = participants.get(player.getUniqueId());
        if (part == null) return;

        part.setAlive(false);
        try {
            player.setWorldBorder(null);
            player.setInvulnerable(false);
        } catch (Throwable ignored) {
        }
        broadcast("<red>" + player.getName() + " disconnected and forfeited the match.");

        // Detach the quitter immediately. Otherwise they stay registered to this match while
        // the arena is still running, and rejoining puts them back inside the arena dimension.
        PlayerRestoreSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null && plugin.getMatchManager() != null) {
            plugin.getMatchManager().savePendingRestore(player.getUniqueId(), snapshot);
        }
        participants.remove(player.getUniqueId());
        if (plugin.getMatchManager() != null) {
            plugin.getMatchManager().unregisterParticipant(player.getUniqueId());
        }

        if (state.get() == MatchState.ACTIVE || state.get() == MatchState.COUNTDOWN) {
            checkWinCondition();
        }
    }

    /**
     * Evaluates whether a team has achieved victory.
     */
    protected void checkWinCondition() {
        int aliveTeam0 = 0;
        int aliveTeam1 = 0;

        for (MatchParticipant part : participants.values()) {
            if (part.isAlive()) {
                if (part.getTeamIndex() == 0) aliveTeam0++;
                else if (part.getTeamIndex() == 1) aliveTeam1++;
            }
        }

        if (aliveTeam0 == 0 && aliveTeam1 > 0) {
            enterEnding(1);
        } else if (aliveTeam1 == 0 && aliveTeam0 > 0) {
            enterEnding(0);
        } else if (aliveTeam0 == 0 && aliveTeam1 == 0) {
            enterEnding(-1); // Draw
        }
    }

    /**
     * Phase 4: ENDING (Victory celebration, fireworks, cooldown countdown, process payouts)
     */
    protected void enterEnding(int winningTeamIndex) {
        MatchState current = state.get();
        if (current == MatchState.ENDING || current == MatchState.CLEANUP) {
            return;
        }
        if (!state.compareAndSet(current, MatchState.ENDING)) {
            return;
        }

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (matchTickerTask != null) {
            matchTickerTask.cancel();
            matchTickerTask = null;
        }

        this.endTime = System.currentTimeMillis();

        // Subclass hook for mode-specific ELO, stats, and Vault payouts
        processMatchResults(winningTeamIndex);

        if (plugin.getBettingManager() != null) {
            plugin.getBettingManager().onMatchEnd(this, winningTeamIndex);
        }

        // Grant post-match damage immunity to all participants
        for (MatchParticipant part : participants.values()) {
            Player p = Bukkit.getPlayer(part.getUuid());
            if (p != null && p.isOnline()) {
                p.setInvulnerable(true);
            }
        }

        if (ownInventory && winningTeamIndex != -1) {
            broadcast("<gradient:#00FFAA:#00AAFF><bold>LOOTING WINDOW!</bold></gradient> <green>The winner has 15 seconds to loot dropped items before returning to lobby!</green>");
        }

        boolean celebrationEnabled = plugin.getConfig() != null ? plugin.getConfig().getBoolean("match.celebration.enabled", true) : true;
        int celebrationDurationSec = plugin.getConfig() != null ? plugin.getConfig().getInt("match.celebration.duration-seconds", 5) : 5;
        boolean spawnFireworks = plugin.getConfig() != null ? plugin.getConfig().getBoolean("match.celebration.fireworks", true) : true;

        int totalCountdownSec = ownInventory ? 15 : (celebrationEnabled ? Math.max(1, celebrationDurationSec) : 3);

        celebrationTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = totalCountdownSec;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    if (celebrationTask != null) {
                        celebrationTask.cancel();
                        celebrationTask = null;
                    }
                    cleanup();
                    return;
                }

                for (MatchParticipant part : participants.values()) {
                    Player p = Bukkit.getPlayer(part.getUuid());
                    if (p == null || !p.isOnline()) continue;

                    boolean isWinner = (winningTeamIndex != -1 && part.getTeamIndex() == winningTeamIndex);
                    boolean isDraw = (winningTeamIndex == -1);

                    // Show Title on first tick
                    if (secondsLeft == totalCountdownSec) {
                        if (isWinner) {
                            Title winTitle = Title.title(
                                    TextUtil.parse("<gradient:#FFD700:#FFA500><bold>VICTORY!</bold></gradient>"),
                                    TextUtil.parse("<gray>Teleporting to lobby in <yellow>" + secondsLeft + "s</yellow>...</gray>"),
                                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(200))
                            );
                            p.showTitle(winTitle);
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else if (isDraw) {
                            Title drawTitle = Title.title(
                                    TextUtil.parse("<yellow><bold>DRAW!</bold></yellow>"),
                                    TextUtil.parse("<gray>Teleporting to lobby in <yellow>" + secondsLeft + "s</yellow>...</gray>"),
                                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(200))
                            );
                            p.showTitle(drawTitle);
                        } else {
                            Title defeatTitle = Title.title(
                                    TextUtil.parse("<red><bold>DEFEAT</bold></red>"),
                                    TextUtil.parse("<gray>Teleporting to lobby in <yellow>" + secondsLeft + "s</yellow>...</gray>"),
                                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(200))
                            );
                            p.showTitle(defeatTitle);
                        }
                    }

                    // Action bar countdown & sound on each second
                    if (isWinner) {
                        p.sendActionBar(TextUtil.parse("<gold><bold>★ VICTORY CELEBRATION ★</bold></gold> <gray>• Lobby in <yellow>" + secondsLeft + "s</yellow></gray>"));
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                        if (spawnFireworks) {
                            spawnCelebrationFirework(p.getLocation());
                        }
                    } else {
                        p.sendActionBar(TextUtil.parse("<gray>Match ended • Lobby in <yellow>" + secondsLeft + "s</yellow></gray>"));
                    }
                }

                secondsLeft--;
            }
        }, 0L, 20L);
    }

    private void spawnCelebrationFirework(@NotNull Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        try {
            Location fwLoc = loc.clone().add((Math.random() - 0.5) * 4.0, 0.5, (Math.random() - 0.5) * 4.0);
            Firework fw = world.spawn(fwLoc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.YELLOW, Color.ORANGE, Color.AQUA, Color.LIME, Color.RED)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build());
            fw.setFireworkMeta(meta);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hook implemented by Ranked, Duel, Team, and Bot matches to process ELO, money, and stats.
     */
    protected abstract void processMatchResults(int winningTeamIndex);

    /**
     * Phase 5: CLEANUP (Restore inventories, teleport to lobby, trigger arena auto-rollback)
     */
    public void cleanup() {
        state.set(MatchState.CLEANUP);

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (matchTickerTask != null) {
            matchTickerTask.cancel();
            matchTickerTask = null;
        }

        if (celebrationTask != null) {
            celebrationTask.cancel();
            celebrationTask = null;
        }

        // Reset world border and invulnerability on all participants
        for (MatchParticipant part : participants.values()) {
            Player p = Bukkit.getPlayer(part.getUuid());
            if (p != null && p.isOnline()) {
                p.setInvulnerable(false);
                try {
                    p.setWorldBorder(null);
                } catch (Throwable ignored) {
                }
            }
        }
        worldBorder = null;

        if (plugin.getBettingManager() != null) {
            plugin.getBettingManager().refundMatch(matchId);
        }

        // Restore all player inventories and lobby positions
        for (Map.Entry<UUID, PlayerRestoreSnapshot> entry : snapshots.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                if (ownInventory) {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setHealth(20.0);
                    p.setFoodLevel(20);
                    p.setFireTicks(0);
                    p.setInvulnerable(false);
                    try {
                        p.setWorldBorder(null);
                    } catch (Throwable ignored) {
                    }
                    Location lobby = entry.getValue().getReturnLocation();
                    if (arena.getTemplate().isTeleportToLobby()
                            || (lobby.getWorld() != null && lobby.getWorld().getName().equalsIgnoreCase("apex_arenas"))) {
                        org.bukkit.World defaultWorld = Bukkit.getWorlds().get(0);
                        if (defaultWorld != null) {
                            lobby = defaultWorld.getSpawnLocation();
                        }
                    }
                    p.teleport(lobby);
                } else {
                    entry.getValue().restore(p, arena.getTemplate().isTeleportToLobby());
                }
                plugin.getMatchManager().unregisterParticipant(p.getUniqueId());
                if (plugin.getLobbyBossBarManager() != null) {
                    plugin.getLobbyBossBarManager().update(p);
                }
            } else {
                if (!ownInventory) {
                    plugin.getMatchManager().savePendingRestore(entry.getKey(), entry.getValue());
                }
                plugin.getMatchManager().unregisterParticipant(entry.getKey());
            }
        }
        snapshots.clear();

        // Trigger tick-budgeted zero-lag rollback on arena instance, unless the template
        // explicitly disables regeneration.
        Runnable onArenaReleased = () -> {
            for (MatchParticipant part : participants.values()) {
                Player p = Bukkit.getPlayer(part.getUuid());
                if (p == null || !p.isOnline()) {
                    plugin.getProfileManager().unloadProfile(part.getUuid());
                } else if (plugin.getLobbyBossBarManager() != null) {
                    plugin.getLobbyBossBarManager().update(p);
                }
            }
            plugin.getMatchManager().unregisterMatch(matchId);
        };
        if (arena.getTemplate().isRegeneration()) {
            int blocksPerTick = (plugin.getConfig() != null)
                    ? plugin.getConfig().getInt("arenas.rollback.blocks-per-tick", 250)
                    : 250;
            arena.startRollback(plugin, blocksPerTick, onArenaReleased);
        } else {
            arena.finishWithoutRollback(plugin, onArenaReleased);
        }
    }

    public void broadcast(@NotNull String miniMessage) {
        if (Bukkit.getServer() == null) {
            return;
        }
        Component parsed = TextUtil.prefixed(miniMessage);
        for (MatchParticipant part : participants.values()) {
            Player p = Bukkit.getPlayer(part.getUuid());
            if (p != null) {
                p.sendMessage(parsed);
            }
        }
    }

    @NotNull
    public UUID getMatchId() {
        return matchId;
    }

    @NotNull
    public MatchState getState() {
        return state.get();
    }

    @NotNull
    public ArenaInstance getArena() {
        return arena;
    }

    @NotNull
    public Kit getKit() {
        return kit;
    }

    public boolean isParticipant(@NotNull UUID uuid) {
        return participants.containsKey(uuid);
    }

    @Nullable
    public MatchParticipant getParticipant(@NotNull UUID uuid) {
        return participants.get(uuid);
    }

    @NotNull
    public List<MatchParticipant> getParticipants() {
        return Collections.unmodifiableList(new ArrayList<>(participants.values()));
    }

    public double getCurrentSafeRadius() {
        return currentSafeRadius;
    }

    @Nullable
    public WorldBorder getWorldBorder() {
        return worldBorder;
    }
}
