package com.ecotale.economy;

import com.ecotale.Main;
import com.ecotale.api.events.BalanceChangeEvent;
import com.ecotale.api.events.EcotaleEvents;
import com.ecotale.api.events.TransactionEvent;
import com.ecotale.storage.H2StorageProvider;
import com.ecotale.storage.JsonStorageProvider;
import com.ecotale.storage.MySQLStorageProvider;
import com.ecotale.storage.StorageProvider;
import com.ecotale.systems.BalanceHudSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Thread-safe economy manager with:
 * - Per-player locking for atomic operations
 * - Dirty tracking for efficient saves
 * - Async auto-save via StorageProvider
 * - Race condition prevention in transfers
 * - Transaction logging for activity monitoring
 * - Leaderboard caching for performance
 * - Lock eviction for memory management
 * 
 * Performance optimizations:
 * - PERF-01: Bulk preload on startup
 * - PERF-02: Leaderboard cache with rate-limit
 * - PERF-03: Lock eviction for offline players
 */
public class EconomyManager {
    
    // In-memory cache of loaded balances
    private final ConcurrentHashMap<UUID, PlayerBalance> cache = new ConcurrentHashMap<>();
    
    // Per-player locks for atomic operations
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    
    // Tracks which players have unsaved changes
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    
    // Storage backend (H2 or JSON based on config)
    private final StorageProvider storage;
    
    // Transaction logger for activity monitoring
    private final TransactionLogger transactionLogger = TransactionLogger.getInstance();
    
    // Leaderboard cache
    private volatile List<Map.Entry<UUID, PlayerBalance>> cachedLeaderboard;
    private volatile long lastLeaderboardRebuild = 0;
    
    /** Time in milliseconds to cache leaderboard data before rebuilding */
    private static final long LEADERBOARD_CACHE_MS = 2000;
    
    /** Maximum number of entries to cache in the leaderboard for performance */
    private static final int MAX_LEADERBOARD_CACHE_SIZE = 100;
    
    /** Number of characters to show when displaying truncated UUIDs */
    private static final int UUID_PREVIEW_LENGTH = 8;
    
    // Lock eviction timing
    private volatile long lastLockCleanup = System.currentTimeMillis();
    
    /** Time in milliseconds between lock cleanup cycles (30 minutes) */
    private static final long LOCK_CLEANUP_INTERVAL_MS = 30 * 60 * 1000;
    
    // Delayed cache eviction for reconnect protection
    private final ConcurrentHashMap<UUID, Long> pendingEvictions = new ConcurrentHashMap<>();
    
    /** Delay before evicting disconnected players from cache (5 minutes) */
    private static final long EVICTION_DELAY_MS = 5 * 60 * 1000;
    
    /** Maximum players in cache to prevent unbounded growth */
    private static final int MAX_CACHE_SIZE = 1000;
    
    // Auto-save executor
    private final ScheduledExecutorService saveExecutor;
    private final HytaleLogger logger;
    
    public EconomyManager(@Nonnull Object plugin) {
        this.logger = HytaleLogger.getLogger().getSubLogger("Ecotale");
        
        // Initialize storage provider based on config
        String providerType = Main.CONFIG.get().getStorageProvider().toLowerCase();
        switch (providerType) {
            case "mysql" -> {
                this.storage = new MySQLStorageProvider();
                logger.at(Level.INFO).log("Using MySQL storage provider (shared database)");
            }
            case "json" -> {
                this.storage = new JsonStorageProvider();
                logger.at(Level.INFO).log("Using JSON storage provider");
            }
            default -> {
                // H2 is default for reliability and transaction logging
                H2StorageProvider h2 = new H2StorageProvider();
                this.storage = h2;
                // Connect TransactionLogger to H2 for persistent logging
                transactionLogger.setH2Storage(h2);
                logger.at(Level.INFO).log("Using H2 storage provider");
            }
        }
        storage.initialize().join();
        
        // Lazy loading: players are loaded when they join instead of all at once
        // bulkPreload();
        
        // Start auto-save using virtual threads (Java 25)
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("Ecotale-Economy-Maintenance-", 0).factory()
        );
        
        long interval = Main.CONFIG.get().getAutoSaveInterval();
        this.saveExecutor.scheduleAtFixedRate(this::performMaintenance, 
            interval, interval, TimeUnit.SECONDS);
        
