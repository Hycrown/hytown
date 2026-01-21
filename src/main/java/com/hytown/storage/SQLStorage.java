package com.hytown.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hytown.data.Town;

import java.sql.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MySQL storage provider with connection pooling and transaction support.
 * Stores town data as JSON blobs for compatibility with flatfile format.
 *
 * Features:
 * - Connection pooling with configurable pool size
 * - Transaction support for atomic operations
 * - Auto-reconnect on connection failure
 * - Proper resource cleanup
 */
public class SQLStorage implements StorageProvider {

    private final StorageConfig.SQLConfig config;
    private final Gson gson;

    // Connection pool - uses blocking queue to avoid busy-waiting
    private final LinkedBlockingQueue<Connection> connectionPool = new LinkedBlockingQueue<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final int MIN_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 10;
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;

    private final String tableTowns;
    private final String tableInvites;
    private final String validatedDatabase;

    public SQLStorage(StorageConfig.SQLConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create();

        // SECURITY: Validate identifiers to prevent SQL injection
        String prefix = sanitizeIdentifier(config.getTablePrefix(), "hytown_");
        this.tableTowns = prefix + "towns";
        this.tableInvites = prefix + "invites";
        this.validatedDatabase = sanitizeIdentifier(config.getDatabase(), "hytown");
    }

