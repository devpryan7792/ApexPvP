package com.apexpvp.bot;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.match.Match;
import com.apexpvp.match.MatchState;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

/**
 * Autonomous Combat Controller driving Citizens 2 Practice Bots with difficulty-scaled CPS,
 * strict 3.0-block reach clamping, strafing, and automated healing behavior.
 */
public class BotAIController implements Runnable {

    private final Plugin plugin;
    private final Match match;
    private final NPC npc;
    private final Player targetPlayer;
    private final BotDifficulty difficulty;

    private final Random random = new Random();
    private BukkitTask task;
    private long lastAttackTime = 0;
    private long nextAttackDelayMs = 0;

    public BotAIController(@NotNull Plugin plugin,
                           @NotNull Match match,
                           @NotNull NPC npc,
                           @NotNull Player targetPlayer,
                           @NotNull BotDifficulty difficulty) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.match = Objects.requireNonNull(match, "match cannot be null");
        this.npc = Objects.requireNonNull(npc, "npc cannot be null");
        this.targetPlayer = Objects.requireNonNull(targetPlayer, "targetPlayer cannot be null");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty cannot be null");
    }

    public void start() {
        // Configure responsive pathfinding speed and search range
        npc.getNavigator().getDefaultParameters().range(50.0f);
        npc.getNavigator().getDefaultParameters().baseSpeed(1.15f);

        // Runs every tick (50ms) to ensure smooth tracking and accurate CPS
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        if (match.getState() != MatchState.ACTIVE || !npc.isSpawned() || !targetPlayer.isOnline()) {
            return;
        }

        if (!(npc.getEntity() instanceof LivingEntity botEntity)) {
            return;
        }

        Location botLoc = botEntity.getLocation();
        Location playerLoc = targetPlayer.getLocation();

        if (botLoc.getWorld() == null || playerLoc.getWorld() == null || !botLoc.getWorld().equals(playerLoc.getWorld())) {
            return;
        }

        // 1. Navigation & Pathfinding
        if (!npc.getNavigator().isNavigating() || random.nextInt(10) == 0) {
            npc.getNavigator().setTarget(targetPlayer, true);
        }

        double distance = botLoc.distance(playerLoc);

        // 2. Automated Healing Simulation (below difficulty HP threshold)
        if (botEntity.getHealth() < difficulty.getHealHpThreshold()) {
            executeHealing(botEntity);
        }

        // 3. Strict 3.0 Block Reach Clamping
        if (distance > difficulty.getReachLimit()) {
            return; // Out of reach: strictly refuse hit
        }

        // 4. CPS Timing Engine
        long now = System.currentTimeMillis();
        if (now - lastAttackTime >= nextAttackDelayMs) {
            performAttack(botEntity);
            lastAttackTime = now;

            // Recalculate next interval based on CPS range: delay = 1000 / CPS
            int targetCps = difficulty.getMinCps() + random.nextInt(difficulty.getMaxCps() - difficulty.getMinCps() + 1);
            this.nextAttackDelayMs = 1000L / Math.max(1, targetCps);
        }
    }

    private void performAttack(@NotNull LivingEntity botEntity) {
        // Swing arm animation
        if (botEntity instanceof Player botPlayer) {
            botPlayer.swingMainHand();
        }

        double weaponDamage = 7.0; // Diamond Sword baseline
        ItemStack hand = (botEntity.getEquipment() != null) ? botEntity.getEquipment().getItemInMainHand() : null;
        if (hand != null && hand.getType() != null) {
            String typeName = hand.getType().name();
            if (typeName.endsWith("_SWORD")) {
                switch (hand.getType()) {
                    case NETHERITE_SWORD -> weaponDamage = 8.0;
                    case DIAMOND_SWORD -> weaponDamage = 7.0;
                    case IRON_SWORD -> weaponDamage = 6.0;
                    case STONE_SWORD -> weaponDamage = 5.0;
                    default -> weaponDamage = 4.0;
                }
            } else if (typeName.endsWith("_AXE")) {
                weaponDamage = 9.0;
            }
        }

        // Apply player damage through Bukkit combat pipeline
        targetPlayer.damage(weaponDamage, botEntity);

        // Sprint-reset / W-tap knockback modifier
        if (random.nextDouble() < difficulty.getSprintResetProbability()) {
            targetPlayer.setVelocity(targetPlayer.getVelocity().add(
                botEntity.getLocation().getDirection().setY(0).normalize().multiply(0.35)
            ));
        }
    }

    private void executeHealing(@NotNull LivingEntity botEntity) {
        // Simulate splash health potion or golden apple consumption
        double maxHp = 20.0;
        double healed = Math.min(maxHp, botEntity.getHealth() + 4.0); // Restores 2 hearts
        botEntity.setHealth(healed);

        Location loc = botEntity.getLocation();
        loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.5, 0), 3, 0.3, 0.3, 0.3, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 0.8f, 1.0f);
    }
}
