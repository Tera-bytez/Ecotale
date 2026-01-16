package com.ecotale.api.events;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Fired when a transaction occurs between players or with the system.
 * 
 * <p>This event is fired AFTER the transaction completes successfully.
 * It is NOT cancellable since the transaction already happened.</p>
 * 
 * <p>For cancellable logic, use {@link BalanceChangeEvent} instead.</p>
 */
public class TransactionEvent extends EcotaleEvent {
    
    public enum Type {
        /** Player-to-player transfer via /pay */
        PLAYER_TRANSFER,
        /** Admin deposit via /eco give */
        ADMIN_GIVE,
        /** Admin withdrawal via /eco take */
        ADMIN_TAKE,
        /** Admin set via /eco set */
        ADMIN_SET,
        /** Balance reset */
        RESET,
        /** External API call */
        API
    }
    
    private final Type type;
    private final UUID sourceUuid;
    private final UUID targetUuid;
    private final double amount;
    private final double fee;
    private final String reason;
    
    public TransactionEvent(@Nonnull Type type, @Nullable UUID sourceUuid,
                            @Nonnull UUID targetUuid, double amount, 
                            double fee, @Nonnull String reason) {
        this.type = type;
        this.sourceUuid = sourceUuid;
        this.targetUuid = targetUuid;
        this.amount = amount;
        this.fee = fee;
        this.reason = reason;
    }
    
    /**
     * Get the transaction type.
     */
    @Nonnull
    public Type getType() {
        return type;
    }
    
    /**
     * Get the source UUID (sender in transfers, null for admin actions).
     */
    @Nullable
    public UUID getSourceUuid() {
        return sourceUuid;
    }
    
    /**
     * Get the target UUID (recipient in transfers, target in admin actions).
     */
    @Nonnull
    public UUID getTargetUuid() {
        return targetUuid;
    }
    
    /**
     * Get the transaction amount (before fees).
     */
    public double getAmount() {
        return amount;
    }
    
    /**
     * Get the fee charged (0 for non-transfer transactions).
     */
    public double getFee() {
        return fee;
    }
    
    /**
     * Get the total amount deducted from source (amount + fee).
     */
    public double getTotalDeducted() {
        return amount + fee;
    }
    
    /**
     * Get the reason/description for this transaction.
     */
    @Nonnull
    public String getReason() {
        return reason;
    }
}
