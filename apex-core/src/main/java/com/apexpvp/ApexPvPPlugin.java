package com.apexpvp;

import com.apexpvp.arena.ArenaManager;
import com.apexpvp.database.DatabaseManager;
import com.apexpvp.kit.KitManager;
import com.apexpvp.match.MatchManager;
import com.apexpvp.match.listener.MatchListener;
import com.apexpvp.profile.ProfileManager;
import com.apexpvp.profile.listener.ProfileListener;
import com.apexpvp.util.TextUtil;
import com.apexpvp.command.ApexCommand;
import com.apexpvp.command.BetCommand;
import com.apexpvp.command.DuelCommand;
import com.apexpvp.command.KitCommand;
import com.apexpvp.command.LeaderboardCommand;
import com.apexpvp.command.PartyCommand;
import com.apexpvp.command.PracticeCommand;
import com.apexpvp.command.QueueCommand;
import com.apexpvp.command.RankupCommand;
import com.apexpvp.command.StatsCommand;
import com.apexpvp.duel.DuelManager;
import com.apexpvp.economy.BettingManager;
import com.apexpvp.economy.EscrowManager;
import com.apexpvp.integration.papi.ApexExpansion;
import com.apexpvp.integration.proxy.VelocityReporter;
import com.apexpvp.leaderboard.LeaderboardManager;
import com.apexpvp.party.PartyManager;
import com.apexpvp.queue.QueueManager;
import com.apexpvp.ui.bossbar.LobbyBossBarManager;
import com.apexpvp.ui.gui.KitGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;

/**
 * ApexPvP Core Plugin targeting Paper 26.3 (Java 21+).
 */
public class ApexPvPPlugin extends JavaPlugin {

    private static ApexPvPPlugin instance;

    private DatabaseManager databaseManager;
    private ProfileManager profileManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private MatchManager matchManager;
    private EscrowManager escrowManager;
    private QueueManager queueManager;
    private DuelManager duelManager;
    private PartyManager partyManager;
    private VelocityReporter velocityReporter;
    private Economy economy;
    private LeaderboardManager leaderboardManager;
    private BettingManager bettingManager;
    private KitGUI kitGUI;
    private LobbyBossBarManager lobbyBossBarManager;

    private boolean citizensAvailable;
    private boolean faweAvailable;
    private boolean placeholderApiAvailable;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Save default configuration files
        saveDefaultConfig();
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        // Load custom prefix from messages if present
        String prefix = getConfig().getString("prefix");
        if (prefix != null) {
            TextUtil.setPrefix(prefix);
        }

        // 2. Initialize Database Layer
        try {
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "CRITICAL: Failed to initialize ApexPvP database. Disabling plugin...", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize Profile & Player Data Manager
        this.profileManager = new ProfileManager(this, databaseManager);
        getServer().getPluginManager().registerEvents(new ProfileListener(profileManager), this);

        // 4. Initialize Multi-Arena Engine
        this.arenaManager = new ArenaManager(this);
        this.arenaManager.initialize();

        // 5. Initialize Multi-Kit Engine and Kit Selection GUI
        this.kitManager = new KitManager(this);
        this.kitManager.initialize();
        this.kitGUI = new KitGUI(this);
        getServer().getPluginManager().registerEvents(kitGUI, this);

        // 6. Initialize Lobby BossBar Manager
        this.lobbyBossBarManager = new LobbyBossBarManager(this);
        this.lobbyBossBarManager.start();

        // 7. Initialize Match Lifecycle Engine
        this.matchManager = new MatchManager(this);
        getServer().getPluginManager().registerEvents(new MatchListener(matchManager), this);

        // 8. Initialize Escrow, Queue, Duels, and Party Systems
        this.escrowManager = new EscrowManager(this);
        this.queueManager = new QueueManager(this);
        this.queueManager.start();
        this.duelManager = new DuelManager(this, escrowManager);
        this.partyManager = new PartyManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        this.bettingManager = new BettingManager(this);

        // 9. Register Commands
        registerCommands();

        // 10. Velocity Proxy Heartbeat Bridge
        if (getConfig().getBoolean("proxy.velocity-support", false)) {
            this.velocityReporter = new VelocityReporter(this);
            this.velocityReporter.start();
            getLogger().info("Started Velocity proxy capacity reporting on channel " + VelocityReporter.CHANNEL);
        }

        // 11. Inspect External Integrations (Strict Citizens Requirement)
        verifyDependencies();

        getLogger().info("=================================================");
        getLogger().info(" ApexPvP Core v" + getPluginMeta().getVersion() + " initialized successfully!");
        getLogger().info(" Storage: " + databaseManager.getDatabaseType().getFriendlyName());
        getLogger().info(" Arenas: " + arenaManager.getTotalInstanceCount() + " instances across " + arenaManager.getAllTemplates().size() + " templates");
        getLogger().info(" Kits: " + kitManager.getAllKits().size() + " loaded kits");
        getLogger().info(" Citizens: " + (citizensAvailable ? "ENABLED" : "MISSING (Required for Practice)"));
        getLogger().info(" Vault: " + (economy != null ? "HOOKED" : "DISABLED"));
        getLogger().info(" FAWE: " + (faweAvailable ? "HOOKED" : "DISABLED"));
        getLogger().info(" PlaceholderAPI: " + (placeholderApiAvailable ? "HOOKED" : "DISABLED"));
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Initiating ApexPvP graceful shutdown...");

        // 0. Stop Lobby BossBar Manager
        if (lobbyBossBarManager != null) {
            lobbyBossBarManager.stop();
        }

        // 1. Stop Velocity reporter
        if (velocityReporter != null) {
            velocityReporter.stop();
        }

        // 2. Stop Queue ticker
        if (queueManager != null) {
            queueManager.stop();
        }

        // 3. Abort all active matches and restore player inventories
        if (matchManager != null) {
            matchManager.shutdown();
        }

        // 4. Refund active escrows and spectator bets
        if (bettingManager != null) {
            bettingManager.shutdown();
        }
        if (escrowManager != null) {
            escrowManager.shutdown();
        }

        // 5. Flush all pending dirty player profiles synchronously
        if (profileManager != null) {
            profileManager.saveAllSync();
        }

        // 6. Teardown Arena Engine and clean chunk tickets
        if (arenaManager != null) {
            arenaManager.shutdown();
        }

        // 7. Terminate Database Pool & Virtual Threads
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("ApexPvP Core successfully disabled.");
        instance = null;
    }

