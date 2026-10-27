package com.apexpvp.match.types;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.arena.ArenaInstance;
import com.apexpvp.kit.Kit;
import com.apexpvp.match.Match;
import com.apexpvp.bot.BotAIController;
import com.apexpvp.bot.BotDifficulty;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Single-player PvE practice match fighting a Citizens 2 combat NPC.
 */
public class BotMatch extends Match {

    private final UUID playerUuid;
    private final String difficulty;
    private NPC practiceBot;
    private BotAIController botAI;

    public BotMatch(@NotNull ApexPvPPlugin plugin,
                    @NotNull ArenaInstance arena,
                    @NotNull Kit kit,
                    @NotNull Player player,
                    @NotNull String difficulty) {
        super(plugin, arena, kit);
        this.playerUuid = player.getUniqueId();
        this.difficulty = difficulty.toUpperCase();

        addParticipant(player, 0); // Player on Team 0
    }

    @Override
    public void start() {
        super.start();

        // Spawn Citizens Practice Bot on Team 1 (Spawn B)
        if (plugin.isCitizensAvailable()) {
            this.practiceBot = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Bot_" + difficulty);
            practiceBot.setProtected(false); // Make bot vulnerable to player attacks!
            practiceBot.spawn(arena.getSpawnB());

            // Equip bot with Kit gear and register combat participant
            if (practiceBot.getEntity() instanceof Player botPlayer) {
                botPlayer.setGameMode(GameMode.SURVIVAL);
                kit.apply(botPlayer, null);
                addParticipant(botPlayer, 1);
                plugin.getMatchManager().registerParticipant(botPlayer.getUniqueId(), this);
                plugin.getMatchManager().registerParticipant(practiceBot.getUniqueId(), this);
            }
        }
    }

    @Override
    protected void enterActive() {
        super.enterActive();

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && practiceBot != null && practiceBot.isSpawned()) {
            BotDifficulty diff = BotDifficulty.fromString(difficulty);
            this.botAI = new BotAIController(plugin, this, practiceBot, player, diff);
            this.botAI.start();
        }
    }

    @Override
    protected void processMatchResults(int winningTeamIndex) {
        if (winningTeamIndex == 0) {
            broadcast("<gradient:#00FFAA:#FFFF55><bold>PRACTICE WON!</bold></gradient> You defeated the <gold>" + difficulty + "</gold> practice bot!");
        } else {
            broadcast("<gradient:#FF5555:#AA0000><bold>PRACTICE LOST!</bold></gradient> You were defeated by the <gold>" + difficulty + "</gold> practice bot.");
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();

        if (botAI != null) {
            botAI.stop();
            botAI = null;
        }

        // Destroy practice bot NPC safely
        if (practiceBot != null) {
            if (practiceBot.getEntity() != null) {
                plugin.getMatchManager().unregisterParticipant(practiceBot.getEntity().getUniqueId());
            }
            plugin.getMatchManager().unregisterParticipant(practiceBot.getUniqueId());
            practiceBot.destroy();
            practiceBot = null;
        }
    }

    @NotNull
    public String getDifficulty() {
        return difficulty;
    }

    public NPC getPracticeBot() {
        return practiceBot;
    }
}
