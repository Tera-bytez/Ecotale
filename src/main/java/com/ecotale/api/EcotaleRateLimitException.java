package com.ecotale.api;

/**
 * Exception thrown when API rate limit is exceeded.
 * 
 * This protects the economy system from abuse by:
 * - Malicious plugins spamming transactions
 * - Buggy plugins with infinite loops
 * - External exploits
 * 
 * Developers should catch this exception and implement
 * appropriate backoff/retry logic.
 */
public class EcotaleRateLimitException extends RuntimeException {
    
    private final int remainingTokens;
    private final int maxTokens;
    
    public EcotaleRateLimitException(String message) {
        super(message);
        this.remainingTokens = 0;
        this.maxTokens = 50;
    }
    
    public EcotaleRateLimitException(String message, int remainingTokens, int maxTokens) {
        super(message);
        this.remainingTokens = remainingTokens;
        this.maxTokens = maxTokens;
    }
    
    /**
     * Get the remaining tokens in the bucket.
     */
    public int getRemainingTokens() {
        return remainingTokens;
    }
    
    /**
     * Get the maximum token capacity.
     */
    public int getMaxTokens() {
        return maxTokens;
    }
    
    /**
     * Get the wait time in milliseconds until more tokens are available.
     * Approximately 100ms for 1 token at default 10/second rate.
     */
    public long getRetryAfterMs() {
        return 100; // 1 token refills every 100ms at 10/sec rate
    }
}