    /**
     * Sanitizes a database identifier (table/column prefix) to prevent SQL injection.
     * Only allows alphanumeric characters and underscores.
     *
     * @param identifier The identifier to sanitize
     * @param defaultValue Default value if identifier is null/empty/invalid
     * @return A safe identifier string
     * @throws IllegalArgumentException if identifier contains invalid characters and no default provided
     */
    private static String sanitizeIdentifier(String identifier, String defaultValue) {
        if (identifier == null || identifier.isEmpty()) {
            return defaultValue;
        }

        // Only allow alphanumeric and underscore - standard SQL identifier rules
        if (!identifier.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            System.out.println("\u001B[33m[SQLStorage] WARNING: Invalid table prefix '" + identifier +
                    "' contains illegal characters. Using default: " + defaultValue + "\u001B[0m");
            return defaultValue;
        }

        // Limit length to prevent buffer issues (MySQL max identifier length is 64)
        if (identifier.length() > 32) {
            System.out.println("\u001B[33m[SQLStorage] WARNING: Table prefix too long, truncating to 32 characters\u001B[0m");
            identifier = identifier.substring(0, 32);
        }

        return identifier;
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

        // Initialize connection pool
        for (int i = 0; i < MIN_POOL_SIZE; i++) {
            Connection conn = createConnection();
            if (conn != null) {
                connectionPool.offer(conn);
            }
        }

        if (connectionPool.isEmpty()) {
            throw new Exception("Failed to create any database connections");
        }

        // Create tables using a pooled connection
        try (PooledConnection pooled = getPooledConnection()) {
            createTables(pooled.connection);
        }

        initialized.set(true);
        System.out.println("[SQLStorage] Connected to MySQL: " + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
        System.out.println("[SQLStorage] Connection pool initialized with " + connectionPool.size() + " connections");
    }

    /**
     * Create a new database connection.
     */
    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
        );
        conn.setAutoCommit(true);
        activeConnections.incrementAndGet();
        return conn;
    }

    /**
     * Get a connection from the pool, creating a new one if necessary.
     */
    private PooledConnection getPooledConnection() throws SQLException {
        Connection conn = connectionPool.poll();

        if (conn != null) {
            // Validate the connection
            try {
                if (conn.isClosed() || !conn.isValid(CONNECTION_TIMEOUT_SECONDS)) {
                    activeConnections.decrementAndGet();
                    conn = null;
                }
            } catch (SQLException e) {
                activeConnections.decrementAndGet();
                conn = null;
            }
        }

        if (conn == null) {
            // Create new connection if pool is empty and we haven't hit max
            if (activeConnections.get() < MAX_POOL_SIZE) {
                conn = createConnection();
            } else {
                // Wait for a connection using blocking poll (no busy-waiting)
                try {
                    conn = connectionPool.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for connection");
                }
                if (conn == null) {
                    throw new SQLException("Connection pool exhausted");
                }
            }
        }

        return new PooledConnection(conn);
    }

    /**
     * Return a connection to the pool.
     */
    private void returnConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(1)) {
                    // Reset connection state
                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }
                    connectionPool.offer(conn);
                } else {
                    activeConnections.decrementAndGet();
                }
            } catch (SQLException e) {
                activeConnections.decrementAndGet();
            }
        }
    }

    /**
     * Wrapper for pooled connections that auto-returns to pool on close.
     */
    private class PooledConnection implements AutoCloseable {
        final Connection connection;
        private boolean returned = false;

        PooledConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            if (!returned) {
                returned = true;
                returnConnection(connection);
            }
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Towns table
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    name VARCHAR(64) PRIMARY KEY,
                    data MEDIUMTEXT NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, tableTowns));

            // Invites table
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid CHAR(36),
                    town_name VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_uuid, town_name),
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_town_name (town_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """, tableInvites));
        }
    }

    @Override
    public void shutdown() {
        initialized.set(false);

        // Close all pooled connections
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
                activeConnections.decrementAndGet();
            } catch (SQLException e) {
                System.err.println("[SQLStorage] Error closing connection: " + e.getMessage());
            }
        }

        System.out.println("[SQLStorage] Disconnected from MySQL, closed " +
                (MIN_POOL_SIZE - activeConnections.get()) + " connections");
    }

    @Override
    public String getName() {
        return "MySQL";
    }

    @Override
    public boolean isConnected() {
        if (!initialized.get()) {
            return false;
        }

        try (PooledConnection pooled = getPooledConnection()) {
            return pooled.connection.isValid(CONNECTION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== TOWN OPERATIONS ====================

    @Override
    public Town loadTown(String townName) {
        String sql = String.format("SELECT data FROM %s WHERE name = ?", tableTowns);

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

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
        } catch (Exception e) {
            System.err.println("[SQLStorage] Error loading town " + townName + ": " + e.getMessage());
        }

        return null;
    }

    @Override
    public Collection<Town> loadAllTowns() {
        List<Town> towns = new ArrayList<>();
        String sql = String.format("SELECT name, data FROM %s", tableTowns);

        try (PooledConnection pooled = getPooledConnection();
             Statement stmt = pooled.connection.createStatement();
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
                    String name = rs.getString("name");
                    System.err.println("[SQLStorage] Error parsing town data for '" + name + "': " + e.getMessage());
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
                "INSERT INTO %s (name, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP",
                tableTowns
        );

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            String json = gson.toJson(town);
            stmt.setString(1, town.getName().toLowerCase());
            stmt.setString(2, json);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error saving town " + town.getName() + ": " + e.getMessage());
            throw new RuntimeException("Failed to save town: " + town.getName(), e);
        }
    }

    @Override
    public void deleteTown(String townName) {
        String sql = String.format("DELETE FROM %s WHERE name = ?", tableTowns);

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            stmt.setString(1, townName.toLowerCase());
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error deleting town " + townName + ": " + e.getMessage());
        }
    }

    /**
     * Rename a town atomically using a transaction.
     * This ensures data consistency if the operation fails midway.
     */
    @Override
    public void renameTown(String oldName, String newName, Town town) {
        try (PooledConnection pooled = getPooledConnection()) {
            Connection conn = pooled.connection;
            boolean originalAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);

                String json = gson.toJson(town);

                // Insert/update with new name
                String insertSql = String.format(
                        "INSERT INTO %s (name, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP",
                        tableTowns
                );
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, newName.toLowerCase());
                    insertStmt.setString(2, json);
                    insertStmt.executeUpdate();
                }

                // Delete old name (if different)
                if (!oldName.equalsIgnoreCase(newName)) {
                    String deleteSql = String.format("DELETE FROM %s WHERE name = ?", tableTowns);
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setString(1, oldName.toLowerCase());
                        deleteStmt.executeUpdate();
                    }

                    // Update invites to reference new town name
                    String updateInvitesSql = String.format(
                            "UPDATE %s SET town_name = ? WHERE town_name = ?",
                            tableInvites
                    );
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateInvitesSql)) {
                        updateStmt.setString(1, newName);
                        updateStmt.setString(2, oldName);
                        updateStmt.executeUpdate();
                    }
                }

                conn.commit();
                System.out.println("[SQLStorage] Renamed town '" + oldName + "' to '" + newName + "'");

            } catch (SQLException e) {
                // Rollback on any error
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("[SQLStorage] Error during rollback: " + rollbackEx.getMessage());
                }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error renaming town " + oldName + " to " + newName + ": " + e.getMessage());
            throw new RuntimeException("Failed to rename town", e);
        }
    }

    // ==================== INVITE OPERATIONS ====================

    @Override
    public Map<UUID, Set<String>> loadInvites() {
        Map<UUID, Set<String>> invites = new HashMap<>();
        String sql = String.format("SELECT player_uuid, town_name FROM %s ORDER BY player_uuid", tableInvites);

        try (PooledConnection pooled = getPooledConnection();
             Statement stmt = pooled.connection.createStatement();
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
        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error loading invites: " + e.getMessage());
        }

        return invites;
    }

    /**
     * Save all pending invites atomically using a transaction.
     * Uses batch operations for efficiency.
     */
    @Override
    public void saveInvites(Map<UUID, Set<String>> invites) {
        try (PooledConnection pooled = getPooledConnection()) {
            Connection conn = pooled.connection;
            boolean originalAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);

                // Clear existing invites
                String deleteSql = String.format("DELETE FROM %s", tableInvites);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(deleteSql);
                }

                // Insert all invites using batch
                if (!invites.isEmpty()) {
                    String insertSql = String.format(
                            "INSERT INTO %s (player_uuid, town_name) VALUES (?, ?)",
                            tableInvites
                    );
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        int batchCount = 0;
                        for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
                            String playerId = entry.getKey().toString();
                            for (String townName : entry.getValue()) {
                                stmt.setString(1, playerId);
                                stmt.setString(2, townName);
                                stmt.addBatch();
                                batchCount++;

                                // Execute batch every 1000 records to avoid memory issues
                                if (batchCount % 1000 == 0) {
                                    stmt.executeBatch();
                                }
                            }
                        }
                        // Execute remaining batch
                        if (batchCount % 1000 != 0) {
                            stmt.executeBatch();
                        }
                    }
                }

                conn.commit();

            } catch (SQLException e) {
                // Rollback on any error
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("[SQLStorage] Error during rollback: " + rollbackEx.getMessage());
                }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error saving invites: " + e.getMessage());
            throw new RuntimeException("Failed to save invites", e);
        }
    }

    // ==================== ADDITIONAL TRANSACTIONAL OPERATIONS ====================

    /**
     * Add a single invite without full replace.
     */
    public void addInvite(UUID playerId, String townName) {
        String sql = String.format(
                "INSERT IGNORE INTO %s (player_uuid, town_name) VALUES (?, ?)",
                tableInvites
        );

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            stmt.setString(2, townName);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error adding invite: " + e.getMessage());
        }
    }

    /**
     * Remove a single invite.
     */
    public void removeInvite(UUID playerId, String townName) {
        String sql = String.format(
                "DELETE FROM %s WHERE player_uuid = ? AND town_name = ?",
                tableInvites
        );

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            stmt.setString(2, townName);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error removing invite: " + e.getMessage());
        }
    }

    /**
     * Remove all invites for a player.
     */
    public void clearPlayerInvites(UUID playerId) {
        String sql = String.format("DELETE FROM %s WHERE player_uuid = ?", tableInvites);

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error clearing player invites: " + e.getMessage());
        }
    }

    /**
     * Remove all invites for a town (when town is deleted).
     */
    public void clearTownInvites(String townName) {
        String sql = String.format("DELETE FROM %s WHERE town_name = ?", tableInvites);

        try (PooledConnection pooled = getPooledConnection();
             PreparedStatement stmt = pooled.connection.prepareStatement(sql)) {

            stmt.setString(1, townName);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SQLStorage] Error clearing town invites: " + e.getMessage());
        }
    }

    /**
     * Get connection info for status display.
     */
    @Override
    public String getConnectionInfo() {
        return config.getHost() + ":" + config.getPort() + "/" + validatedDatabase +
               " (pool: " + connectionPool.size() + "/" + activeConnections.get() + " active, tables: " + tableTowns + ", " + tableInvites + ")";
    }

    /**
     * Check if this provider supports transactions.
     */
    @Override
    public boolean supportsTransactions() {
        return true;  // MySQL/InnoDB supports ACID transactions
    }

    /**
     * Get pool statistics for monitoring.
     */
    public String getPoolStats() {
        return String.format("Pool size: %d, Active connections: %d, Max: %d",
                connectionPool.size(), activeConnections.get(), MAX_POOL_SIZE);
    }
}
