package com.ecotale.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter for economy operations.
 * 
 * Purpose:
 * - Allow legitimate bursts (e.g., mob farm giving 5 rewards quickly)
 * - Prevent infinite spam from malicious/buggy plugins
 * - Developer-friendly: high burst capacity, reasonable sustained rate
 * 
 * Algorithm:
 * - Each player has a "bucket" of tokens (default: 50)
 * - Each operation consumes 1 token
 * - Tokens refill continuously at REFILL_RATE per second
 * - If bucket is empty, operation is rejected
 * 
 * Configuration:
 * - MAX_TOKENS: 50 (burst capacity)
 * - REFILL_RATE: 10 tokens/second (sustained rate)
 * 
 * Example scenarios:
 * - Developer calls deposit() 30 times instantly → All succeed (burst)
 * - Developer calls deposit() 60 times in 1 second → First 50 succeed, 10 fail
 * - Developer waits 5 seconds → Bucket refills to 50 tokens
 */
public class RateLimiter {
    
    private final int maxTokens;
    private final int refillRate;
    private final ConcurrentHashMap<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    /**
     * Create a rate limiter with default settings.
     * Burst: 50 operations, Sustained: 10/second
     */
    public RateLimiter() {
        this(50, 10);
    }
    
    /**
     * Create a rate limiter with custom settings.
     * 
     * @param maxTokens Maximum burst capacity
     * @param refillRate Tokens added per second
     */
    public RateLimiter(int maxTokens, int refillRate) {
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
    }
    
    /**
     * Try to acquire a token for an operation.
     * 
     * @param playerUuid The player's UUID
     * @return true if operation is allowed, false if rate limited
     */
    public boolean tryAcquire(UUID playerUuid) {
        return buckets.computeIfAbsent(playerUuid, k -> new TokenBucket(maxTokens, refillRate))
                      .tryConsume();
    }
    
    /**
     * Get remaining tokens for a player (for debugging/admin).
     */
    public int getRemainingTokens(UUID playerUuid) {
        TokenBucket bucket = buckets.get(playerUuid);
        if (bucket == null) {
            return maxTokens;
        }
        bucket.refill();
        return (int) bucket.tokens;
    }
    
    /**
     * Reset a player's bucket to full (admin use).
     */
    public void resetBucket(UUID playerUuid) {
        buckets.remove(playerUuid);
    }
    
    /**
     * Cleanup old buckets (call periodically to free memory).
     * Removes buckets that are full (haven't been used recently).
     */
    public void cleanup() {
        buckets.entrySet().removeIf(entry -> {
            TokenBucket bucket = entry.getValue();
            bucket.refill();
            return bucket.tokens >= maxTokens;
        });
    }
    
    /**
     * Individual token bucket for a player.
     */
    private static class TokenBucket {
        private final int maxTokens;
        private final int refillRate;
        private double tokens;
        private long lastRefillTime;
        
        TokenBucket(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.tokens = maxTokens; // Start full
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        /**
         * Try to consume a token.
         * Thread-safe via synchronization.
         */
        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
        
        /**
         * Refill tokens based on elapsed time.
         */
        void refill() {
            long now = System.currentTimeMillis();
            double elapsedSeconds = (now - lastRefillTime) / 1000.0;
            
            if (elapsedSeconds > 0) {
                tokens = Math.min(maxTokens, tokens + elapsedSeconds * refillRate);
                lastRefillTime = now;
            }
        }
    }
}
