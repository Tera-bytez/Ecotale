package com.ecotale.economy;

/**
 * Result of a deposit operation.
 * <p>
 * Provides detailed information about why a deposit succeeded or failed,
 * enabling better error handling and user feedback.
 * 
 * @author michiweon
 */
public enum DepositResult {
    /** Deposit was successful */
    SUCCESS,
    
    /** Amount was invalid (negative or zero) */
    INVALID_AMOUNT,
    
    /** Deposit would exceed the configured maximum balance */
    EXCEEDS_MAX_BALANCE,
    
    /** A BalanceChangeEvent listener cancelled the operation */
    EVENT_CANCELLED,
    
    /** Player account not found */
    ACCOUNT_NOT_FOUND
}
