package com.ecotale.api;

import com.ecotale.economy.EconomyManager;
import com.ecotale.util.RateLimiter;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Public API for Ecotale economy plugin.
 * 
 * Other plugins can use this API to interact with the economy system.
 * 
 * Rate Limiting:
 * - All write operations (deposit, withdraw, transfer, setBalance) are rate limited
 * - Default: 50 burst capacity, 10 operations/second sustained
 * - Read operations (getBalance, hasBalance) are NOT rate limited
 * - Throws EcotaleRateLimitException if rate limit exceeded
 * 
 * Thread Safety:
 * - All methods are thread-safe
 * - Atomic transfers prevent race conditions
 * 
 * Example usage:
 * <pre>
 * // Check if API is available
 * if (EcotaleAPI.isAvailable()) {
 *     // Read balance (no rate limit)
 *     double balance = EcotaleAPI.getBalance(playerUuid);
 *     
 *     // Deposit with rate limiting
 *     try {
 *         boolean success = EcotaleAPI.deposit(playerUuid, 100.0, "Quest reward");
 *     } catch (EcotaleRateLimitException e) {
 *         // Handle rate limit - wait and retry
 *         Thread.sleep(e.getRetryAfterMs());
 *     }
 * }
 * </pre>
 */
public final class EcotaleAPI {
    
    private static EconomyManager economyManager;
    private static RateLimiter rateLimiter;
    
    private EcotaleAPI() {}
    
    /**
     * Initialize the API (called by Ecotale plugin on startup).
     */
    public static void init(@Nonnull EconomyManager manager) {
        economyManager = manager;
        rateLimiter = new RateLimiter(); // Default: 50 burst, 10/sec
    }
    
    /**
     * Initialize with custom rate limit settings.
     */
    public static void init(@Nonnull EconomyManager manager, int rateLimitBurst, int rateLimitRefill) {
        economyManager = manager;
        rateLimiter = new RateLimiter(rateLimitBurst, rateLimitRefill);
    }
    
    /**
     * Check if the API is available and ready for use.
     */
    public static boolean isAvailable() {
        return economyManager != null;
    }
    
    // ========== Read Operations (No Rate Limit) ==========
    
    /**
     * Get a player's current balance.
     * NOT rate limited.
     */
    public static double getBalance(@Nonnull UUID playerUuid) {
        validateAvailable();
        return economyManager.getBalance(playerUuid);
    }
    
