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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
    private double currentSafeRadius = -1.0;
    private boolean warningAnnounced = false;
    private boolean shrinkAnnounced = false;

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

    /**
     * Phase 1: PREPARING
     * Acquires arena, takes player snapshots, teleports to spawns, applies kit, and freezes.
     */
    public void start() {
        if (!arena.acquire(plugin)) {
            broadcast("<red>Failed to allocate arena instance. Match aborted.");
            cleanup();
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

            // 2. Teleport to designated team spawn
            Location spawn = (participant.getTeamIndex() == 0) ? arena.getSpawnA() : arena.getSpawnB();
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

            // 3. Apply Kit with custom hotbar layout
            PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
            String customLayout = (profile != null) ? profile.getCustomHotbar(kit.getId()) : null;
            kit.apply(player, customLayout);
        }

        // Transition to COUNTDOWN
        enterCountdown();
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
        boolean borderEnabled = plugin.getConfig().getBoolean("match.border.enabled", true);
        int shrinkDelaySec = plugin.getConfig().getInt("match.border.shrink-delay-seconds", 180);
        int shrinkDurationSec = plugin.getConfig().getInt("match.border.shrink-duration-seconds", 60);
        double minRadius = plugin.getConfig().getDouble("match.border.minimum-radius", 6.0);
        double damagePerSec = plugin.getConfig().getDouble("match.border.damage-per-second", 4.0);
        int maxDurationSec = plugin.getConfig().getInt("match.max-match-duration-seconds", 600);

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

                    if (dist > currentSafeRadius) {
                        p.damage(damagePerSec);
                        p.sendActionBar(TextUtil.parse("<red><bold>OUTSIDE SAFE ZONE!</bold></red> <gray>(-" + (int)damagePerSec + " HP)</gray>"));
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
        broadcast("<red>" + player.getName() + " disconnected and forfeited the match.");

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
     * Phase 4: ENDING (Celebrate victor, process payouts, 3-second delay)
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

        // 3-second victory celebration delay before cleanup
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 60L);
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

        if (plugin.getBettingManager() != null) {
            plugin.getBettingManager().refundMatch(matchId);
        }

        // Restore all player inventories and lobby positions
        for (Map.Entry<UUID, PlayerRestoreSnapshot> entry : snapshots.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                entry.getValue().restore(p);
                plugin.getMatchManager().unregisterParticipant(p.getUniqueId());
                if (plugin.getLobbyBossBarManager() != null) {
                    plugin.getLobbyBossBarManager().update(p);
                }
            } else {
                plugin.getMatchManager().savePendingRestore(entry.getKey(), entry.getValue());
                plugin.getMatchManager().unregisterParticipant(entry.getKey());
            }
        }
        snapshots.clear();

        // Trigger tick-budgeted zero-lag rollback on arena instance
        int blocksPerTick = plugin.getConfig().getInt("arenas.rollback.blocks-per-tick", 250);
        arena.startRollback(plugin, blocksPerTick, () -> {
            for (MatchParticipant part : participants.values()) {
                Player p = Bukkit.getPlayer(part.getUuid());
                if (p == null || !p.isOnline()) {
                    plugin.getProfileManager().unloadProfile(part.getUuid());
                } else if (plugin.getLobbyBossBarManager() != null) {
                    plugin.getLobbyBossBarManager().update(p);
                }
            }
            plugin.getMatchManager().unregisterMatch(matchId);
        });
    }

    public void broadcast(@NotNull String miniMessage) {
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
}