        logger.at(Level.INFO).log("EconomyManager initialized with %s (lazy loading enabled, auto-save: %ds)", 
            storage.getName(), interval);
    }
    
    // ========== Lock Management ==========
    
    /**
     * Get or create a lock for a specific player.
     * Locks are cached and reused for the same player.
     */
    private ReentrantLock getLock(UUID playerUuid) {
        return playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
    }
    
    // ========== Player Account Management ==========
    
    /**
     * Ensure a player has an account (load from storage or create new).
     * This is called when a player joins the server.
     * Cancels any pending eviction if the player reconnects.
     */
    public void ensureAccount(@Nonnull UUID playerUuid) {
        // Cancel pending eviction if reconnecting
        pendingEvictions.remove(playerUuid);
        
        cache.computeIfAbsent(playerUuid, uuid -> {
            // Load from storage or create new
            PlayerBalance balance = storage.loadPlayer(uuid).join();
            dirtyPlayers.add(uuid);
            return balance;
        });
    }
    
    /**
     * Get an account, loading from storage if not in cache.
     */
    private PlayerBalance getOrLoadAccount(@Nonnull UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, uuid -> 
            storage.loadPlayer(uuid).join()
        );
    }
    
    // ========== Balance Operations ==========
    
    public double getBalance(@Nonnull UUID playerUuid) {
        PlayerBalance balance = cache.get(playerUuid);
        return balance != null ? balance.getBalance() : 0.0;
    }
    
    public PlayerBalance getPlayerBalance(@Nonnull UUID playerUuid) {
        return cache.get(playerUuid);
    }
    
    public boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        return getBalance(playerUuid) >= amount;
    }
    
    /**
     * Deposit money into a player's account.
     * Thread-safe with per-player locking.
     * Rejects if would exceed maxBalance.
     * Fires BalanceChangeEvent (cancellable).
     */
    public boolean deposit(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = getLock(playerUuid);
        lock.lock();
        try {
            PlayerBalance balance = getOrLoadAccount(playerUuid);
            if (balance == null) return false;
            
            double oldBalance = balance.getBalance();
            double newBalance = oldBalance + amount;
            
            // Fire cancellable event
            BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(
                playerUuid, oldBalance, newBalance, 
                BalanceChangeEvent.Cause.DEPOSIT, reason != null ? reason : "Deposit"
            ));
            if (event.isCancelled()) return false;
            
            if (balance.deposit(amount, reason)) {
                dirtyPlayers.add(playerUuid);
                BalanceHudSystem.updatePlayerHud(playerUuid, balance.getBalance());
                
                // Log transaction (skip internal transfer logs)
                if (reason != null && !reason.startsWith("Transfer")) {
                    TransactionType type = reason.startsWith("Admin") 
                        ? TransactionType.GIVE : TransactionType.EARN;
                    transactionLogger.logAction(type, playerUuid, 
                        resolvePlayerName(playerUuid), amount);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Withdraw money from a player's account.
     * Thread-safe with per-player locking.
     * Fires BalanceChangeEvent (cancellable).
     */
    public boolean withdraw(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = getLock(playerUuid);
        lock.lock();
        try {
            PlayerBalance balance = cache.get(playerUuid);
            if (balance == null) return false;
            
            double oldBalance = balance.getBalance();
            double newBalance = oldBalance - amount;
            
            // Fire cancellable event
            BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(
                playerUuid, oldBalance, newBalance,
                BalanceChangeEvent.Cause.WITHDRAW, reason != null ? reason : "Withdraw"
            ));
            if (event.isCancelled()) return false;
            
            if (balance.withdraw(amount, reason)) {
                dirtyPlayers.add(playerUuid);
                BalanceHudSystem.updatePlayerHud(playerUuid, balance.getBalance());
                
                // Log transaction (skip internal transfer logs)
                if (reason != null && !reason.startsWith("Transfer")) {
                    TransactionType type = reason.startsWith("Admin") 
                        ? TransactionType.TAKE : TransactionType.SPEND;
                    transactionLogger.logAction(type, playerUuid, 
                        resolvePlayerName(playerUuid), amount);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Set a player's balance to a specific amount.
     * Thread-safe with per-player locking.
     * Fires BalanceChangeEvent (cancellable).
     */
    public void setBalance(@Nonnull UUID playerUuid, double amount, String reason) {
        ReentrantLock lock = getLock(playerUuid);
        lock.lock();
        try {
            PlayerBalance balance = getOrLoadAccount(playerUuid);
            if (balance != null) {
                double oldBalance = balance.getBalance();
                
                // Fire cancellable event
                BalanceChangeEvent event = EcotaleEvents.fire(new BalanceChangeEvent(
                    playerUuid, oldBalance, amount,
                    BalanceChangeEvent.Cause.ADMIN, reason != null ? reason : "Set balance"
                ));
                if (event.isCancelled()) return;
                
                balance.setBalance(amount, reason);
                dirtyPlayers.add(playerUuid);
                BalanceHudSystem.updatePlayerHud(playerUuid, amount);
                
                // Log transaction
                TransactionType type = (reason != null && reason.contains("reset")) 
                    ? TransactionType.RESET : TransactionType.SET;
                transactionLogger.logAction(type, playerUuid, resolvePlayerName(playerUuid), amount);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Transfer money between two players.
     * ATOMIC: Either both operations succeed or neither does.
     * Uses ordered lock acquisition to prevent deadlocks.
     * 
     * Security: Fixes SEC-01 (race condition) and DATA-01 (non-atomic transfer)
     */
    public TransferResult transfer(@Nonnull UUID from, @Nonnull UUID to, double amount, String reason) {
        if (from.equals(to)) {
            return TransferResult.SELF_TRANSFER;
        }
        
        if (amount <= 0) {
            return TransferResult.INVALID_AMOUNT;
        }
        
        // Calculate total with fee
        double fee = amount * Main.CONFIG.get().getTransferFee();
        double total = amount + fee;
        
        // CRITICAL: Ordered lock acquisition to prevent deadlock
        // Always lock the "smaller" UUID first (consistent ordering)
        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = from.compareTo(to) < 0 ? to : from;
        
        ReentrantLock lock1 = getLock(first);
        ReentrantLock lock2 = getLock(second);
        
        lock1.lock();
        try {
            lock2.lock();
            try {
                // Get both balances (ensure accounts exist)
                PlayerBalance fromBalance = getOrLoadAccount(from);
                PlayerBalance toBalance = getOrLoadAccount(to);
                
                // Check sufficient funds INSIDE the lock
                if (fromBalance == null || !fromBalance.hasBalance(total)) {
                    return TransferResult.INSUFFICIENT_FUNDS;
                }
                
                // Check recipient can receive (maxBalance)
                double maxBalance = Main.CONFIG.get().getMaxBalance();
                if (toBalance != null && toBalance.getBalance() + amount > maxBalance) {
                    return TransferResult.RECIPIENT_MAX_BALANCE;
                }
                
                // ATOMIC: Both operations under lock
                fromBalance.withdrawInternal(total, "Transfer to " + to + ": " + reason);
                toBalance.depositInternal(amount, "Transfer from " + from + ": " + reason);
                
                // Mark both as dirty
                dirtyPlayers.add(from);
                dirtyPlayers.add(to);
                
                // Update HUDs
                BalanceHudSystem.updatePlayerHud(from, fromBalance.getBalance());
                BalanceHudSystem.updatePlayerHud(to, toBalance.getBalance());
                
                // Log transfer
                transactionLogger.logTransfer(from, resolvePlayerName(from), 
                    to, resolvePlayerName(to), amount);
                
                return TransferResult.SUCCESS;
                
            } finally {
                lock2.unlock();
            }
        } finally {
            lock1.unlock();
        }
    }
    
    // ========== Bulk Operations ==========
    
    /**
     * Get all balances (for leaderboards).
     * Returns a snapshot copy to prevent external modification.
     */
    public Map<UUID, PlayerBalance> getAllBalances() {
        return new HashMap<>(cache);
    }
    
    /**
     * Get the number of cached players.
     */
    public int getCachedPlayerCount() {
        return cache.size();
    }
    
    // ========== Persistence ==========
    
    /**
     * Mark a player as needing to be saved.
     */
    public void markDirty(@Nonnull UUID playerUuid) {
        dirtyPlayers.add(playerUuid);
    }
    
    /**
     * Force save all dirty players immediately.
     */
    public void forceSave() {
        saveDirtyPlayers();
    }
    
    /**
     * Periodic maintenance task:
     * 1. Save dirty players
     * 2. Cleanup stale locks
     * 3. Cleanup rate limiter
     * 4. Process pending cache evictions
     */
    private void performMaintenance() {
        try {
            // 1. Save dirty players
            if (!dirtyPlayers.isEmpty()) {
                saveDirtyPlayers();
            }
            
            // 2 & 3. Lock and RateLimit cleanup (every 30 min)
            long now = System.currentTimeMillis();
            if (now - lastLockCleanup > LOCK_CLEANUP_INTERVAL_MS) {
                cleanupStaleLocks();
                lastLockCleanup = now;
                com.ecotale.api.EcotaleAPI.cleanupRateLimiter();
            }
            
            // 4. Process delayed cache evictions
            processPendingEvictions();
            
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Error in maintenance task: %s", e.getMessage());
        }
    }
    
    /**
     * Save all dirty players asynchronously.
     */
    private void saveDirtyPlayers() {
        if (dirtyPlayers.isEmpty()) {
            return;
        }
        
        // Snapshot and clear dirty set
        Set<UUID> toSave = new HashSet<>(dirtyPlayers);
        dirtyPlayers.clear();
        
        // Build map of dirty players
        Map<UUID, PlayerBalance> dirty = new HashMap<>();
        for (UUID uuid : toSave) {
            PlayerBalance balance = cache.get(uuid);
            if (balance != null) {
                dirty.put(uuid, balance);
            }
        }
        
        // Save asynchronously
        storage.saveAll(dirty).exceptionally(e -> {
            logger.at(Level.SEVERE).log("Auto-save failed: %s", e.getMessage());
            // Re-mark as dirty for retry on next cycle
            dirtyPlayers.addAll(toSave);
            return null;
        });
    }
    
    /**
     * Shutdown the economy manager.
     * Saves all dirty players and stops the auto-save thread.
     */
    public void shutdown() {
        logger.at(Level.INFO).log("EconomyManager shutdown starting... (%d dirty, %d cached)", 
            dirtyPlayers.size(), cache.size());
        
        // Shut down scheduled maintenance
        if (saveExecutor != null) {
            saveExecutor.shutdown();
            try {
                if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveExecutor.shutdownNow();
            }
        }
        
        // Save ALL cached players on shutdown (not just dirty) to ensure nothing is lost
        // Use SYNC save to avoid executor issues during server shutdown
        if (!cache.isEmpty()) {
            logger.at(Level.INFO).log("Saving %d player balances...", cache.size());
            try {
                if (storage instanceof H2StorageProvider h2) {
                    // Use sync method directly - bypasses executor which may be killed during shutdown
                    h2.saveAllSync(cache);
                    logger.at(Level.INFO).log("Player balances saved successfully (sync)");
                } else {
                    // For other providers, use async with timeout as fallback
                    storage.saveAll(cache).get(10, java.util.concurrent.TimeUnit.SECONDS);
                    logger.at(Level.INFO).log("Player balances saved successfully");
                }
            } catch (java.util.concurrent.TimeoutException e) {
                logger.at(Level.WARNING).log("Save operation timed out after 10 seconds - data may be lost");
            } catch (Exception e) {
                logger.at(Level.SEVERE).log("Error saving player balances: %s", e.getMessage());
            }
        }
        
        // Shutdown storage provider
        logger.at(Level.INFO).log("Shutting down storage provider...");
        try {
            storage.shutdown().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.at(Level.WARNING).log("Storage shutdown timed out after 5 seconds");
        } catch (Exception e) {
            logger.at(Level.SEVERE).log("Error during storage shutdown: %s", e.getMessage());
        }
        
        logger.at(Level.INFO).log("EconomyManager shutdown complete");
    }
    
    // ========== Storage Access ==========
    
    /**
     * Get the storage provider.
     */
    public StorageProvider getStorage() {
        return storage;
    }
    
    /**
     * Get the H2 storage provider for direct queries (transaction logs, etc).
     * Returns null if not using H2.
     */
    public H2StorageProvider getH2Storage() {
        return storage instanceof H2StorageProvider h2 ? h2 : null;
    }
    
    /**
     * Get the transaction logger for activity monitoring.
     */
    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }
    
    // ========== Performance Optimizations ==========
    
    /**
     * PERF-01: Bulk preload all player data on startup.
     * Avoids blocking .join() calls during player joins.
     */
    private void bulkPreload() {
        try {
            Map<UUID, PlayerBalance> all = storage.loadAll().join();
            cache.putAll(all);
            logger.at(Level.INFO).log("Bulk preloaded %d player balances", all.size());
        } catch (Exception e) {
            logger.at(Level.WARNING).log("Bulk preload failed, will load on-demand: %s", e.getMessage());
        }
    }
    
    /**
     * PERF-02: Get leaderboard with caching and rate-limit.
     * Avoids sorting entire cache on every request.
     * 
     * @param limit Maximum number of entries to return
     * @return Sorted list of top players by balance
     */
    public List<Map.Entry<UUID, PlayerBalance>> getLeaderboard(int limit) {
        long now = System.currentTimeMillis();
        
        // Rebuild if cache is stale or null
        if (cachedLeaderboard == null || now - lastLeaderboardRebuild > LEADERBOARD_CACHE_MS) {
            cachedLeaderboard = cache.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getBalance(), a.getValue().getBalance()))
                .limit(MAX_LEADERBOARD_CACHE_SIZE)
                .collect(Collectors.toList());
            lastLeaderboardRebuild = now;
        }
        
        // Return requested limit from cache
        return cachedLeaderboard.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * PERF-03: Clean up locks for offline players.
     * Prevents unbounded growth of playerLocks map.
     */
    private void cleanupStaleLocks() {
        Set<UUID> onlinePlayers = Universe.get().getPlayers().stream()
            .map(p -> p.getUuid())
            .collect(Collectors.toSet());
        
        int removed = 0;
        for (UUID uuid : new HashSet<>(playerLocks.keySet())) {
            if (!onlinePlayers.contains(uuid)) {
                ReentrantLock lock = playerLocks.get(uuid);
                // Only remove if not currently held
                if (lock != null && !lock.isLocked()) {
                    playerLocks.remove(uuid);
                    removed++;
                }
            }
        }
        
        if (removed > 0) {
            logger.at(Level.FINE).log("Cleaned up %d stale player locks", removed);
        }
    }
    
    /**
     * Schedule a player for cache eviction after delay.
     * Called when player disconnects.
     */
    public void scheduleEviction(@Nonnull UUID playerUuid) {
        // Save any pending changes first
        if (dirtyPlayers.contains(playerUuid)) {
            PlayerBalance balance = cache.get(playerUuid);
            if (balance != null) {
                storage.savePlayer(playerUuid, balance);
                dirtyPlayers.remove(playerUuid);
            }
        }
        
        // Schedule for eviction after delay
        pendingEvictions.put(playerUuid, System.currentTimeMillis() + EVICTION_DELAY_MS);
    }
    
    /**
     * Process pending evictions and enforce max cache size.
     * Called periodically from autoSaveLoop.
     */
    private void processPendingEvictions() {
        long now = System.currentTimeMillis();
        int evicted = 0;
        
        // Process delayed evictions
        for (var entry : new java.util.HashMap<>(pendingEvictions).entrySet()) {
            if (now >= entry.getValue()) {
                UUID uuid = entry.getKey();
                pendingEvictions.remove(uuid);
                
                // Only evict if not currently online
                if (Universe.get().getPlayer(uuid) == null) {
                    cache.remove(uuid);
                    playerLocks.remove(uuid);
                    evicted++;
                }
            }
        }
        
        // Enforce max cache size by evicting oldest offline players
        if (cache.size() > MAX_CACHE_SIZE) {
            Set<UUID> onlinePlayers = Universe.get().getPlayers().stream()
                .map(PlayerRef::getUuid)
                .collect(Collectors.toSet());
            
            for (UUID uuid : new java.util.ArrayList<>(cache.keySet())) {
                if (cache.size() <= MAX_CACHE_SIZE) break;
                if (!onlinePlayers.contains(uuid)) {
                    cache.remove(uuid);
                    playerLocks.remove(uuid);
                    evicted++;
                }
            }
        }
        
        if (evicted > 0) {
            logger.at(Level.FINE).log("Evicted %d players from cache (size: %d)", evicted, cache.size());
        }
    }
    
    /**
     * Resolve player name for logging purposes.
     * Falls back to truncated UUID if player is offline.
     */
    private String resolvePlayerName(UUID uuid) {
        var player = Universe.get().getPlayer(uuid);
        if (player != null) {
            return player.getUsername();
        }
        return uuid.toString().substring(0, UUID_PREVIEW_LENGTH) + "...";
    }
    
    // ========== Result Enums ==========
    
    public enum TransferResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        SELF_TRANSFER,
        INVALID_AMOUNT,
        RECIPIENT_MAX_BALANCE
    }
}
