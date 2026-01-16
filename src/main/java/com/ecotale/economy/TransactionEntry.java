package com.ecotale.economy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Immutable transaction record for activity logging.
 * Pre-formats timestamp on creation to avoid work during UI render.
 * 
 * Design notes:
 * - Immutable: safe for concurrent access without synchronization
 * - Pre-formatted time: avoids DateTimeFormatter work per render
 * - Nullable targetPlayer: some actions don't have a target (e.g., EARN)
 */
public record TransactionEntry(
    Instant timestamp,
    String formattedTime,
    TransactionType type,
    UUID sourcePlayer,
    UUID targetPlayer,  // null for non-transfer actions
    double amount,
    String playerName   // Pre-resolved name to avoid lookups during render
) {
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    
    /**
     * Create entry for single-player action (give, take, set, reset, earn, spend).
     */
    public static TransactionEntry single(
            TransactionType type, 
            UUID player, 
            String playerName,
            double amount) {
        Instant now = Instant.now();
        return new TransactionEntry(
            now,
            TIME_FORMATTER.format(now),
            type,
            player,
            null,
            amount,
            playerName
        );
    }
    
    /**
     * Create entry for transfer between players (pay).
     */
    public static TransactionEntry transfer(
            UUID from, 
            String fromName,
            UUID to, 
            String toName,
            double amount) {
        Instant now = Instant.now();
        return new TransactionEntry(
            now,
            TIME_FORMATTER.format(now),
            TransactionType.PAY,
            from,
            to,
            amount,
            fromName + " → " + toName
        );
    }
    
    /**
     * Format for UI display. Example: "[14:30] Admin give: PlayerX +$1,000"
     */
    public String toDisplayString() {
        return String.format("[%s] %s: %s %s",
            formattedTime,
            type.getDisplayName(),
            playerName,
            formatAmount()
        );
    }
    
    private String formatAmount() {
        if (type == TransactionType.TAKE || type == TransactionType.SPEND) {
            return String.format("-$%.0f", amount);
        } else if (type == TransactionType.SET || type == TransactionType.RESET) {
            return String.format("=$%.0f", amount);
        } else {
            return String.format("+$%.0f", amount);
        }
    }
    
    /**
     * Check if this transaction involves a specific player.
     */
    public boolean involvesPlayer(UUID playerUuid) {
        if (playerUuid == null) return false;
        return playerUuid.equals(sourcePlayer) || playerUuid.equals(targetPlayer);
    }
}
