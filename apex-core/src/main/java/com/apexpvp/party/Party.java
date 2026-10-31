package com.apexpvp.party;

import com.apexpvp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight Party entity grouping players for Team PvP and Party Split scrimmages.
 */
public class Party {

    private volatile UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final int maxMembers;

    public Party(@NotNull Player leader, int maxMembers) {
        Objects.requireNonNull(leader, "leader cannot be null");
        this.leader = leader.getUniqueId();
        this.maxMembers = Math.max(2, maxMembers);
        this.members.add(this.leader);
    }

    @NotNull
    public UUID getLeader() {
        return leader;
    }

    public void setLeader(@NotNull UUID newLeader) {
        this.leader = Objects.requireNonNull(newLeader, "newLeader cannot be null");
    }

    public boolean isLeader(@NotNull UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean addMember(@NotNull UUID uuid) {
        if (members.size() >= maxMembers) {
            return false;
        }
        return members.add(uuid);
    }

    public boolean removeMember(@NotNull UUID uuid) {
        return members.remove(uuid);
    }

    public boolean contains(@NotNull UUID uuid) {
        return members.contains(uuid);
    }

    public int getSize() {
        return members.size();
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    @NotNull
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void broadcast(@NotNull String miniMessage) {
        if (Bukkit.getServer() == null) return;
        for (UUID uuid : members) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                TextUtil.sendPrefixed(p, miniMessage);
            }
        }
    }
}
