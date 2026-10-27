package com.apexpvp.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Velocity Proxy Bridge for ApexPvP network synchronization and load-balanced routing.
 */
@Plugin(
    id = "apex-velocity",
    name = "ApexPvP Velocity",
    version = "1.0.0-SNAPSHOT",
    description = "ApexPvP Velocity Proxy Bridge for network-wide queueing and rank sync",
    authors = {"ApexPvP Team"}
)
public class ApexVelocityPlugin {

    public static final MinecraftChannelIdentifier APEX_CHANNEL = MinecraftChannelIdentifier.from("apex:sync");

    private final ProxyServer proxy;
    private final Logger logger;
    private final ServerLoadRegistry serverLoadRegistry;

    @Inject
    public ApexVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.serverLoadRegistry = new ServerLoadRegistry(15000L); // 15s stale timeout
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(APEX_CHANNEL);
        logger.info("ApexPvP Velocity Bridge registered channel [{}] successfully.", APEX_CHANNEL.getId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(APEX_CHANNEL)) {
            return;
        }

        @SuppressWarnings("UnstableApiUsage")
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subChannel = in.readUTF();

        if ("HEARTBEAT".equalsIgnoreCase(subChannel)) {
            String serverId = in.readUTF();
            int activeMatches = in.readInt();
            int totalArenas = in.readInt();
            int availableArenas = in.readInt();

            serverLoadRegistry.updateHeartbeat(serverId, activeMatches, totalArenas, availableArenas);
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    @NotNull
    public ProxyServer getProxy() {
        return proxy;
    }

    @NotNull
    public Logger getLogger() {
        return logger;
    }

    @NotNull
    public ServerLoadRegistry getServerLoadRegistry() {
        return serverLoadRegistry;
    }
}
