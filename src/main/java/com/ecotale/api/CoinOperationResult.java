package com.ecotale.api;

import javax.annotation.Nonnull;

/**
 * Result object for coin operations.
 * 
 * Provides detailed information about success or failure,
 * enabling proper feedback to players and safe transaction handling.
 * 
 * @author Ecotale
 * @since 1.2.0
 */
public final class CoinOperationResult {
    
    /**
     * Status codes for coin operations.
     */
    public enum Status {
        /** Operation completed successfully. */
        SUCCESS,
        /** Player's inventory doesn't have enough space. */
        NOT_ENOUGH_SPACE,
        /** Player doesn't have enough coins. */
        INSUFFICIENT_FUNDS,
        /** Amount was zero or negative. */
        INVALID_AMOUNT,
        /** Player entity was null or invalid. */
        INVALID_PLAYER
    }
    
    private final Status status;
    private final long requestedAmount;
    private final long actualAmount;
    private final int slotsNeeded;
    private final int slotsAvailable;
    
    private CoinOperationResult(Status status, long requestedAmount, long actualAmount,
                                 int slotsNeeded, int slotsAvailable) {
        this.status = status;
        this.requestedAmount = requestedAmount;
        this.actualAmount = actualAmount;
        this.slotsNeeded = slotsNeeded;
        this.slotsAvailable = slotsAvailable;
    }
    
    // ========== Factory Methods ==========
    
    public static CoinOperationResult success(long amount) {
        return new CoinOperationResult(Status.SUCCESS, amount, amount, 0, 0);
    }
    
    public static CoinOperationResult notEnoughSpace(long requestedAmount, int slotsNeeded, int slotsAvailable) {
        return new CoinOperationResult(Status.NOT_ENOUGH_SPACE, requestedAmount, 0, slotsNeeded, slotsAvailable);
    }
    
    public static CoinOperationResult insufficientFunds(long requestedAmount, long actualBalance) {
        return new CoinOperationResult(Status.INSUFFICIENT_FUNDS, requestedAmount, actualBalance, 0, 0);
    }
    
    public static CoinOperationResult invalidAmount(long amount) {
        return new CoinOperationResult(Status.INVALID_AMOUNT, amount, 0, 0, 0);
    }
    
    public static CoinOperationResult invalidPlayer() {
        return new CoinOperationResult(Status.INVALID_PLAYER, 0, 0, 0, 0);
    }
    
    // ========== Getters ==========
    
    /** @return true if operation was successful */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /** @return The status code */
    @Nonnull
    public Status getStatus() {
        return status;
    }
    
    /** @return The amount that was requested */
    public long getRequestedAmount() {
        return requestedAmount;
    }
    
    /** @return The actual amount available (for INSUFFICIENT_FUNDS) */
    public long getActualAmount() {
        return actualAmount;
    }
    
    /** @return Number of inventory slots needed (for NOT_ENOUGH_SPACE) */
    public int getSlotsNeeded() {
        return slotsNeeded;
    }
    
    /** @return Number of slots available (for NOT_ENOUGH_SPACE) */
    public int getSlotsAvailable() {
        return slotsAvailable;
    }
    
    /** @return Human-readable message describing the result */
    @Nonnull
    public String getMessage() {
        return switch (status) {
            case SUCCESS -> "Operation successful";
            case NOT_ENOUGH_SPACE -> String.format("Need %d slots, only %d available", slotsNeeded, slotsAvailable);
            case INSUFFICIENT_FUNDS -> String.format("Requested %d but only have %d", requestedAmount, actualAmount);
            case INVALID_AMOUNT -> "Amount must be positive";
            case INVALID_PLAYER -> "Player is null or invalid";
        };
    }
    
    @Override
    public String toString() {
        return "CoinOperationResult{status=" + status +
               ", requested=" + requestedAmount +
               ", actual=" + actualAmount +
               '}';
    }
}
