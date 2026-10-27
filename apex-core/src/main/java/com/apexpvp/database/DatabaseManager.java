package com.apexpvp.database;

import com.apexpvp.database.schema.DatabaseSchema;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * High-performance database connection manager utilizing HikariCP with
 * CompletableFuture asynchronous I/O and support for SQLite, PostgreSQL, MariaDB, and MySQL.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final DatabaseType databaseType;
    private HikariDataSource dataSource;
    private final ExecutorService asyncExecutor;

    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T map(@NotNull ResultSet rs) throws SQLException;
    }

    public DatabaseManager(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        FileConfiguration config = plugin.getConfig();
        String typeString = config.getString("storage.type", "SQLITE");
        this.databaseType = DatabaseType.fromString(typeString);

        // Java 21 Virtual Thread Per Task Executor for high-concurrency database queries
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void initialize() throws SQLException {
        plugin.getLogger().info("Initializing database connection pool using " + databaseType.getFriendlyName() + " backend...");

        HikariConfig hikariConfig = new HikariConfig();
        FileConfiguration config = plugin.getConfig();

        if (databaseType == DatabaseType.SQLITE) {
            setupSqlite(hikariConfig, config);
        } else {
            setupRemote(hikariConfig, config);
        }

        hikariConfig.setPoolName("ApexPvP-Pool");
        this.dataSource = new HikariDataSource(hikariConfig);

        // Initialize schema
        createTables();
        plugin.getLogger().info("Database initialization completed successfully.");
    }

    private void setupSqlite(@NotNull HikariConfig config, @NotNull FileConfiguration fileConfig) {
        String fileName = fileConfig.getString("storage.sqlite.file", "apex_pvp.db");
        File dbFile = new File(plugin.getDataFolder(), fileName);

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data directory: " + plugin.getDataFolder());
        }

        config.setDriverClassName(databaseType.getDriverClassName());
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite locks during concurrent writes, keep single connection
        config.setMinimumIdle(1);
        config.setConnectionTimeout(fileConfig.getLong("storage.sqlite.busy-timeout-ms", 5000));
        config.addDataSourceProperty("journal_mode", fileConfig.getString("storage.sqlite.journal-mode", "WAL"));
        config.addDataSourceProperty("synchronous", fileConfig.getString("storage.sqlite.synchronous", "NORMAL"));
    }

    private void setupRemote(@NotNull HikariConfig config, @NotNull FileConfiguration fileConfig) {
        String host = fileConfig.getString("storage.remote.host", "127.0.0.1");
        int port = fileConfig.getInt("storage.remote.port", databaseType.getDefaultPort());
        String database = fileConfig.getString("storage.remote.database", "apexpvp");
        String username = fileConfig.getString("storage.remote.username", "apex_user");
        String password = fileConfig.getString("storage.remote.password", "");
        boolean ssl = fileConfig.getBoolean("storage.remote.ssl", false);

        config.setDriverClassName(databaseType.getDriverClassName());

        String jdbcUrl = switch (databaseType) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database + "?ssl=" + ssl;
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + ssl;
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true";
            case SQLITE -> throw new IllegalStateException("SQLite setup should use setupSqlite()");
        };

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // Pool tuning
        config.setMaximumPoolSize(fileConfig.getInt("storage.remote.pool.maximum-pool-size", 10));
        config.setMinimumIdle(fileConfig.getInt("storage.remote.pool.minimum-idle", 2));
        config.setConnectionTimeout(fileConfig.getLong("storage.remote.pool.connection-timeout-ms", 10000));
        config.setMaxLifetime(fileConfig.getLong("storage.remote.pool.max-lifetime-ms", 1800000));
        config.setIdleTimeout(fileConfig.getLong("storage.remote.pool.idle-timeout-ms", 600000));
        config.setKeepaliveTime(fileConfig.getLong("storage.remote.pool.keepalive-time-ms", 30000));
    }

    private void createTables() throws SQLException {
        List<String> ddlStatements = DatabaseSchema.getInitStatements(databaseType);
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : ddlStatements) {
                statement.execute(ddl);
            }
            migrateColumns(statement);
        }
    }

    private void migrateColumns(@NotNull Statement statement) {
        try { statement.execute("ALTER TABLE apex_players ADD COLUMN current_streak INT DEFAULT 0;"); } catch (SQLException ignored) {}
        try { statement.execute("ALTER TABLE apex_players ADD COLUMN best_streak INT DEFAULT 0;"); } catch (SQLException ignored) {}
    }

    @NotNull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariCP DataSource is closed or uninitialized.");
        }
        return dataSource.getConnection();
    }

    @NotNull
    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * Executes an asynchronous write (INSERT, UPDATE, DELETE).
     */
    @NotNull
    public CompletableFuture<Integer> executeUpdateAsync(@NotNull String sql, @NotNull Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParameters(ps, params);
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to execute async update: " + sql, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Executes an asynchronous query returning a mapped result object.
     */
    @NotNull
    public <T> CompletableFuture<@Nullable T> executeQueryAsync(@NotNull String sql,
                                                               @NotNull ResultSetMapper<T> mapper,
                                                               @NotNull Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParameters(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapper.map(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to execute async query: " + sql, e);
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
    }

    private void bindParameters(@NotNull PreparedStatement ps, @NotNull Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugin.getLogger().info("Closing HikariCP database pool...");
            dataSource.close();
        }

        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
