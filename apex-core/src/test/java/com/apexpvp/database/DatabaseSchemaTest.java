package com.apexpvp.database;

import com.apexpvp.database.schema.DatabaseSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSchemaTest {

    @Test
    @DisplayName("Verify DDL generation across all supported database backends")
    void testInitStatementsForAllTypes() {
        for (DatabaseType type : DatabaseType.values()) {
            List<String> statements = DatabaseSchema.getInitStatements(type);
            assertNotNull(statements);
            assertFalse(statements.isEmpty(), "Statements must not be empty for " + type);

            // Verify core tables are present
            boolean hasPlayersTable = statements.stream().anyMatch(s -> s.contains("apex_players"));
            boolean hasStatsTable = statements.stream().anyMatch(s -> s.contains("apex_player_stats"));
            boolean hasLayoutsTable = statements.stream().anyMatch(s -> s.contains("apex_player_kit_layouts"));
            boolean hasHistoryTable = statements.stream().anyMatch(s -> s.contains("apex_match_history"));

            assertTrue(hasPlayersTable, "Must create apex_players for " + type);
            assertTrue(hasStatsTable, "Must create apex_player_stats for " + type);
            assertTrue(hasLayoutsTable, "Must create apex_player_kit_layouts for " + type);
            assertTrue(hasHistoryTable, "Must create apex_match_history for " + type);

            assertTrue(statements.stream().anyMatch(s -> s.contains("current_streak")), "Must define current_streak for " + type);
            assertTrue(statements.stream().anyMatch(s -> s.contains("best_streak")), "Must define best_streak for " + type);
        }
    }
}
