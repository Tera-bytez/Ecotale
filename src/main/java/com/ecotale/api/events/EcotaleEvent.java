package com.ecotale.api.events;

import javax.annotation.Nonnull;

/**
 * Base class for all Ecotale events.
 * 
 * <p>Events allow external plugins to react to economy changes without
 * modifying the Ecotale plugin directly.</p>
 * 
 * <p>Example listener registration:</p>
 * <pre>
 * EcotaleEvents.register(BalanceChangeEvent.class, event -> {
 *     System.out.println("Balance changed for " + event.getPlayerUuid());
 * });
 * </pre>
 */
public abstract class EcotaleEvent {
    private boolean cancelled = false;
    private final long timestamp;
    
    protected EcotaleEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get the timestamp when this event was created.
     * @return Unix timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if this event has been cancelled by a listener.
     * Cancelled events will not complete their action.
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * Set the cancellation state of this event.
     * Only applicable to cancellable events.
     * @param cancelled true to cancel
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
