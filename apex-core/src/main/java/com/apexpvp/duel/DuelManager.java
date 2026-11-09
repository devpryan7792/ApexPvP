package com.apexpvp.duel;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.arena.ArenaTemplate;
import com.apexpvp.economy.EscrowManager;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.types.DuelMatch;
import com.apexpvp.util.TextUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages 1v1 duel invitations, clickable MiniMessage prompts, and escrow stake settlement.
 */
public class DuelManager {

    private final ApexPvPPlugin plugin;
    private final EscrowManager escrowManager;
    // targetUuid -> (challengerUuid -> DuelRequest)
    private final Map<UUID, Map<UUID, DuelRequest>> incomingRequests = new ConcurrentHashMap<>();

    public DuelManager(@NotNull ApexPvPPlugin plugin, @NotNull EscrowManager escrowManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.escrowManager = Objects.requireNonNull(escrowManager, "escrowManager cannot be null");
    }

    public boolean sendChallenge(@NotNull Player challenger,
                                 @NotNull Player target,
                                 @NotNull Kit kit,
                                 @NotNull ArenaTemplate template,
                                 double stake) {
        return sendChallenge(challenger, target, kit, template, stake, false);
    }

    public boolean sendChallenge(@NotNull Player challenger,
                                 @NotNull Player target,
                                 @NotNull Kit kit,
                                 @NotNull ArenaTemplate template,
                                 double stake,
                                 boolean ownInventory) {
        if (challenger.equals(target)) {
            TextUtil.sendPrefixed(challenger, "<red>You cannot duel yourself!");
            return false;
        }

        if (plugin.getMatchManager().isInMatch(challenger) || plugin.getMatchManager().isInMatch(target)) {
            TextUtil.sendPrefixed(challenger, "<red>Either you or the target is currently in a match.");
            return false;
        }

        // Validate stake
        if (stake > 0.0) {
            Economy economy = plugin.getEconomy();
            if (economy == null) {
                TextUtil.sendPrefixed(challenger, "<red>Economy is disabled; stake duels cannot be created.");
                return false;
            }
            if (!economy.has(challenger, stake)) {
                TextUtil.sendPrefixed(challenger, "<red>You lack sufficient funds (<gold><stake></gold> coins required).", Placeholder.parsed("stake", String.valueOf(stake)));
                return false;
            }
            if (!economy.has(target, stake)) {
                TextUtil.sendPrefixed(challenger, "<red><target> does not have sufficient funds for a <gold><stake></gold> coin wager.",
                        Placeholder.parsed("target", target.getName()),
                        Placeholder.parsed("stake", String.valueOf(stake)));
                return false;
            }
        }

        DuelRequest request = new DuelRequest(challenger.getUniqueId(), target.getUniqueId(), kit, template, stake, ownInventory);
        incomingRequests.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>()).put(challenger.getUniqueId(), request);

        String modeInfo = ownInventory ? " <light_purple>[Own Inventory]</light_purple>" : "";
        String stakeInfo = stake > 0 ? " for <gold>" + stake + "</gold> coins" : "";
        TextUtil.sendPrefixed(challenger, "<green>Duel challenge sent to <gold><target></gold> with kit <yellow><kit></yellow>" + modeInfo + stakeInfo + ".",
                Placeholder.parsed("target", target.getName()),
                Placeholder.parsed("kit", kit.getDisplayName()));

        // Send rich clickable invite to target
        String inviteMessage = "<gradient:#FFAA00:#FF5555><bold>DUEL CHALLENGE!</bold></gradient> <gold>" + challenger.getName() +
                "</gold> challenged you with kit <yellow>" + kit.getDisplayName() + "</yellow>" + modeInfo + stakeInfo + " in <aqua>" + template.getDisplayName() + "</aqua>!\n" +
                "<click:run_command:'/duel preview " + challenger.getName() + "'><hover:show_text:'<yellow>Click to view kit & arena details before deciding'><gold><bold>[PREVIEW & ACCEPT]</bold></gold></hover></click>  " +
                "<click:run_command:'/duel accept " + challenger.getName() + "'><hover:show_text:'<green>Click to accept duel immediately'><green><bold>[ACCEPT]</bold></green></hover></click>  " +
                "<click:run_command:'/duel decline " + challenger.getName() + "'><hover:show_text:'<red>Click to decline duel'><red><bold>[DECLINE]</bold></red></hover></click>";

