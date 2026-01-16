package com.ecotale.economy;

/**
 * Types of transactions for logging.
 */
public enum TransactionType {
    GIVE("Admin give"),
    TAKE("Admin take"),
    SET("Admin set"),
    RESET("Admin reset"),
    PAY("Player transfer"),
    EARN("Earnings"),      // Future: mob kills, quests, etc.
    SPEND("Spending");     // Future: shops, fees, etc.
    
    private final String displayName;
    
    TransactionType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