    private void verifyDependencies() {
        // Vault Economy
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
                getLogger().info("Hooked into Vault Economy (" + economy.getName() + ").");
            }
        }

        // Citizens 2 (Strict bot dependency)
        this.citizensAvailable = getServer().getPluginManager().isPluginEnabled("Citizens");
        if (!citizensAvailable) {
            getLogger().warning("Citizens 2 is not detected! Practice Bot module will remain locked until Citizens is installed.");
        }

        // FAWE
        this.faweAvailable = getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit");

        // PlaceholderAPI
        this.placeholderApiAvailable = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (placeholderApiAvailable) {
            new ApexExpansion(this).register();
            getLogger().info("Registered ApexPvP PlaceholderAPI expansion.");
        }
    }

    private void registerCommands() {
        if (getCommand("apex") != null) {
            ApexCommand apexCmd = new ApexCommand(this);
            getCommand("apex").setExecutor(apexCmd);
            getCommand("apex").setTabCompleter(apexCmd);
        }
        if (getCommand("duel") != null) {
            DuelCommand duelCmd = new DuelCommand(this);
            getCommand("duel").setExecutor(duelCmd);
            getCommand("duel").setTabCompleter(duelCmd);
        }
        if (getCommand("queue") != null) {
            QueueCommand queueCmd = new QueueCommand(this);
            getCommand("queue").setExecutor(queueCmd);
            getCommand("queue").setTabCompleter(queueCmd);
        }
        if (getCommand("leavequeue") != null) {
            getCommand("leavequeue").setExecutor(new QueueCommand(this));
        }
        if (getCommand("party") != null) {
            PartyCommand partyCmd = new PartyCommand(this);
            getCommand("party").setExecutor(partyCmd);
            getCommand("party").setTabCompleter(partyCmd);
        }
        if (getCommand("practice") != null) {
            PracticeCommand practiceCmd = new PracticeCommand(this);
            getCommand("practice").setExecutor(practiceCmd);
            getCommand("practice").setTabCompleter(practiceCmd);
        }
        if (getCommand("rankup") != null) {
            getCommand("rankup").setExecutor(new RankupCommand(this));
        }
        if (getCommand("stats") != null) {
            StatsCommand statsCmd = new StatsCommand(this);
            getCommand("stats").setExecutor(statsCmd);
            getCommand("stats").setTabCompleter(statsCmd);
        }
        if (getCommand("top") != null) {
            LeaderboardCommand topCmd = new LeaderboardCommand(this);
            getCommand("top").setExecutor(topCmd);
            getCommand("top").setTabCompleter(topCmd);
        }
        if (getCommand("bet") != null) {
            BetCommand betCmd = new BetCommand(this);
            getCommand("bet").setExecutor(betCmd);
            getCommand("bet").setTabCompleter(betCmd);
        }
        if (getCommand("kits") != null) {
            KitCommand kitCmd = new KitCommand(this, kitGUI);
            getCommand("kits").setExecutor(kitCmd);
            getCommand("kits").setTabCompleter(kitCmd);
        }
    }

    @NotNull
    public static ApexPvPPlugin getInstance() {
        return instance;
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @NotNull
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    @NotNull
    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    @NotNull
    public KitManager getKitManager() {
        return kitManager;
    }

    @NotNull
    public MatchManager getMatchManager() {
        return matchManager;
    }

    @NotNull
    public EscrowManager getEscrowManager() {
        return escrowManager;
    }

    @NotNull
    public QueueManager getQueueManager() {
        return queueManager;
    }

    @NotNull
    public DuelManager getDuelManager() {
        return duelManager;
    }

    @NotNull
    public PartyManager getPartyManager() {
        return partyManager;
    }

    @Nullable
    public VelocityReporter getVelocityReporter() {
        return velocityReporter;
    }

    @Nullable
    public Economy getEconomy() {
        return economy;
    }

    public boolean isCitizensAvailable() {
        return citizensAvailable;
    }

    public boolean isFaweAvailable() {
        return faweAvailable;
    }

    public boolean isPlaceholderApiAvailable() {
        return placeholderApiAvailable;
    }

    @Nullable
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    @Nullable
    public BettingManager getBettingManager() {
        return bettingManager;
    }

    @NotNull
    public KitGUI getKitGUI() {
        return kitGUI;
    }

    @NotNull
    public LobbyBossBarManager getLobbyBossBarManager() {
        return lobbyBossBarManager;
    }
}
