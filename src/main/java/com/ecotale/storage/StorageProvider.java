package com.ecotale.storage;

import com.ecotale.economy.PlayerBalance;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage provider interface for economy data persistence.
 * Implementations handle the actual read/write operations.
 * 
 * Design: Provider Pattern allows swapping storage backends
 * without modifying core economy logic.
 * 
 * Available implementations:
 * - JsonStorageProvider: Per-player JSON files (default, zero deps)
 * - Future: SqliteStorageProvider, H2StorageProvider, etc.
 */
public interface StorageProvider {
    
    /**
     * Initialize the storage system.
     * Called once on plugin startup.
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Load a player's balance from storage.
     * Creates a new account with starting balance if not exists.
     * 
     * @param playerUuid The player's UUID
     * @return The player's balance data
     */
    CompletableFuture<PlayerBalance> loadPlayer(@Nonnull UUID playerUuid);
    
    /**
     * Save a single player's balance to storage.
     * Implements atomic write with backup rotation.
     * 
     * @param playerUuid The player's UUID
     * @param balance The balance data to save
     */
    CompletableFuture<Void> savePlayer(@Nonnull UUID playerUuid, @Nonnull PlayerBalance balance);
    
    /**
     * Batch save multiple players' balances.
     * More efficient than individual saves for auto-save.
     * 
     * @param dirtyPlayers Map of UUID to PlayerBalance for changed players
     */
    CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerBalance> dirtyPlayers);
    
    /**
     * Load all player balances.
     * Used for leaderboards and startup migration.
     * 
     * @return Map of all player UUIDs to their balances
     */
    CompletableFuture<Map<UUID, PlayerBalance>> loadAll();
    
    /**
     * Check if a player has saved data.
     * 
     * @param playerUuid The player's UUID
     * @return true if player data exists in storage
     */
    CompletableFuture<Boolean> playerExists(@Nonnull UUID playerUuid);
    
    /**
     * Delete a player's balance data.
     * Used for account resets or GDPR compliance.
     * 
     * @param playerUuid The player's UUID
     */
    CompletableFuture<Void> deletePlayer(@Nonnull UUID playerUuid);
    
    /**
     * Shutdown and cleanup resources.
     * Ensures all pending writes are flushed.
     */
    CompletableFuture<Void> shutdown();
    
    /**
     * Get the provider's display name for logging.
     */
    String getName();
    
    /**
     * Get the number of players with saved data.
     */
    int getPlayerCount();
}
