package com.ecotale.api.events;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Fired when a player's balance changes for any reason.
 * 
 * <p>This event is cancellable - if cancelled, the balance change will not occur.</p>
 * 
 * <p>Example:</p>
 * <pre>
 * EcotaleEvents.register(BalanceChangeEvent.class, event -> {
 *     if (event.getNewBalance() > 1_000_000) {
 *         event.setCancelled(true); // Prevent becoming a millionaire
 *     }
 * });
 * </pre>
 */
public class BalanceChangeEvent extends EcotaleEvent {
    
    public enum Cause {
        /** Administrative action (set/reset) */
        ADMIN,
        /** Deposit (earn, receive) */
        DEPOSIT,
        /** Withdrawal (spend, lose) */
        WITHDRAW,
        /** Transfer to/from another player */
        TRANSFER,
        /** Plugin API call */
        API,
        /** Unknown/other cause */
        OTHER
    }
    
    private final UUID playerUuid;
    private final double oldBalance;
    private final double newBalance;
    private final Cause cause;
    private final String reason;
    
    public BalanceChangeEvent(@Nonnull UUID playerUuid, double oldBalance, 
                               double newBalance, @Nonnull Cause cause, 
                               @Nonnull String reason) {
        this.playerUuid = playerUuid;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.cause = cause;
        this.reason = reason;
    }
    
    /**
     * Get the UUID of the player whose balance changed.
     */
    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Get the balance before the change.
     */
    public double getOldBalance() {
        return oldBalance;
    }
    
    /**
     * Get the balance after the change.
     */
    public double getNewBalance() {
        return newBalance;
    }
    
    /**
     * Get the amount changed (positive for increase, negative for decrease).
     */
    public double getDelta() {
        return newBalance - oldBalance;
    }
    
    /**
     * Get the cause of this balance change.
     */
    @Nonnull
    public Cause getCause() {
        return cause;
    }
    
    /**
     * Get the reason/description for this change.
     */
    @Nonnull
    public String getReason() {
        return reason;
    }
}
