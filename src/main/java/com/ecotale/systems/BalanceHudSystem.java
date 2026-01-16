package com.ecotale.systems;

import com.ecotale.hud.BalanceHud;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active balance HUDs for updates
 * 
 * Uses static methods for easy access from EconomyManager
 */
public class BalanceHudSystem {
    
    // Track active HUDs for each player
    private static final ConcurrentHashMap<UUID, BalanceHud> activeHuds = new ConcurrentHashMap<>();
    
    /**
     * Register a HUD for a player
     */
    public static void registerHud(UUID playerUuid, BalanceHud hud) {
        activeHuds.put(playerUuid, hud);
    }
    
    /**
     * Update a player's balance HUD when their balance changes
     */
    public static void updatePlayerHud(UUID playerUuid, double newBalance) {
        BalanceHud hud = activeHuds.get(playerUuid);
        if (hud != null) {
            hud.updateBalance(newBalance);
        }
    }
    
    /**
     * Remove HUD tracking when player leaves
     */
    /**
     * Remove HUD tracking when player leaves
     */
    public static void removePlayerHud(UUID playerUuid) {
        activeHuds.remove(playerUuid);
    }

    /**
     * Get the active HUD for a player
     */
    public static BalanceHud getHud(UUID playerUuid) {
        return activeHuds.get(playerUuid);
    }
    
    /**
     * Refresh all active HUDs (for config changes like symbol/formatting)
     */
    public static void refreshAllHuds() {
        for (BalanceHud hud : activeHuds.values()) {
            hud.refresh();
        }
    }
}
