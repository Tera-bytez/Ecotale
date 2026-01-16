package com.ecotale.economy;

import com.ecotale.storage.H2StorageProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe transaction logger with dual storage:
 * - Ring buffer: Fast in-memory access for Dashboard (last 500)
 * - H2 Database: Persistent storage for LOG tab (unlimited)
 * 
 * Performance characteristics:
 * - Write: O(1) lock-free to ring buffer, async to H2
 * - Read (recent): O(n) from ring buffer
 * - Read (history): SQL query from H2
 */
public class TransactionLogger {
    
    private static final int DEFAULT_BUFFER_SIZE = 500;
    
    private final TransactionEntry[] buffer;
    private final int bufferSize;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger totalWrites = new AtomicInteger(0);
    
    // H2 storage for persistence
    private H2StorageProvider h2Storage;
    
    // Singleton instance
    private static TransactionLogger instance;
    
    public TransactionLogger() {
        this(DEFAULT_BUFFER_SIZE);
    }
    
    public TransactionLogger(int bufferSize) {
        this.bufferSize = bufferSize;
        this.buffer = new TransactionEntry[bufferSize];
    }
    
    public static TransactionLogger getInstance() {
        if (instance == null) {
            instance = new TransactionLogger();
        }
        return instance;
    }
    
    public static void setInstance(TransactionLogger logger) {
        instance = logger;
    }
    
    /**
     * Set the H2 storage provider for persistent logging.
     * Called by EconomyManager after initialization.
     */
    public void setH2Storage(H2StorageProvider storage) {
        this.h2Storage = storage;
    }
    
    // ========== Logging Methods ==========
    
    /**
     * Log a single-player action (give, take, set, reset).
     */
    public void logAction(TransactionType type, UUID player, String playerName, double amount) {
        log(TransactionEntry.single(type, player, playerName, amount));
    }
    
    /**
     * Log a transfer between players.
     */
    public void logTransfer(UUID from, String fromName, UUID to, String toName, double amount) {
        log(TransactionEntry.transfer(from, fromName, to, toName, amount));
    }
    
    /**
     * Internal log method - writes to ring buffer AND H2.
     */
    private void log(TransactionEntry entry) {
        // Write to ring buffer (fast, for Dashboard)
        int idx = writeIndex.getAndIncrement() % bufferSize;
        buffer[idx] = entry;
        totalWrites.incrementAndGet();
        
        // Write to H2 (async, for persistent LOG tab)
        if (h2Storage != null) {
            h2Storage.logTransaction(entry);
        }
    }
    
    // ========== Reading Methods ==========
    
    /**
     * Get the most recent entries in reverse chronological order.
     * 
     * @param count Maximum number of entries to return
     * @return List of entries, newest first. May be smaller than count if buffer not full.
     */
    public List<TransactionEntry> getRecent(int count) {
        int total = totalWrites.get();
        int available = Math.min(total, bufferSize);
        int toReturn = Math.min(count, available);
        
        if (toReturn == 0) {
            return Collections.emptyList();
        }
        
        List<TransactionEntry> result = new ArrayList<>(toReturn);
        int currentIdx = (writeIndex.get() - 1 + bufferSize) % bufferSize;
        
        for (int i = 0; i < toReturn; i++) {
            TransactionEntry entry = buffer[currentIdx];
            if (entry != null) {
                result.add(entry);
            }
            currentIdx = (currentIdx - 1 + bufferSize) % bufferSize;
        }
        
        return result;
    }
    
    /**
     * Get all available entries in reverse chronological order.
     */
    public List<TransactionEntry> getAll() {
        return getRecent(bufferSize);
    }
    
    /**
     * Get recent entries for a specific player.
     * 
     * @param playerUuid The player to filter by
     * @param limit Maximum entries to return
     * @return List of entries involving the player, newest first
     */
    public List<TransactionEntry> getRecentForPlayer(UUID playerUuid, int limit) {
        List<TransactionEntry> all = getRecent(bufferSize);
        List<TransactionEntry> filtered = new ArrayList<>();
        
        for (TransactionEntry entry : all) {
            if (entry.involvesPlayer(playerUuid)) {
                filtered.add(entry);
                if (filtered.size() >= limit) break;
            }
        }
        
        return filtered;
    }
    
    /**
     * Get statistics for monitoring.
     */
    public int getTotalTransactions() {
        return totalWrites.get();
    }
    
    public int getBufferSize() {
        return bufferSize;
    }
    
    public int getAvailableCount() {
        return Math.min(totalWrites.get(), bufferSize);
    }
    
    /**
     * Clear all entries (for testing).
     */
    public void clear() {
        for (int i = 0; i < bufferSize; i++) {
            buffer[i] = null;
        }
        writeIndex.set(0);
        totalWrites.set(0);
    }
}
