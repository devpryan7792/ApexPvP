package com.apexpvp.leaderboard;

import com.apexpvp.ApexPvPPlugin;
import com.apexpvp.database.DatabaseManager;
import com.apexpvp.profile.Rank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LeaderboardTest {

    private ApexPvPPlugin plugin;
    private DatabaseManager dbManager;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws SQLException {
        plugin = mock(ApexPvPPlugin.class);
        dbManager = mock(DatabaseManager.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(plugin.getDatabaseManager()).thenReturn(dbManager);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(dbManager.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
    }

    @Test
    @DisplayName("Verify LeaderboardEntry record properties")
    void testLeaderboardEntryRecord() {
        LeaderboardManager.LeaderboardEntry entry = new LeaderboardManager.LeaderboardEntry(
            1, "Notch", Rank.NETHERITE_3, 2850
        );

        assertEquals(1, entry.position());
        assertEquals("Notch", entry.username());
        assertEquals(Rank.NETHERITE_3, entry.rank());
        assertEquals(2850, entry.score());
    }

    @Test
    @DisplayName("Verify LeaderboardManager 60-second TTL in-memory caching")
    void testLeaderboardCaching() throws Exception {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("username")).thenReturn("TopPlayer");
        when(resultSet.getString("current_rank")).thenReturn("NETHERITE_3");
        when(resultSet.getInt("elo")).thenReturn(2900);

        LeaderboardManager manager = new LeaderboardManager(plugin);

        // First call: executes query on database
        List<LeaderboardManager.LeaderboardEntry> result1 = manager.getTopElo(10).get();
        assertEquals(1, result1.size());
        assertEquals("TopPlayer", result1.get(0).username());
        assertEquals(2900, result1.get(0).score());
        verify(dbManager, times(1)).getConnection();

        // Second call: should return from in-memory cache without hitting DB
        List<LeaderboardManager.LeaderboardEntry> result2 = manager.getTopElo(10).get();
        assertEquals(1, result2.size());
        assertEquals("TopPlayer", result2.get(0).username());
        verify(dbManager, times(1)).getConnection(); // Still only 1 invocation!

        // Invalidate cache
        manager.invalidateCache();

        // Third call: cache is invalidated, must query DB again
        when(resultSet.next()).thenReturn(true, false);
        List<LeaderboardManager.LeaderboardEntry> result3 = manager.getTopElo(10).get();
        assertEquals(1, result3.size());
        verify(dbManager, times(2)).getConnection();
    }
}
