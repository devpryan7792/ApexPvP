package com.apexpvp.database;

import org.jetbrains.annotations.NotNull;

/**
 * Supported relational database backends for ApexPvP.
 */
public enum DatabaseType {
    SQLITE("SQLite", "org.sqlite.JDBC", 0),
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),
    MARIADB("MariaDB", "org.mariadb.jdbc.Driver", 3306),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306);

    private final String friendlyName;
    private final String driverClassName;
    private final int defaultPort;

    DatabaseType(@NotNull String friendlyName, @NotNull String driverClassName, int defaultPort) {
        this.friendlyName = friendlyName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
    }

    @NotNull
    public String getFriendlyName() {
        return friendlyName;
    }

    @NotNull
    public String getDriverClassName() {
        return driverClassName;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    @NotNull
    public static DatabaseType fromString(@NotNull String raw) {
        for (DatabaseType type : values()) {
            if (type.name().equalsIgnoreCase(raw) || type.friendlyName.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return SQLITE; // Default fallback
    }
}
