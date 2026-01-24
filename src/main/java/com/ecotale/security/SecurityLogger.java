package com.ecotale.security;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ecotale.Main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Security Logger for unauthorized access attempts.
 * 
 * Logs to dedicated file: mods/Ecotale/security_alerts.log
 * 
 * These alerts should NEVER have false positives - they indicate
 * a client attempting to bypass permission checks.
 */
public class SecurityLogger {
    
    private static final String LOG_FILE = "mods/Ecotale/security_alerts.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static SecurityLogger instance;
    
    private final Main plugin;
    private final ConcurrentLinkedQueue<SecurityEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flushExecutor;
    private File logFile;
    
    public SecurityLogger(Main plugin) {
        this.plugin = plugin;
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("Ecotale-SecurityLogger").factory()
        );
        
        initLogFile();
        
        // Flush queue every 5 seconds
        flushExecutor.scheduleAtFixedRate(this::flushQueue, 5, 5, TimeUnit.SECONDS);
        
        instance = this;
    }
    
    public static SecurityLogger getInstance() {
        return instance;
    }
    
    private void initLogFile() {
        try {
            logFile = new File(LOG_FILE);
            logFile.getParentFile().mkdirs();
            
            if (!logFile.exists()) {
                logFile.createNewFile();
                // Write header
                try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                    writer.println("# Ecotale Security Alerts");
                    writer.println("# These indicate potential hack attempts - NO FALSE POSITIVES EXPECTED");
                    writer.println("# Format: [timestamp] [severity] [uuid] [username] [gui] [action] [details]");
                    writer.println("---");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).log("[Ecotale] Failed to initialize security log: %s", e.getMessage());
        }
    }
    
    /**
     * Log an unauthorized access attempt.
     */
    public void logUnauthorizedAccess(PlayerRef playerRef, String guiName, String attemptedAction, String details) {
        SecurityEvent event = new SecurityEvent(
            LocalDateTime.now(),
            Severity.CRITICAL,
            playerRef.getUuid(),
            playerRef.getUsername(),
            guiName,
            attemptedAction,
            details
        );
        
        pendingEvents.add(event);
        
        // Log to console immediately
        plugin.getLogger().at(Level.WARNING).log(
            "[SECURITY] Unauthorized GUI access: %s attempted %s on %s (%s)",
            playerRef.getUsername(), attemptedAction, guiName, details
        );
    }
    
    /**
     * Log a suspicious but not definitively malicious event.
     */
    public void logSuspicious(PlayerRef playerRef, String guiName, String action, String details) {
        SecurityEvent event = new SecurityEvent(
            LocalDateTime.now(),
            Severity.WARNING,
            playerRef.getUuid(),
            playerRef.getUsername(),
            guiName,
            action,
            details
        );
        
        pendingEvents.add(event);
    }
    
    private void flushQueue() {
        if (pendingEvents.isEmpty()) return;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            SecurityEvent event;
            while ((event = pendingEvents.poll()) != null) {
                writer.printf("[%s] [%s] [%s] [%s] [%s] [%s] %s%n",
                    FORMATTER.format(event.timestamp),
                    event.severity,
                    event.uuid,
                    event.username,
                    event.guiName,
                    event.attemptedAction,
                    event.details
                );
            }
        } catch (IOException e) {
            plugin.getLogger().at(Level.WARNING).log("[Ecotale] Failed to flush security log: %s", e.getMessage());
        }
    }
    
    public void shutdown() {
        flushQueue();
        flushExecutor.shutdown();
        try {
            flushExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ========== Event Classes ==========
    
    public enum Severity {
        WARNING,
        CRITICAL
    }
    
    private static class SecurityEvent {
        final LocalDateTime timestamp;
        final Severity severity;
        final UUID uuid;
        final String username;
        final String guiName;
        final String attemptedAction;
        final String details;
        
        SecurityEvent(LocalDateTime timestamp, Severity severity, UUID uuid, String username,
                      String guiName, String attemptedAction, String details) {
            this.timestamp = timestamp;
            this.severity = severity;
            this.uuid = uuid;
            this.username = username;
            this.guiName = guiName;
            this.attemptedAction = attemptedAction;
            this.details = details;
        }
    }
}
