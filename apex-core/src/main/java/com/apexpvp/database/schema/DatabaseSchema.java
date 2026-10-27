package com.apexpvp.database.schema;

import com.apexpvp.database.DatabaseType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates dialect-specific SQL DDL statements for ApexPvP tables.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {
        // Utility
    }

    @NotNull
    public static List<String> getInitStatements(@NotNull DatabaseType type) {
        List<String> statements = new ArrayList<>();

        String doubleType = switch (type) {
            case POSTGRESQL -> "DOUBLE PRECISION";
            case SQLITE -> "REAL";
            case MYSQL, MARIADB -> "DOUBLE";
        };

        String timestampType = switch (type) {
            case POSTGRESQL -> "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP";
            case SQLITE -> "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            case MYSQL, MARIADB -> "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
        };

        // 1. Core Player Profile Table
        statements.add("""
            CREATE TABLE IF NOT EXISTS apex_players (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                current_rank VARCHAR(32) NOT NULL DEFAULT 'COPPER_1',
                elo INT NOT NULL DEFAULT 800,
                coins %s NOT NULL DEFAULT 0.0,
                current_streak INT NOT NULL DEFAULT 0,
                best_streak INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_seen %s
            );
            """.formatted(doubleType, timestampType));

        // 2. Player Kit Statistics Table
        statements.add("""
            CREATE TABLE IF NOT EXISTS apex_player_stats (
                uuid VARCHAR(36) NOT NULL,
                kit_id VARCHAR(32) NOT NULL,
                wins INT NOT NULL DEFAULT 0,
                losses INT NOT NULL DEFAULT 0,
                kills INT NOT NULL DEFAULT 0,
                deaths INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, kit_id),
                FOREIGN KEY (uuid) REFERENCES apex_players(uuid) ON DELETE CASCADE
            );
            """);

        // 3. Custom Kit Hotbar Layouts Table
        statements.add("""
            CREATE TABLE IF NOT EXISTS apex_player_kit_layouts (
                uuid VARCHAR(36) NOT NULL,
                kit_id VARCHAR(32) NOT NULL,
                serialized_layout TEXT NOT NULL,
                updated_at %s,
                PRIMARY KEY (uuid, kit_id),
                FOREIGN KEY (uuid) REFERENCES apex_players(uuid) ON DELETE CASCADE
            );
            """.formatted(timestampType));

        // 4. Match Audit History Table
        statements.add("""
            CREATE TABLE IF NOT EXISTS apex_match_history (
                match_id VARCHAR(36) PRIMARY KEY,
                mode VARCHAR(16) NOT NULL,
                kit_id VARCHAR(32) NOT NULL,
                arena_id VARCHAR(32) NOT NULL,
                winner_uuid VARCHAR(36) NOT NULL,
                loser_uuid VARCHAR(36) NOT NULL,
                elo_delta INT NOT NULL DEFAULT 0,
                stake_amount %s NOT NULL DEFAULT 0.0,
                duration_seconds INT NOT NULL,
                completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.formatted(doubleType));

        // Indexes for performance
        if (type != DatabaseType.SQLITE) {
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_players_elo ON apex_players(elo DESC);");
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_match_history_winner ON apex_match_history(winner_uuid);");
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_match_history_loser ON apex_match_history(loser_uuid);");
        } else {
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_players_elo ON apex_players(elo);");
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_match_history_winner ON apex_match_history(winner_uuid);");
            statements.add("CREATE INDEX IF NOT EXISTS idx_apex_match_history_loser ON apex_match_history(loser_uuid);");
        }

        return statements;
    }
}