    /**
     * Check if a player has at least the specified amount.
     * NOT rate limited.
     */
    public static boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        validateAvailable();
        return economyManager.hasBalance(playerUuid, amount);
    }
    
    /**
     * Get the currency symbol (e.g., "$").
     * NOT rate limited.
     */
    public static String getCurrencySymbol() {
        return com.ecotale.Main.CONFIG.get().getCurrencySymbol();
    }
    
    /**
     * Get the currency name (e.g., "Coins").
     * NOT rate limited.
     */
    public static String getCurrencyName() {
        return com.ecotale.Main.CONFIG.get().getCurrencyName();
    }
    
    /**
     * Format an amount with currency symbol.
     * NOT rate limited.
     */
    public static String format(double amount) {
        return com.ecotale.Main.CONFIG.get().format(amount);
    }
    
    // ========== Write Operations (Rate Limited) ==========
    
    /**
     * Deposit money into a player's account.
     * 
     * Rate limited: 50 burst, 10/second sustained.
     * 
     * @param playerUuid Target player
     * @param amount Amount to deposit (must be positive)
     * @param reason Reason for transaction (for logging)
     * @return true if successful, false if rejected (invalid amount or exceeds maxBalance)
     * @throws EcotaleRateLimitException if rate limit exceeded
     */
    public static boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        validateAvailable();
        checkRateLimit(playerUuid);
        return economyManager.deposit(playerUuid, amount, reason);
    }
    
    /**
     * Withdraw money from a player's account.
     * 
     * Rate limited: 50 burst, 10/second sustained.
     * 
     * @param playerUuid Target player
     * @param amount Amount to withdraw (must be positive and <= balance)
     * @param reason Reason for transaction (for logging)
     * @return true if successful, false if insufficient funds
     * @throws EcotaleRateLimitException if rate limit exceeded
     */
    public static boolean withdraw(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        validateAvailable();
        checkRateLimit(playerUuid);
        return economyManager.withdraw(playerUuid, amount, reason);
    }
    
    /**
     * Transfer money between two players.
     * 
     * Rate limited: applies to sender only.
     * Atomic: either both operations succeed or neither does.
     * 
     * @param from Sender's UUID
     * @param to Recipient's UUID
     * @param amount Amount to transfer (before fees)
     * @param reason Reason for transfer
     * @return TransferResult indicating success or failure reason
     * @throws EcotaleRateLimitException if rate limit exceeded
     */
    public static EconomyManager.TransferResult transfer(@Nonnull UUID from, @Nonnull UUID to, 
                                                          double amount, @Nonnull String reason) {
        validateAvailable();
        checkRateLimit(from); // Rate limit based on sender
        return economyManager.transfer(from, to, amount, reason);
    }
    
    /**
     * Set a player's balance to a specific amount.
     * Intended for admin/console use only.
     * 
     * Rate limited: 50 burst, 10/second sustained.
     * 
     * @param playerUuid Target player
     * @param amount New balance amount
     * @param reason Reason for change (for audit logging)
     * @throws EcotaleRateLimitException if rate limit exceeded
     */
    public static void setBalance(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        validateAvailable();
        checkRateLimit(playerUuid);
        economyManager.setBalance(playerUuid, amount, reason);
    }
    
    // ========== Query Operations (No Rate Limit) ==========
    
    /**
     * Get the top N balances in the economy.
     * Useful for leaderboards.
     * NOT rate limited.
     * 
     * @param limit Maximum number of entries to return
     * @return List of PlayerBalance objects sorted by balance descending
     */
    public static java.util.List<com.ecotale.economy.PlayerBalance> getTopBalances(int limit) {
        validateAvailable();
        return economyManager.getAllBalances().values().stream()
            .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
            .limit(limit)
            .toList();
    }
    
    /**
     * Get all player UUIDs that have economy accounts.
     * NOT rate limited.
     * 
     * @return Set of all player UUIDs with accounts
     */
    public static java.util.Set<UUID> getAllPlayerUUIDs() {
        validateAvailable();
        return economyManager.getAllBalances().keySet();
    }
    
    /**
     * Get the total money circulating in the economy.
     * Useful for economy statistics.
     * NOT rate limited.
     * 
     * @return Total sum of all player balances
     */
    public static double getTotalCirculating() {
        validateAvailable();
        return economyManager.getAllBalances().values().stream()
            .mapToDouble(com.ecotale.economy.PlayerBalance::getBalance)
            .sum();
    }
    
    /**
     * Reset a player's balance to starting amount.
     * Rate limited.
     * 
     * @param playerUuid Target player
     * @param reason Reason for reset (for logging)
     * @throws EcotaleRateLimitException if rate limit exceeded
     */
    public static void resetBalance(@Nonnull UUID playerUuid, @Nonnull String reason) {
        validateAvailable();
        checkRateLimit(playerUuid);
        double startingBalance = com.ecotale.Main.CONFIG.get().getStartingBalance();
        economyManager.setBalance(playerUuid, startingBalance, reason);
    }
    
    /**
     * Get recent transaction history for a player.
     * Returns entries from the in-memory ring buffer.
     * NOT rate limited.
     * 
     * @param playerUuid Target player
     * @param limit Maximum number of entries
     * @return List of recent transactions
     */
    public static java.util.List<com.ecotale.economy.TransactionEntry> getTransactionHistory(
            @Nonnull UUID playerUuid, int limit) {
        validateAvailable();
        return economyManager.getTransactionLogger().getRecentForPlayer(playerUuid, limit);
    }
    
    // ========== Internal Methods ==========
    
    private static void validateAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("Ecotale is not initialized. " +
                "Ensure Ecotale plugin is loaded before calling the API.");
        }
    }
    
    private static void checkRateLimit(UUID playerUuid) {
        if (!rateLimiter.tryAcquire(playerUuid)) {
            throw new EcotaleRateLimitException(
                "Rate limit exceeded for player " + playerUuid + ". " +
                "Max 50 burst, 10/sec sustained. Wait 100ms and retry."
            );
        }
    }
}
