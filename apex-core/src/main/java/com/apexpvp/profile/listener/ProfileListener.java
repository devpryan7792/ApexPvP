package com.apexpvp.profile.listener;

import com.apexpvp.profile.PlayerProfile;
import com.apexpvp.profile.ProfileManager;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles asynchronous pre-login profile population and disconnect cleanup.
 */
public class ProfileListener implements Listener {

    private final ProfileManager profileManager;

    public ProfileListener(@NotNull ProfileManager profileManager) {
        this.profileManager = Objects.requireNonNull(profileManager, "profileManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            // Block the pre-login thread (which is already async) until profile is loaded or timeout
            profileManager.loadProfile(uuid, name).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // In case of timeout or failure, allow login with memory fallback or disallow if strict
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                Component.text("Failed to load your PvP profile. Please reconnect in a moment.")
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        com.apexpvp.ApexPvPPlugin plugin = com.apexpvp.ApexPvPPlugin.getInstance();
        if (plugin != null) {
            if (plugin.getQueueManager() != null) {
                plugin.getQueueManager().leaveQueue(player);
            }
            if (plugin.getDuelManager() != null) {
                plugin.getDuelManager().purgePlayer(uuid);
            }
            if (plugin.getPartyManager() != null) {
                plugin.getPartyManager().leaveParty(player);
            }
            if (plugin.getMatchManager() != null && plugin.getMatchManager().isInMatch(player)) {
                return; // Let match conclusion process stats and ELO before unloading
            }
        }
        profileManager.unloadProfile(uuid);
    }
}
