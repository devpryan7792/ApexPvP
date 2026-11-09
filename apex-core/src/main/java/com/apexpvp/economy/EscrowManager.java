package com.apexpvp.economy;

import com.apexpvp.ApexPvPPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Atomic Escrow Transaction Manager.
 * Safely holds wagered funds during duels and guarantees automatic refunds on match aborts or server crashes.
 */
public class EscrowManager {

    public record EscrowRecord(
        UUID matchId,
        UUID player1Uuid,
        UUID player2Uuid,
        double stakePerPlayer,
        boolean isSimulated,
        long timestamp
    ) {
        public EscrowRecord(UUID matchId, UUID player1Uuid, UUID player2Uuid, double stakePerPlayer, long timestamp) {
            this(matchId, player1Uuid, player2Uuid, stakePerPlayer, false, timestamp);
        }
    }

    private final ApexPvPPlugin plugin;
    private final Map<UUID, EscrowRecord> activeEscrows = new ConcurrentHashMap<>();

    public EscrowManager(@NotNull ApexPvPPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Atomically locks stakes from both players prior to match start.
     *
     * @return true if both players had sufficient funds and were debited successfully.
     */
    public synchronized boolean lockStake(@NotNull UUID matchId,
                                          @NotNull Player player1,
                                          @NotNull Player player2,
                                          double stakeAmount) {
        if (stakeAmount <= 0.0) {
            return true; // Free match
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Cannot lock escrow stake: Vault Economy is unavailable.");
            return false;
        }

        // Verify balances
        if (!economy.has(player1, stakeAmount) || !economy.has(player2, stakeAmount)) {
            return false;
        }

        // Debit player 1
        var res1 = economy.withdrawPlayer(player1, stakeAmount);
        if (!res1.transactionSuccess()) {
            return false;
        }

        // Debit player 2
        var res2 = economy.withdrawPlayer(player2, stakeAmount);
        if (!res2.transactionSuccess()) {
            // Rollback player 1
            economy.depositPlayer(player1, stakeAmount);
            return false;
        }

        activeEscrows.put(matchId, new EscrowRecord(matchId, player1.getUniqueId(), player2.getUniqueId(), stakeAmount, false, System.currentTimeMillis()));
        plugin.getLogger().info("Locked escrow stake of " + stakeAmount + " coins for match " + matchId);
        return true;
    }

    /**
     * Locks escrow stake for a simulated match where player2 is a simulated AI bot.
     */
    public synchronized boolean lockSimulatedStake(@NotNull UUID matchId,
                                                   @NotNull Player player,
                                                   @NotNull UUID botUuid,
                                                   double stakeAmount) {
        if (stakeAmount <= 0.0) {
            return true;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Cannot lock escrow stake: Vault Economy is unavailable.");
            return false;
        }

        if (!economy.has(player, stakeAmount)) {
            return false;
        }

        var res = economy.withdrawPlayer(player, stakeAmount);
        if (!res.transactionSuccess()) {
            return false;
        }

        activeEscrows.put(matchId, new EscrowRecord(matchId, player.getUniqueId(), botUuid, stakeAmount, true, System.currentTimeMillis()));
        plugin.getLogger().info("Locked simulated escrow stake of " + stakeAmount + " coins for match " + matchId);
        return true;
    }

    /**
     * Distributes the pot to the victor minus server commission.
     *
     * <p>A real duel has two equal contributions, so the pot is
     * {@code stakePerPlayer * 2}. A simulated duel only debits the human player
     * once, so the pot is exactly {@code stakePerPlayer}; paying 2x there would
     * mint coins out of nothing.</p>
     */
    public synchronized void distributePayout(@NotNull UUID matchId, @NotNull org.bukkit.OfflinePlayer winner, double taxPercent) {
        EscrowRecord record = activeEscrows.remove(matchId);
        if (record == null) return;

        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        double totalPot = record.isSimulated()
                ? record.stakePerPlayer()
                : record.stakePerPlayer() * 2.0;
        double tax = totalPot * (Math.max(0.0, taxPercent) / 100.0);
        double netPayout = totalPot - tax;

        economy.depositPlayer(winner, netPayout);
        plugin.getLogger().info("Paid out " + netPayout + " coins (pot: " + totalPot + ", tax: " + tax + ") to winner " + (winner.getName() != null ? winner.getName() : winner.getUniqueId()) + " for match " + matchId);
    }

    /**
     * Forfeits or consumes escrow funds without payout (e.g. house win in simulated duel).
     */
    public synchronized void consumeEscrow(@NotNull UUID matchId) {
        activeEscrows.remove(matchId);
    }

    /**
     * Refunds both participants in full if a match aborts or ends in a draw.
     */
    public synchronized void refund(@NotNull UUID matchId) {
        EscrowRecord record = activeEscrows.remove(matchId);
        if (record == null) return;

        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        economy.depositPlayer(plugin.getServer().getOfflinePlayer(record.player1Uuid()), record.stakePerPlayer());
        if (!record.isSimulated()) {
            economy.depositPlayer(plugin.getServer().getOfflinePlayer(record.player2Uuid()), record.stakePerPlayer());
        }
        plugin.getLogger().info("Refunded escrow stake of " + record.stakePerPlayer() + " coins for match " + matchId);
    }

    /**
     * Emergency refund of all locked funds on server shutdown.
     */
    public synchronized void shutdown() {
        if (activeEscrows.isEmpty()) return;
        plugin.getLogger().info("Refunding " + activeEscrows.size() + " active escrows on shutdown...");
        for (UUID matchId : new java.util.ArrayList<>(activeEscrows.keySet())) {
            try {
                refund(matchId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refund escrow for match " + matchId, e);
            }
        }
        activeEscrows.clear();
    }
}
