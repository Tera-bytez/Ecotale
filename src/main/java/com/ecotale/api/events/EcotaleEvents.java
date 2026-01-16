package com.ecotale.api.events;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event manager for Ecotale events.
 * 
 * <p>External plugins can register listeners here to react to economy events.</p>
 * 
 * <p>Example registration:</p>
 * <pre>
 * // Register a listener for balance changes
 * EcotaleEvents.register(BalanceChangeEvent.class, event -> {
 *     System.out.println("Balance changed: " + event.getDelta());
 *     
 *     // Cancel if trying to go over 1 million
 *     if (event.getNewBalance() > 1_000_000) {
 *         event.setCancelled(true);
 *     }
 * });
 * 
 * // Unregister all listeners for a specific event type
 * EcotaleEvents.unregisterAll(BalanceChangeEvent.class);
 * </pre>
 * 
 * <p>Thread-safe: can be called from any thread.</p>
 */
public final class EcotaleEvents {
    
    private static final Map<Class<? extends EcotaleEvent>, List<Consumer<? extends EcotaleEvent>>> listeners = 
            new ConcurrentHashMap<>();
    
    private EcotaleEvents() {}
    
    /**
     * Register a listener for a specific event type.
     * 
     * @param eventClass The event class to listen for
     * @param listener The listener callback
     * @param <T> Event type
     */
    @SuppressWarnings("unchecked")
    public static <T extends EcotaleEvent> void register(@Nonnull Class<T> eventClass, 
                                                          @Nonnull Consumer<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }
    
    /**
     * Unregister a specific listener.
     * 
     * @param eventClass The event class
     * @param listener The listener to remove
     * @param <T> Event type
     * @return true if the listener was found and removed
     */
    public static <T extends EcotaleEvent> boolean unregister(@Nonnull Class<T> eventClass,
                                                               @Nonnull Consumer<T> listener) {
        List<Consumer<? extends EcotaleEvent>> list = listeners.get(eventClass);
        if (list != null) {
            return list.remove(listener);
        }
        return false;
    }
    
    /**
     * Unregister all listeners for a specific event type.
     * 
     * @param eventClass The event class
     */
    public static void unregisterAll(@Nonnull Class<? extends EcotaleEvent> eventClass) {
        listeners.remove(eventClass);
    }
    
    /**
     * Fire an event to all registered listeners.
     * 
     * <p>This method is called internally by Ecotale when events occur.
     * External plugins should not call this directly.</p>
     * 
     * @param event The event to fire
     * @param <T> Event type
     * @return The event (may be modified/cancelled by listeners)
     */
    @SuppressWarnings("unchecked")
    public static <T extends EcotaleEvent> T fire(@Nonnull T event) {
        List<Consumer<? extends EcotaleEvent>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<? extends EcotaleEvent> consumer : list) {
                try {
                    ((Consumer<T>) consumer).accept(event);
                } catch (Exception e) {
                    // Log but don't propagate exceptions from listeners
                    System.err.println("[Ecotale] Error in event listener: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return event;
    }
    
    /**
     * Get the number of registered listeners for an event type.
     * 
     * @param eventClass The event class
     * @return Number of listeners
     */
    public static int getListenerCount(@Nonnull Class<? extends EcotaleEvent> eventClass) {
        List<Consumer<? extends EcotaleEvent>> list = listeners.get(eventClass);
        return list != null ? list.size() : 0;
    }
}