        TextUtil.sendPrefixed(target, inviteMessage);
        return true;
    }

    public boolean acceptChallenge(@NotNull Player target, @NotNull Player challenger) {
        Map<UUID, DuelRequest> requests = incomingRequests.get(target.getUniqueId());
        if (requests == null) {
            TextUtil.sendPrefixed(target, "<red>You have no pending duel requests from <gold><challenger></gold>.", Placeholder.parsed("challenger", challenger.getName()));
            return false;
        }

        DuelRequest request = requests.remove(challenger.getUniqueId());
        if (request == null || request.isExpired()) {
            TextUtil.sendPrefixed(target, "<red>That duel challenge has expired or does not exist.");
            return false;
        }

        if (plugin.getMatchManager().isInMatch(challenger) || plugin.getMatchManager().isInMatch(target)) {
            TextUtil.sendPrefixed(target, "<red>Either you or the challenger is already in a match.");
            return false;
        }

        ArenaInstance arenaInstance = plugin.getArenaManager().getAvailableInstance(request.getTemplate());
        if (arenaInstance == null) {
            TextUtil.sendPrefixed(target, "<yellow>No arenas are currently available. Auto-scaling instance, please try in a moment...");
            plugin.getArenaManager().requestScaling(request.getTemplate().getId());
            return false;
        }

        DuelMatch match = new DuelMatch(plugin, arenaInstance, request.getKit(), challenger, target, request.getStake());
        match.setOwnInventory(request.isOwnInventory());

        // Lock Escrow Stakes before match initialization using the match's exact ID
        if (request.getStake() > 0.0) {
            if (!escrowManager.lockStake(match.getMatchId(), challenger, target, request.getStake())) {
                TextUtil.sendPrefixed(target, "<red>Escrow stake lock failed due to insufficient funds.");
                TextUtil.sendPrefixed(challenger, "<red>Escrow stake lock failed due to insufficient funds.");
                return false;
            }
        }

        plugin.getMatchManager().registerMatch(match);
        match.start();

        return true;
    }

    public boolean declineChallenge(@NotNull Player target, @NotNull Player challenger) {
        Map<UUID, DuelRequest> requests = incomingRequests.get(target.getUniqueId());
        if (requests != null && requests.remove(challenger.getUniqueId()) != null) {
            TextUtil.sendPrefixed(target, "<yellow>You declined the duel challenge from <gold><challenger></gold>.", Placeholder.parsed("challenger", challenger.getName()));
            TextUtil.sendPrefixed(challenger, "<yellow><target> declined your duel challenge.", Placeholder.parsed("target", target.getName()));
            return true;
        }
        TextUtil.sendPrefixed(target, "<red>No pending request from <gold><challenger></gold>.", Placeholder.parsed("challenger", challenger.getName()));
        return false;
    }

    public void purgePlayer(@NotNull UUID uuid) {
        incomingRequests.remove(uuid);
        for (Map<UUID, DuelRequest> map : incomingRequests.values()) {
            map.remove(uuid);
        }
    }

    @org.jetbrains.annotations.Nullable
    public DuelRequest getPendingRequest(@NotNull Player target, @NotNull Player challenger) {
        Map<UUID, DuelRequest> requests = incomingRequests.get(target.getUniqueId());
        if (requests == null) return null;
        DuelRequest req = requests.get(challenger.getUniqueId());
        if (req != null && !req.isExpired()) {
            return req;
        }
        return null;
    }
}
