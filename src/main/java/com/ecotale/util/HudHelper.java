package com.ecotale.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Helper for HUD registration with optional MultipleHUD support.
 * 
 * If MultipleHUD is installed, uses its API to allow multiple HUDs to coexist.
 * Otherwise falls back to vanilla Hytale HUD registration.
 * 
 * @author michiweon
 */
public final class HudHelper {
    
    private static final String HUD_ID = "ecotale";
    private static final String MULTIPLEHUD_CLASS = "com.buuz135.mhud.MultipleHUD";
    
    private static boolean multipleHudAvailable = false;
    private static Object multipleHudInstance = null;
    private static Method setCustomHudMethod = null;
    private static Method hideCustomHudMethod = null;
    
    private HudHelper() {} // Utility class
    
    /**
     * Initialize the helper - checks if MultipleHUD is available.
     * Call this once during plugin setup.
     */
    public static void init() {
        try {
            Class<?> multipleHudClass = Class.forName(MULTIPLEHUD_CLASS);
            
            // Get the singleton instance
            Method getInstanceMethod = multipleHudClass.getMethod("getInstance");
            multipleHudInstance = getInstanceMethod.invoke(null);
            
            if (multipleHudInstance != null) {
                // Cache the API methods
                setCustomHudMethod = multipleHudClass.getMethod(
                    "setCustomHud", 
                    Player.class, 
                    PlayerRef.class, 
                    String.class, 
                    CustomUIHud.class
                );
                
                hideCustomHudMethod = multipleHudClass.getMethod(
                    "hideCustomHud",
                    Player.class,
                    PlayerRef.class,
                    String.class
                );
                
                multipleHudAvailable = true;
                com.ecotale.Main.getInstance().getLogger()
                    .at(Level.INFO).log("Ecotale: MultipleHUD detected, using compatible mode!");
            }
        } catch (ClassNotFoundException e) {
            // MultipleHUD not installed - this is fine
            com.ecotale.Main.getInstance().getLogger()
                .at(Level.INFO).log("Ecotale: MultipleHUD not detected, using vanilla HUD");
        } catch (Exception e) {
            // Something went wrong with reflection - fall back to vanilla
            com.ecotale.Main.getInstance().getLogger()
                .at(Level.WARNING).log("Ecotale: Failed to initialize MultipleHUD compat: " + e.getMessage());
        }
    }
    
    /**
     * Register a custom HUD for a player.
     * Uses MultipleHUD API if available, otherwise vanilla method.
     */
    public static void setCustomHud(Player player, PlayerRef playerRef, CustomUIHud hud) {
        if (multipleHudAvailable && multipleHudInstance != null && setCustomHudMethod != null) {
            try {
                setCustomHudMethod.invoke(multipleHudInstance, player, playerRef, HUD_ID, hud);
                return;
            } catch (Exception e) {
                // Fall back to vanilla on error
                com.ecotale.Main.getInstance().getLogger()
                    .at(Level.WARNING).log("Ecotale: MultipleHUD call failed, using vanilla: " + e.getMessage());
            }
        }
        
        // Vanilla fallback
        player.getHudManager().setCustomHud(playerRef, hud);
    }
    
    /**
     * Remove the Ecotale HUD for a player.
     * Only works if MultipleHUD is installed.
     */
    public static void hideCustomHud(Player player, PlayerRef playerRef) {
        if (multipleHudAvailable && multipleHudInstance != null && hideCustomHudMethod != null) {
            try {
                hideCustomHudMethod.invoke(multipleHudInstance, player, playerRef, HUD_ID);
            } catch (Exception e) {
                // Ignore - best effort cleanup
            }
        }
        // No vanilla equivalent - would require removing all HUDs
    }
    
    /**
     * Check if MultipleHUD compatibility is active.
     */
    public static boolean isMultipleHudAvailable() {
        return multipleHudAvailable;
    }
}
