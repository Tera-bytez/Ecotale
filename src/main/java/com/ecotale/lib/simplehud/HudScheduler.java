package com.ecotale.lib.simplehud;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe scheduler for delayed HUD operations.
 * 
 * Originally from SimpleHUD-Lib by Terabytez, inlined into Ecotale.
 */
public final class HudScheduler {
    
    private static final Logger LOGGER = Logger.getLogger(HudScheduler.class.getName());
    private static final ScheduledExecutorService EXECUTOR = 
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "Ecotale-HudScheduler");
                t.setDaemon(true);
                return t;
            });
    
    private HudScheduler() {
        // Utility class - no instantiation
    }
    
    /**
     * Schedule a task to run after a delay.
     */
    public static ScheduledFuture<?> runLater(Runnable task, long delayMs) {
        return EXECUTOR.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing scheduled HUD task", e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Cancel a scheduled task.
     */
    public static boolean cancel(ScheduledFuture<?> future) {
        if (future != null && !future.isDone()) {
            return future.cancel(false);
        }
        return false;
    }
    
    /**
     * Shutdown the scheduler.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(1, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
