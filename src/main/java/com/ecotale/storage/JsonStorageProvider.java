package com.ecotale.storage;

import com.ecotale.Main;
import com.ecotale.economy.PlayerBalance;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * JSON per-player file storage provider.
 * 
 * Storage Structure:
 *   Universe/Ecotale/
 *     players/
 *       <uuid>.json      <- Current data
 *       <uuid>.json.bak  <- Previous save (backup)
 * 
 * Features:
 * - Atomic writes via temp file + rename
 * - Automatic backup before overwrite
 * - Per-player files (corruption affects only one player)
 * - Zero external dependencies
 * - Human-readable format for debugging
 * 
 * Thread Safety:
 * - All public methods return CompletableFuture for async execution
 * - Internal operations are atomic at file level
 */
public class JsonStorageProvider implements StorageProvider {
    
    /** Data path: mods/michiweon_Ecotale/ - same location as plugin config */
    private static final Path ECOTALE_PATH = Path.of("mods", "michiweon_Ecotale");
    private static final Path PLAYERS_PATH = ECOTALE_PATH.resolve("players");
    private static final Path LEGACY_PATH = ECOTALE_PATH.resolve("balances.json");
    
    private final HytaleLogger logger;
    private final AtomicInteger playerCount = new AtomicInteger(0);
    
