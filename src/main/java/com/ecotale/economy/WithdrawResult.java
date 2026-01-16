package com.ecotale.economy;

/**
 * Result of a withdraw operation.
 * <p>
 * Provides detailed information about why a withdrawal succeeded or failed,
 * enabling better error handling and user feedback.
 * 
 * @author michiweon
 */
public enum WithdrawResult {
    /** Withdrawal was successful */
    SUCCESS,
    
    /** Amount was invalid (negative or zero) */
    INVALID_AMOUNT,
    
    /** Player doesn't have enough balance */
    INSUFFICIENT_FUNDS,
    
    /** A BalanceChangeEvent listener cancelled the operation */
    EVENT_CANCELLED,
    
    /** Player account not found */
    ACCOUNT_NOT_FOUND
}
