package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.economy.TransactionEntry;
import com.ecotale.economy.TransactionType;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * H2 Database storage provider for economy data.
 * Stores both player balances and transaction history.
 * 
 * Data is stored in: universe/Ecotale/h2/
 * 
 * Features:
 * - ACID compliant transactions
 * - Indexed queries for fast lookups
 * - Async operations via executor
 * - Connection pooling via single persistent connection
 */
public class H2StorageProvider implements StorageProvider {
    
    private static final String DB_NAME = "ecotale";
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("Ecotale-H2");
    
    /** 
     * Data path: mods/michiweon_Ecotale/ - same location as plugin config.
     * Uses relative path from server working directory.
     */
    private static final Path ECOTALE_PATH = Path.of("mods", "michiweon_Ecotale");
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ecotale-H2-IO");
        t.setDaemon(true);
        return t;
    });
    
    private Connection connection;
    private String dbPath;
    private int playerCount = 0;
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Create data directory in universe/Ecotale/h2/
                File dataDir = ECOTALE_PATH.toFile();
                if (!dataDir.exists()) {
                    dataDir.mkdirs();
                }
                
                dbPath = new File(dataDir, DB_NAME).getAbsolutePath();
                
                // Explicitly register H2 driver (needed due to classloader issues)
                try {
                    Class.forName("org.h2.Driver");
                } catch (ClassNotFoundException e) {
                    LOGGER.at(Level.SEVERE).log("H2 Driver class not found: %s", e.getMessage());
                    throw new RuntimeException("H2 Driver not available", e);
                }
                
                // Connect to H2 (creates file if not exists)
                connection = DriverManager.getConnection(
                    "jdbc:h2:" + dbPath + ";MODE=MySQL;AUTO_SERVER=FALSE",
                    "sa", ""
                );
                
                // Create tables
                createTables();
                
                // Count existing players
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM balances")) {
                    if (rs.next()) {
                        playerCount = rs.getInt(1);
                    }
                }
                
                LOGGER.at(Level.INFO).log("H2 database initialized: %s.mv.db (%d players)", dbPath, playerCount);
                
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to initialize H2 database: %s", e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Balances table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS balances (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(64),
                    balance DOUBLE DEFAULT 0.0,
                    total_earned DOUBLE DEFAULT 0.0,
                    total_spent DOUBLE DEFAULT 0.0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // Migration: Add player_name column if missing (for existing databases)
            try {
                stmt.execute("ALTER TABLE balances ADD COLUMN IF NOT EXISTS player_name VARCHAR(64)");
            } catch (SQLException ignored) {
                // Column already exists or syntax not supported
            }
            
            // Transactions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    timestamp BIGINT NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    source_uuid VARCHAR(36),
                    target_uuid VARCHAR(36),
                    player_name VARCHAR(64),
                    amount DOUBLE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            
            // Create indexes if not exist
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_timestamp ON transactions(timestamp DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_player ON transactions(player_name)");
        }
    }
    
    // ========== Balance Operations ==========
    
    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT balance, total_earned, total_spent FROM balances WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            PlayerBalance pb = new PlayerBalance(playerUuid);
                            // Use setBalance to set the loaded balance
                            pb.setBalance(rs.getDouble("balance"), "Loaded from DB");
                            return pb;
                        }
                    }
                }
                
                // Create new account with starting balance
                double startingBalance = Main.CONFIG.get().getStartingBalance();
                PlayerBalance newBalance = new PlayerBalance(playerUuid);
                newBalance.setBalance(startingBalance, "New account");
                savePlayerSync(playerUuid, newBalance);
                playerCount++;
                return newBalance;
                
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load player %s: %s", playerUuid, e.getMessage());
                // Return default balance on error
                PlayerBalance pb = new PlayerBalance(playerUuid);
                pb.setBalance(Main.CONFIG.get().getStartingBalance(), "Error fallback");
                return pb;
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> savePlayerSync(playerUuid, balance), executor);
    }
    
    private void savePlayerSync(UUID playerUuid, PlayerBalance balance) {
        try {
            String sql = """
                MERGE INTO balances (uuid, balance, total_earned, total_spent, updated_at) 
                KEY(uuid) 
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setDouble(2, balance.getBalance());
                ps.setDouble(3, balance.getTotalEarned());
                ps.setDouble(4, balance.getTotalSpent());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save player %s: %s", playerUuid, e.getMessage());
        }
    }
    
    /**
     * Update player's cached name.
     * Call this on player join to keep names current.
     */
    public void updatePlayerName(@Nonnull UUID playerUuid, @Nonnull String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                String sql = "UPDATE balances SET player_name = ? WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerName);
                    ps.setString(2, playerUuid.toString());
                    int updated = ps.executeUpdate();
                    
                    // If no row was updated, insert a new one with just the name
                    if (updated == 0) {
                        String insertSql = """
                            INSERT INTO balances (uuid, player_name, balance) 
                            VALUES (?, ?, ?)
                        """;
                        try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                            insertPs.setString(1, playerUuid.toString());
                            insertPs.setString(2, playerName);
                            insertPs.setDouble(3, com.ecotale.Main.CONFIG.get().getStartingBalance());
                            insertPs.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to update player name: %s", e.getMessage());
            }
        }, executor);
    }
    
    /**
     * Get cached player name from database.
     * Returns null if not found.
     */
    public String getPlayerName(@Nonnull UUID playerUuid) {
        try {
            String sql = "SELECT player_name FROM balances WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("player_name");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to get player name: %s", e.getMessage());
        }
        return null;
    }
    
    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        return CompletableFuture.runAsync(() -> {
            if (dirtyPlayers.isEmpty()) return;
            
            try {
                connection.setAutoCommit(false);
                String sql = """
                    MERGE INTO balances (uuid, balance, total_earned, total_spent, updated_at) 
                    KEY(uuid) 
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (var entry : dirtyPlayers.entrySet()) {
                        ps.setString(1, entry.getKey().toString());
                        ps.setDouble(2, entry.getValue().getBalance());
                        ps.setDouble(3, entry.getValue().getTotalEarned());
                        ps.setDouble(4, entry.getValue().getTotalSpent());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                LOGGER.at(Level.SEVERE).log("Failed to batch save: %s", e.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerBalance> result = new HashMap<>();
            try {
                String sql = "SELECT uuid, balance, total_earned, total_spent FROM balances";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        PlayerBalance pb = new PlayerBalance(uuid);
                        pb.setBalance(rs.getDouble("balance"), "Bulk load");
                        result.put(uuid, pb);
                    }
                }
                playerCount = result.size();
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load all balances: %s", e.getMessage());
            }
            return result;
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT 1 FROM balances WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> deletePlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "DELETE FROM balances WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    int affected = ps.executeUpdate();
                    if (affected > 0) playerCount--;
                }
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to delete player %s: %s", playerUuid, e.getMessage());
            }
        }, executor);
    }
    
    // ========== Transaction Logging ==========
    
    /**
     * Log a transaction to the database.
     * Called asynchronously to avoid blocking economy operations.
     */
    public void logTransaction(TransactionEntry entry) {
        executor.execute(() -> {
            try {
                String sql = """
                    INSERT INTO transactions (timestamp, type, source_uuid, target_uuid, player_name, amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setLong(1, entry.timestamp().toEpochMilli());
                    ps.setString(2, entry.type().name());
                    ps.setString(3, entry.sourcePlayer() != null ? entry.sourcePlayer().toString() : null);
                    ps.setString(4, entry.targetPlayer() != null ? entry.targetPlayer().toString() : null);
                    ps.setString(5, entry.playerName());
                    ps.setDouble(6, entry.amount());
                    ps.executeUpdate();
                    LOGGER.at(Level.INFO).log("Logged transaction to H2: %s %s %.0f", entry.type(), entry.playerName(), entry.amount());
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to log transaction: %s", e.getMessage());
            }
        });
    }
    
    /**
     * Query transactions with optional player filter and pagination.
     */
    public List<TransactionEntry> queryTransactions(String playerFilter, int limit, int offset) {
        List<TransactionEntry> results = new ArrayList<>();
        try {
            String sql;
            if (playerFilter != null && !playerFilter.isEmpty()) {
                sql = """
                    SELECT * FROM transactions 
                    WHERE LOWER(player_name) LIKE ? 
                    ORDER BY timestamp DESC 
                    LIMIT ? OFFSET ?
                """;
            } else {
                sql = """
                    SELECT * FROM transactions 
                    ORDER BY timestamp DESC 
                    LIMIT ? OFFSET ?
                """;
            }
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int paramIndex = 1;
                if (playerFilter != null && !playerFilter.isEmpty()) {
                    ps.setString(paramIndex++, "%" + playerFilter.toLowerCase() + "%");
                }
                ps.setInt(paramIndex++, limit);
                ps.setInt(paramIndex, offset);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(resultSetToEntry(rs));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to query transactions: %s", e.getMessage());
        }
        return results;
    }
    
    /**
     * Count total transactions matching filter.
     */
    public int countTransactions(String playerFilter) {
        try {
            String sql;
            if (playerFilter != null && !playerFilter.isEmpty()) {
                sql = "SELECT COUNT(*) FROM transactions WHERE LOWER(player_name) LIKE ?";
            } else {
                sql = "SELECT COUNT(*) FROM transactions";
            }
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (playerFilter != null && !playerFilter.isEmpty()) {
                    ps.setString(1, "%" + playerFilter.toLowerCase() + "%");
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        LOGGER.at(Level.INFO).log("H2 transaction count: %d (filter: %s)", count, playerFilter);
                        return count;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to count transactions: %s", e.getMessage());
        }
        return 0;
    }
    
    private TransactionEntry resultSetToEntry(ResultSet rs) throws SQLException {
        long timestampMs = rs.getLong("timestamp");
        Instant timestamp = Instant.ofEpochMilli(timestampMs);
        
        String typeStr = rs.getString("type");
        TransactionType type = TransactionType.valueOf(typeStr);
        
        String sourceUuidStr = rs.getString("source_uuid");
        UUID sourceUuid = sourceUuidStr != null ? UUID.fromString(sourceUuidStr) : null;
        
        String targetUuidStr = rs.getString("target_uuid");
        UUID targetUuid = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;
        
        String playerName = rs.getString("player_name");
        double amount = rs.getDouble("amount");
        
        // Re-format timestamp for display
        java.time.format.DateTimeFormatter formatter = 
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
        String formattedTime = formatter.format(timestamp);
        
        return new TransactionEntry(timestamp, formattedTime, type, sourceUuid, targetUuid, amount, playerName);
    }
    
    // ========== Lifecycle ==========
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                LOGGER.at(Level.INFO).log("H2 database connection closed");
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Error closing H2 connection: %s", e.getMessage());
            }
        }, executor).thenRun(() -> {
            executor.shutdown();
        });
    }
    
    @Override
    public String getName() {
        return "H2 Database";
    }
    
    @Override
    public int getPlayerCount() {
        return playerCount;
    }
    
    /**
     * Get the database connection for advanced operations.
     * Use with caution - prefer dedicated methods.
     */
    public Connection getConnection() {
        return connection;
    }
}
