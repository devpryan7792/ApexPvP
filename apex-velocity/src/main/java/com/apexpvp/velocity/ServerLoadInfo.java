package com.apexpvp.velocity;

/**
 * Snapshot of a backend Paper server's capacity and active match load.
 */
public record ServerLoadInfo(
    String serverId,
    int activeMatches,
    int totalArenas,
    int availableArenas,
    long lastHeartbeat
) {
    public boolean isStale(long now, long timeoutMillis) {
        return now - lastHeartbeat > timeoutMillis;
    }
}
