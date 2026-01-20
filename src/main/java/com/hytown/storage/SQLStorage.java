package com.hytown.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hytown.data.Town;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;

/**
 * MySQL storage provider.
 * Stores town data as JSON blobs for compatibility with flatfile format.
 */
public class SQLStorage implements StorageProvider {

    private final StorageConfig.SQLConfig config;
    private final Gson gson;
    private Connection connection;

    private final String tableTowns;
    private final String tableInvites;

    public SQLStorage(StorageConfig.SQLConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create();

        String prefix = config.getTablePrefix();
        this.tableTowns = prefix + "towns";
        this.tableInvites = prefix + "invites";
    }

    @Override
    public void init() throws Exception {
        // Load MySQL driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Try older driver name
            Class.forName("com.mysql.jdbc.Driver");
        }

        // Connect
        connection = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
        );

        // Create tables
        createTables();

        System.out.println("[SQLStorage] Connected to MySQL: " + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Towns table
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    name VARCHAR(64) PRIMARY KEY,
                    data MEDIUMTEXT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, tableTowns));

            // Invites table
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid CHAR(36),
                    town_name VARCHAR(64),
                    PRIMARY KEY (player_uuid, town_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, tableInvites));
        }
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[SQLStorage] Disconnected from MySQL");
            } catch (SQLException e) {
                System.err.println("[SQLStorage] Error closing connection: " + e.getMessage());
            }
        }
    }

    @Override
    public String getName() {
        return "MySQL";
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    private void ensureConnected() throws SQLException {
        if (!isConnected()) {
            // Attempt reconnect
            connection = DriverManager.getConnection(
                    config.getJdbcUrl(),
                    config.getUsername(),
                    config.getPassword()
            );
        }
    }

    // ==================== TOWN OPERATIONS ====================

    @Override
    public Town loadTown(String townName) {
        String sql = String.format("SELECT data FROM %s WHERE name = ?", tableTowns);

        try {
            ensureConnected();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, townName.toLowerCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("data");
                        Town town = gson.fromJson(json, Town.class);
                        if (town != null) {
                            town.validateAfterLoad();
                        }
                        return town;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SQLStorage] Error loading town " + townName + ": " + e.getMessage());
        }

        return null;
    }

    @Override
    public Collection<Town> loadAllTowns() {
        List<Town> towns = new ArrayList<>();
        String sql = String.format("SELECT data FROM %s", tableTowns);

        try {
            ensureConnected();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String json = rs.getString("data");
                        Town town = gson.fromJson(json, Town.class);
                        if (town != null && town.getName() != null) {
                            town.validateAfterLoad();
                            towns.add(town);
                        }
                    } catch (Exception e) {
                        System.err.println("[SQLStorage] Error parsing town data: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error loading towns: " + e.getMessage());
        }

        return towns;
    }

    @Override
    public void saveTown(Town town) {
        String sql = String.format(
                "INSERT INTO %s (name, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = ?, updated_at = CURRENT_TIMESTAMP",
                tableTowns
        );

        try {
            ensureConnected();
            String json = gson.toJson(town);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, town.getName().toLowerCase());
                stmt.setString(2, json);
                stmt.setString(3, json);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error saving town " + town.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void deleteTown(String townName) {
        String sql = String.format("DELETE FROM %s WHERE name = ?", tableTowns);

        try {
            ensureConnected();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, townName.toLowerCase());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error deleting town " + townName + ": " + e.getMessage());
        }
    }

    // ==================== INVITE OPERATIONS ====================

    @Override
    public Map<UUID, Set<String>> loadInvites() {
        Map<UUID, Set<String>> invites = new HashMap<>();
        String sql = String.format("SELECT player_uuid, town_name FROM %s", tableInvites);

        try {
            ensureConnected();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                        String townName = rs.getString("town_name");
                        invites.computeIfAbsent(playerId, k -> new HashSet<>()).add(townName);
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID, skip
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error loading invites: " + e.getMessage());
        }

        return invites;
    }

    @Override
    public void saveInvites(Map<UUID, Set<String>> invites) {
        String deleteSql = String.format("DELETE FROM %s", tableInvites);
        String insertSql = String.format("INSERT INTO %s (player_uuid, town_name) VALUES (?, ?)", tableInvites);

        try {
            ensureConnected();

            // Clear existing invites
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(deleteSql);
            }

            // Insert all invites
            try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
                    String playerId = entry.getKey().toString();
                    for (String townName : entry.getValue()) {
                        stmt.setString(1, playerId);
                        stmt.setString(2, townName);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error saving invites: " + e.getMessage());
        }
    }

    /**
     * Get connection info for status display.
     */
    public String getConnectionInfo() {
        return config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
    }
}