    public JsonStorageProvider() {
        this.logger = HytaleLogger.getLogger().getSubLogger("Ecotale-Storage");
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Create directories if needed
                Files.createDirectories(PLAYERS_PATH);
                
                // Check for legacy migration
                if (Files.exists(LEGACY_PATH)) {
                    migrateLegacyFormat();
                }
                
                // Count existing players
                try (Stream<Path> files = Files.list(PLAYERS_PATH)) {
                    int count = (int) files.filter(p -> p.toString().endsWith(".json")).count();
                    playerCount.set(count);
                }
                
                logger.at(Level.INFO).log("JsonStorageProvider initialized with %d players", playerCount.get());
            } catch (IOException e) {
                logger.at(Level.SEVERE).log("Failed to initialize storage: %s", e.getMessage());
                throw new RuntimeException("Storage initialization failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Path playerFile = getPlayerFile(playerUuid);
            
            if (!Files.exists(playerFile)) {
                // Create new account with starting balance
                PlayerBalance newBalance = new PlayerBalance(playerUuid);
                newBalance.setBalance(Main.CONFIG.get().getStartingBalance(), "Initial balance");
                playerCount.incrementAndGet();
                return newBalance;
            }
            
            try {
                PlayerBalance balance = RawJsonReader.readSync(playerFile, PlayerBalance.CODEC, logger);
                if (balance != null) {
                    return balance;
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to load %s, trying backup: %s", playerUuid, e.getMessage());
                
                // Try backup file
                Path backupFile = getBackupFile(playerUuid);
                if (Files.exists(backupFile)) {
                    try {
                        PlayerBalance backup = RawJsonReader.readSync(backupFile, PlayerBalance.CODEC, logger);
                        if (backup != null) {
                            logger.at(Level.INFO).log("Restored %s from backup", playerUuid);
                            return backup;
                        }
                    } catch (Exception e2) {
                        logger.at(Level.SEVERE).log("Backup also failed for %s: %s", playerUuid, e2.getMessage());
                    }
                }
            }
            
            // Fallback: create new account
            logger.at(Level.WARNING).log("Creating new account for %s after load failure", playerUuid);
            PlayerBalance fallback = new PlayerBalance(playerUuid);
            fallback.setBalance(Main.CONFIG.get().getStartingBalance(), "Recovery - initial balance");
            return fallback;
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance) {
        return CompletableFuture.runAsync(() -> {
            Path playerFile = getPlayerFile(playerUuid);
            Path backupFile = getBackupFile(playerUuid);
            Path tempFile = getTempFile(playerUuid);
            
            try {
                // Step 1: Write to temp file first
                BsonUtil.writeSync(tempFile, PlayerBalance.CODEC, balance, logger);
                
                // Step 2: Backup existing file (if any)
                if (Files.exists(playerFile)) {
                    Files.move(playerFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Step 3: Atomic rename temp -> final
                Files.move(tempFile, playerFile, StandardCopyOption.ATOMIC_MOVE);
                
            } catch (IOException e) {
                logger.at(Level.SEVERE).log("Failed to save %s: %s", playerUuid, e.getMessage());
                
                // Cleanup temp file if it exists
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
                
                // Restore from backup if main file was corrupted
                if (!Files.exists(playerFile) && Files.exists(backupFile)) {
                    try {
                        Files.copy(backupFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.at(Level.INFO).log("Restored %s from backup after save failure", playerUuid);
                    } catch (IOException e2) {
                        logger.at(Level.SEVERE).log("Could not restore backup for %s", playerUuid);
                    }
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers) {
        if (dirtyPlayers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Save all dirty players in parallel
        CompletableFuture<?>[] futures = dirtyPlayers.entrySet().stream()
            .map(entry -> savePlayer(entry.getKey(), entry.getValue()))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures).thenRun(() -> {
            logger.at(Level.INFO).log("Saved %d player balances", dirtyPlayers.size());
        });
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerBalance>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerBalance> allBalances = new ConcurrentHashMap<>();
            
            try (Stream<Path> files = Files.list(PLAYERS_PATH)) {
                files.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".bak"))
                     .forEach(path -> {
                         String filename = path.getFileName().toString();
                         String uuidStr = filename.replace(".json", "");
                         try {
                             UUID uuid = UUID.fromString(uuidStr);
                             PlayerBalance balance = RawJsonReader.readSync(path, PlayerBalance.CODEC, logger);
                             if (balance != null) {
                                 allBalances.put(uuid, balance);
                             }
                         } catch (Exception e) {
                             logger.at(Level.WARNING).log("Skipping invalid file: %s", filename);
                         }
                     });
            } catch (IOException e) {
                logger.at(Level.SEVERE).log("Failed to list player files: %s", e.getMessage());
            }
            
            return allBalances;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> Files.exists(getPlayerFile(playerUuid)));
    }
    
    @Override
    public CompletableFuture<Void> deletePlayer(@Nonnull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(getPlayerFile(playerUuid));
                Files.deleteIfExists(getBackupFile(playerUuid));
                Files.deleteIfExists(getTempFile(playerUuid));
                playerCount.decrementAndGet();
                logger.at(Level.INFO).log("Deleted player data: %s", playerUuid);
            } catch (IOException e) {
                logger.at(Level.WARNING).log("Failed to delete player %s: %s", playerUuid, e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            logger.at(Level.INFO).log("JsonStorageProvider shutdown complete");
        });
    }
    
    @Override
    public String getName() {
        return "JSON (per-player files)";
    }
    
    @Override
    public int getPlayerCount() {
        return playerCount.get();
    }
    
    // ========== Helper Methods ==========
    
    private Path getPlayerFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json");
    }
    
    private Path getBackupFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json.bak");
    }
    
    private Path getTempFile(UUID uuid) {
        return PLAYERS_PATH.resolve(uuid.toString() + ".json.tmp");
    }
    
    // ========== Legacy Migration ==========
    
    /**
     * Migrate from old single-file format (balances.json) to per-player files.
     * This runs once on first startup after upgrade.
     */
    private void migrateLegacyFormat() {
        logger.at(Level.INFO).log("Migrating from legacy balances.json format...");
        
        try {
            // Import the old BalanceStorage class for reading legacy format
            com.ecotale.economy.BalanceStorage legacyStorage = 
                RawJsonReader.readSync(LEGACY_PATH, com.ecotale.economy.BalanceStorage.CODEC, logger);
            
            if (legacyStorage != null && legacyStorage.getBalances() != null) {
                int migrated = 0;
                for (PlayerBalance balance : legacyStorage.getBalances()) {
                    Path playerFile = getPlayerFile(balance.getPlayerUuid());
                    BsonUtil.writeSync(playerFile, PlayerBalance.CODEC, balance, logger);
                    migrated++;
                }
                
                // Rename old file to mark as migrated
                Path migratedPath = ECOTALE_PATH.resolve("balances.json.migrated");
                Files.move(LEGACY_PATH, migratedPath, StandardCopyOption.REPLACE_EXISTING);
                
                logger.at(Level.INFO).log("Migration complete: %d players migrated", migrated);
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Migration failed: %s", e.getMessage());
            logger.at(Level.WARNING).log("Legacy file preserved, manual migration may be needed");
        }
    }
}
