package com.apexpvp.duel;

import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.kit.Kit;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates an active 1v1 duel challenge expiring after 60 seconds.
 */
public class DuelRequest {

    private final UUID challengerId;
    private final UUID targetId;
    private final Kit kit;
    private final ArenaTemplate template;
    private final double stake;
    private final long createdAt;

    public DuelRequest(@NotNull UUID challengerId,
                       @NotNull UUID targetId,
                       @NotNull Kit kit,
                       @NotNull ArenaTemplate template,
                       double stake) {
        this.challengerId = Objects.requireNonNull(challengerId, "challengerId cannot be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId cannot be null");
        this.kit = Objects.requireNonNull(kit, "kit cannot be null");
        this.template = Objects.requireNonNull(template, "template cannot be null");
        this.stake = Math.max(0.0, stake);
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 60000L; // 60 seconds
    }

    @NotNull
    public UUID getChallengerId() {
        return challengerId;
    }

    @NotNull
    public UUID getTargetId() {
        return targetId;
    }

    @NotNull
    public Kit getKit() {
        return kit;
    }

    @NotNull
    public ArenaTemplate getTemplate() {
        return template;
    }

    public double getStake() {
        return stake;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
