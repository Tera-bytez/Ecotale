package com.ecotale.lib.simplehud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A high-level API for creating Custom HUDs in Hytale.
 * <p>
 * This abstract class handles the complexity of the Hytale UI system, providing:
 * <ul>
 *     <li>Automatic state tracking to minimize bandwidth (smart updates).</li>
 *     <li>Robust error handling for invalid IDs or null values.</li>
 *     <li>Fluent API for easy chaining.</li>
 * </ul>
 * 
 * Originally from SimpleHUD-Lib by Terabytez, inlined into Ecotale.
 */
public abstract class SimpleHud extends CustomUIHud {

    private static final Logger LOGGER = Logger.getLogger(SimpleHud.class.getName());

    private final String uiPath;
    
    // Concurrent map to ensure thread safety during async updates
    private final Map<String, String> values = new ConcurrentHashMap<>();
    
    // Track last sent values to avoid unnecessary packet spam
    private final Map<String, String> lastSentValues = new ConcurrentHashMap<>();
    
    // Auto-size configuration
    private AutoSizeConfig autoSizeConfig = null;

    /**
     * Create a new SimpleHud.
     * 
     * @param playerRef The player to show this HUD to.
     * @param uiPath The path to the .ui file (e.g., "Pages/MyPlugin_Hud.ui"). 
     */
    public SimpleHud(@Nonnull PlayerRef playerRef, @Nonnull String uiPath) {
        super(playerRef);
        
        if (uiPath == null || uiPath.trim().isEmpty()) {
            throw new IllegalArgumentException("UI Path cannot be null or empty");
        }
        
        this.uiPath = uiPath;
    }

    /**
     * Set a text value for a UI element (Label).
     */
    public SimpleHud setText(@Nonnull String elementId, @Nullable String value) {
        String safeValue = (value == null) ? "" : value;
        String normalizedId = normalizeId(elementId);
        
        if (!normalizedId.contains(".")) {
            normalizedId += ".Text";
        }
        
        updateValue(normalizedId, safeValue);
        
        // If auto-size is enabled and this is the tracked element, recalculate width
        if (autoSizeConfig != null && elementId.equals(autoSizeConfig.textElementId)) {
            int calculatedWidth = autoSizeConfig.calculateWidth(safeValue);
            setProperty(autoSizeConfig.panelElementId, "Anchor.Width", String.valueOf(calculatedWidth));
        }
        
        return this;
    }
    
    /**
     * Generic method to set any property on a UI element.
     */
    public SimpleHud setProperty(@Nonnull String elementId, @Nonnull String property, @Nonnull String value) {
        String normalizedId = normalizeId(elementId);
        String fullKey = normalizedId + "." + property;
        updateValue(fullKey, value);
        return this;
    }
    
    /**
     * Set the source image for an Image element.
     */
    public SimpleHud setImage(@Nonnull String elementId, @Nonnull String imagePath) {
        String normalizedId = normalizeId(elementId);
        if (!normalizedId.contains(".")) {
            normalizedId += ".Source";
        }
        updateValue(normalizedId, imagePath);
        return this;
    }

    /**
     * Set the visibility of an element.
     */
    public SimpleHud setVisible(@Nonnull String elementId, boolean visible) {
        String value = visible ? "Visible" : "Collapsed";
        return setProperty(elementId, "Visibility", value);
    }
    
    /**
     * Enable auto-sizing for a panel based on text content.
     */
    public SimpleHud enableAutoSize(@Nonnull AutoSizeConfig config) {
        this.autoSizeConfig = config;
        return this;
    }
    
    /**
     * Enable auto-sizing with default configuration.
     */
    public SimpleHud enableAutoSize(@Nonnull String textElementId, @Nonnull String panelElementId) {
        return enableAutoSize(AutoSizeConfig.withDefaults(textElementId, panelElementId));
    }

    /**
     * Set a style property directly.
     */
    public SimpleHud setStyleProperty(@Nonnull String elementId, @Nonnull String property, @Nonnull String value) {
        return setProperty(elementId, property, value);
    }
    
    private void updateValue(String key, String value) {
        values.put(key, value);
    }
    
    private String normalizeId(String id) {
        if (id == null) return "#Unknown";
        if (!id.startsWith("#")) {
            return "#" + id;
        }
        return id;
    }

    /**
     * Push all pending updates to the client.
     */
    public void pushUpdates() {
        try {
            this.show();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to push HUD updates for path: " + uiPath, e);
        }
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        try {
            builder.append(uiPath);

            for (Map.Entry<String, String> entry : values.entrySet()) {
                builder.set(entry.getKey(), entry.getValue());
                lastSentValues.put(entry.getKey(), entry.getValue());
            }
            
            onBuild(builder);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "CRITICAL: Failed to build HUD " + uiPath, e);
        }
    }
    
    /**
     * Lifecycle hook called during the build process.
     */
    protected void onBuild(UICommandBuilder builder) {
        // Optional override
    }
    
    /**
     * Show a temporary UI element that auto-removes after a delay.
     */
    public void showTemporary(@Nonnull String id, @Nonnull String parentSelector, 
                              @Nonnull String uiCode, long durationMs) {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            builder.appendInline(parentSelector, uiCode);
            this.update(false, builder);
            
            if (durationMs > 0) {
                HudScheduler.runLater(() -> removeElement("#" + id), durationMs);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to show temporary element: " + id, e);
        }
    }
    
    /**
     * Remove a UI element by its selector.
     */
    public void removeElement(@Nonnull String selector) {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            builder.remove(selector);
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to remove element: " + selector, e);
        }
    }
    
    /**
     * Send an incremental update without rebuilding the entire HUD.
     */
    public void sendUpdate(@Nonnull UICommandBuilder builder) {
        try {
            this.update(false, builder);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send incremental HUD update", e);
        }
    }
    
    /**
     * Create a new UICommandBuilder for building incremental updates.
     */
    public UICommandBuilder createBuilder() {
        return new UICommandBuilder();
    }
}
