package com.ecotale.economy;

import com.ecotale.Main;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.UUID;

/**
 * Player balance data model with security improvements.
 * 
 * Security fixes implemented:
 * - SEC-02: MaxBalance validation in deposit() - rejects if exceeded
 * - Internal methods for atomic operations (package-private)
 * 
 * NOTE: BuilderCodec keys MUST start with uppercase (PascalCase)
 */
public class PlayerBalance {
    
    public static final BuilderCodec<PlayerBalance> CODEC = BuilderCodec.builder(PlayerBalance.class, PlayerBalance::new)
        .append(new KeyedCodec<>("Uuid", Codec.STRING),
            (p, v, extraInfo) -> p.playerUuid = UUID.fromString(v), 
            (p, extraInfo) -> p.playerUuid.toString()).add()
        .append(new KeyedCodec<>("Balance", Codec.DOUBLE),
            (p, v, extraInfo) -> p.balance = v, 
            (p, extraInfo) -> p.balance).add()
        .append(new KeyedCodec<>("TotalEarned", Codec.DOUBLE),
            (p, v, extraInfo) -> p.totalEarned = v, 
            (p, extraInfo) -> p.totalEarned).add()
        .append(new KeyedCodec<>("TotalSpent", Codec.DOUBLE),
            (p, v, extraInfo) -> p.totalSpent = v, 
            (p, extraInfo) -> p.totalSpent).add()
        .append(new KeyedCodec<>("LastTransaction", Codec.STRING),
            (p, v, extraInfo) -> p.lastTransaction = v, 
            (p, extraInfo) -> p.lastTransaction).add()
        .append(new KeyedCodec<>("LastTransactionTime", Codec.LONG),
            (p, v, extraInfo) -> p.lastTransactionTime = v, 
            (p, extraInfo) -> p.lastTransactionTime).add()
        .build();
    
    public static final ArrayCodec<PlayerBalance> ARRAY_CODEC = new ArrayCodec<>(CODEC, PlayerBalance[]::new, PlayerBalance::new);
    
    private UUID playerUuid;
    private double balance = 0;
    private double totalEarned = 0;
    private double totalSpent = 0;
    private String lastTransaction = "";
    private long lastTransactionTime = 0;
    
    public PlayerBalance() {}
    
    public PlayerBalance(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * Deposit money into this account.
     * 
     * Security: Rejects transaction if it would exceed maxBalance.
     * This prevents economy inflation exploits.
     * 
     * @param amount Amount to deposit (must be positive)
     * @param reason Reason for the transaction (for logging)
     * @return true if successful, false if rejected (invalid amount or max exceeded)
     */
    public boolean deposit(double amount, String reason) {
        if (amount <= 0) return false;
        
        // SEC-02: Enforce maxBalance - REJECT entire transaction
        double maxBalance = Main.CONFIG.get().getMaxBalance();
        if (this.balance + amount > maxBalance) {
            return false; // Reject - would exceed max balance
        }
        
        this.balance += amount;
        this.totalEarned += amount;
        this.lastTransaction = "+" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Withdraw money from this account.
     * 
     * @param amount Amount to withdraw (must be positive and <= balance)
     * @param reason Reason for the transaction
     * @return true if successful, false if insufficient funds
     */
    public boolean withdraw(double amount, String reason) {
        if (amount <= 0 || this.balance < amount) return false;
        this.balance -= amount;
        this.totalSpent += amount;
        this.lastTransaction = "-" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
        return true;
    }
    
    /**
     * Set balance to a specific amount (admin operations).
     * Enforces minimum of 0 but allows bypassing maxBalance for admin use.
     */
    public void setBalance(double amount, String reason) {
        this.balance = Math.max(0, amount);
        this.lastTransaction = "Set to " + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }
    
    // ========== Internal Methods (Package-Private) ==========
    // These are called ONLY from EconomyManager with locks held.
    // They bypass validation because the caller already verified.
    
    /**
     * Internal deposit - NO validation.
     * ONLY call from EconomyManager.transfer() with lock held.
     */
    void depositInternal(double amount, String reason) {
        this.balance += amount;
        this.totalEarned += amount;
        this.lastTransaction = "+" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }
    
    /**
     * Internal withdraw - NO validation.
     * ONLY call from EconomyManager.transfer() with lock held.
     */
    void withdrawInternal(double amount, String reason) {
        this.balance -= amount;
        this.totalSpent += amount;
        this.lastTransaction = "-" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }
    
    // ========== Getters ==========
    
    public UUID getPlayerUuid() { return playerUuid; }
    public double getBalance() { return balance; }
    public double getTotalEarned() { return totalEarned; }
    public double getTotalSpent() { return totalSpent; }
    public String getLastTransaction() { return lastTransaction; }
    public long getLastTransactionTime() { return lastTransactionTime; }
    
    public boolean hasBalance(double amount) {
        return this.balance >= amount;
    }
}
