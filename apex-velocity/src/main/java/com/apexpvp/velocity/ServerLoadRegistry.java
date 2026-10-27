package com.apexpvp.velocity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry tracking Paper backend server load, capacity, and optimal routing targets.
 */
public class ServerLoadRegistry {

    private final Map<String, ServerLoadInfo> servers = new ConcurrentHashMap<>();
    private final long staleTimeoutMillis;

    public ServerLoadRegistry(long staleTimeoutMillis) {
        this.staleTimeoutMillis = staleTimeoutMillis;
    }

    public void updateHeartbeat(@NotNull String serverId,
                                int activeMatches,
                                int totalArenas,
                                int availableArenas) {
        servers.put(serverId.toLowerCase(), new ServerLoadInfo(
            serverId, activeMatches, totalArenas, availableArenas, System.currentTimeMillis()
        ));
    }

    /**
     * Determines the most optimal server: prioritizing available arenas, then least active matches.
     */
    @Nullable
    public ServerLoadInfo getBestServer() {
        long now = System.currentTimeMillis();
        // Purge stale servers
        servers.entrySet().removeIf(e -> e.getValue().isStale(now, staleTimeoutMillis));

        return servers.values().stream()
            .max(Comparator
                .comparingInt(ServerLoadInfo::availableArenas)
                .thenComparing(Comparator.comparingInt(ServerLoadInfo::activeMatches).reversed()))
            .orElse(null);
    }

    public int getRegisteredServerCount() {
        return servers.size();
    }

    @Nullable
    public ServerLoadInfo getServer(@NotNull String serverId) {
        return servers.get(serverId.toLowerCase());
    }

    public void clear() {
        servers.clear();
    }
}
