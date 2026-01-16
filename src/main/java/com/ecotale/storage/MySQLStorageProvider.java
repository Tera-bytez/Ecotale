package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * MySQL storage provider for shared economy data across servers.
 * 
 * Enables multiple Hytale servers to share the same economy database,
 * perfect for server networks or load-balanced setups.
 * 
 * Features:
 * - Connection pooling via single persistent connection
 * - Automatic table creation with configurable prefix
 * - Async operations via dedicated thread
 * - Full StorageProvider interface implementation
 * 
 * @author michiweon
 */
public class MySQLStorageProvider implements StorageProvider {
    
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("Ecotale-MySQL");
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ecotale-MySQL-IO");
        t.setDaemon(true);
        return t;
    });
    
    private Connection connection;
    private String tablePrefix;
    private int playerCount = 0;
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                EcotaleConfig config = Main.CONFIG.get();
                tablePrefix = config.getMysqlTablePrefix();
                
                String host = config.getMysqlHost();
                int port = config.getMysqlPort();
                String database = config.getMysqlDatabase();
                String username = config.getMysqlUsername();
                String password = config.getMysqlPassword();
                
                // Build JDBC URL
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database);
                
                LOGGER.at(Level.INFO).log("Connecting to MySQL: %s:%d/%s", host, port, database);
                
                // Load MySQL driver
                Class.forName("com.mysql.cj.jdbc.Driver");
                
                // Connect
                connection = DriverManager.getConnection(url, username, password);
                
                // Create tables
                createTables();
                
                // Count players
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tablePrefix + "balances")) {
                    if (rs.next()) {
                        playerCount = rs.getInt(1);
                    }
                }
                
                LOGGER.at(Level.INFO).log("MySQL connected successfully (%d players)", playerCount);
                
            } catch (ClassNotFoundException e) {
                LOGGER.at(Level.SEVERE).log("MySQL driver not found! Add mysql-connector-j to dependencies");
                throw new RuntimeException("MySQL driver not available", e);
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to connect to MySQL: %s", e.getMessage());
                throw new RuntimeException("MySQL connection failed", e);
            }
        }, executor);
    }
    
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Balances table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS %sbalances (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(64),
                    balance DOUBLE DEFAULT 0.0,
                    total_earned DOUBLE DEFAULT 0.0,
                    total_spent DOUBLE DEFAULT 0.0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """.formatted(tablePrefix));
            
            // Transactions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS %stransactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    timestamp BIGINT NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    source_uuid VARCHAR(36),
                    target_uuid VARCHAR(36),
                    player_name VARCHAR(64),
                    amount DOUBLE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_timestamp (timestamp DESC),
                    INDEX idx_player (player_name)
                )
                """.formatted(tablePrefix));
        }
    }
    
    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT balance, total_earned, total_spent FROM " + tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            PlayerBalance pb = new PlayerBalance(playerUuid);
                            pb.setBalance(rs.getDouble("balance"), "Loaded from MySQL");
                            return pb;
                        }
                    }
                }
                
                // Create new player
                PlayerBalance newBalance = new PlayerBalance(playerUuid);
                newBalance.setBalance(Main.CONFIG.get().getStartingBalance(), "Initial balance");
                playerCount++;
                return newBalance;
                
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load player %s: %s", playerUuid, e.getMessage());
                return new PlayerBalance(playerUuid);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = """
                    INSERT INTO %sbalances (uuid, balance, total_earned, total_spent, updated_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE 
                        balance = VALUES(balance),
                        total_earned = VALUES(total_earned),
                        total_spent = VALUES(total_spent),
                        updated_at = NOW()
                    """.formatted(tablePrefix);
                
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
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        if (dirtyPlayers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<?>[] futures = dirtyPlayers.entrySet().stream()
            .map(entry -> savePlayer(entry.getKey(), entry.getValue()))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures).thenRun(() -> {
            LOGGER.at(Level.INFO).log("Saved %d players to MySQL", dirtyPlayers.size());
        });
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerBalance> allBalances = new HashMap<>();
            
            try {
                String sql = "SELECT uuid, balance, total_earned, total_spent FROM " + tablePrefix + "balances";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        PlayerBalance pb = new PlayerBalance(uuid);
                        pb.setBalance(rs.getDouble("balance"), "Loaded from MySQL");
                        allBalances.put(uuid, pb);
                    }
                }
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to load all players: %s", e.getMessage());
            }
            
            return allBalances;
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT 1 FROM " + tablePrefix + "balances WHERE uuid = ?";
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
                String sql = "DELETE FROM " + tablePrefix + "balances WHERE uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                    playerCount--;
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to delete player %s: %s", playerUuid, e.getMessage());
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
                executor.shutdown();
                LOGGER.at(Level.INFO).log("MySQL connection closed");
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Error closing MySQL connection: %s", e.getMessage());
            }
        });
    }
    
    @Override
    public String getName() {
        return "MySQL (shared database)";
    }
    
    @Override
    public int getPlayerCount() {
        return playerCount;
    }
}
