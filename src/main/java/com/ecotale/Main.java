package com.ecotale;

import com.ecotale.api.EcotaleAPI;
import com.ecotale.commands.BalanceCommand;
import com.ecotale.commands.EcoAdminCommand;
import com.ecotale.commands.PayCommand;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.EconomyManager;
import com.ecotale.hud.BalanceHud;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * Ecotale - Economy plugin for Hytale
 * 
 * Features:
 * - Player balances with persistent storage
 * - On-screen HUD balance display
 * - Player-to-player transfers
 * - Admin economy commands
 * - Public API for other plugins
 */
public class Main extends JavaPlugin {
    
    private static Main instance;
    public static Config<EcotaleConfig> CONFIG;
    
    private EconomyManager economyManager;
    
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        CONFIG = this.withConfig("Ecotale", EcotaleConfig.CODEC);
    }
    
    @Override
    protected void setup() {
        super.setup();
        instance = this;
        CONFIG.save();
        
        // Initialize economy manager
        this.economyManager = new EconomyManager(this);
        
        // Initialize API with configured rate limiting
        EcotaleAPI.init(
            this.economyManager,
            CONFIG.get().getRateLimitBurst(),
            CONFIG.get().getRateLimitRefill()
        );
        
        // Register commands
        this.getCommandRegistry().registerCommand(new BalanceCommand());
        this.getCommandRegistry().registerCommand(new PayCommand());
        this.getCommandRegistry().registerCommand(new EcoAdminCommand());
        
        // Register player join event for HUD setup
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, (event) -> {
            var player = event.getHolder().getComponent(Player.getComponentType());
            var playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            
            if (player != null && playerRef != null) {
                // Ensure player has an account
                this.economyManager.ensureAccount(playerRef.getUuid());
                
                // Cache player name for leaderboards (H2 only)
                var h2Storage = this.economyManager.getH2Storage();
                if (h2Storage != null) {
                    h2Storage.updatePlayerName(playerRef.getUuid(), playerRef.getUsername());
                }
                
                // Setup Balance HUD if enabled
                if (Main.CONFIG.get().isEnableHudDisplay()) {
                    BalanceHud hud = new BalanceHud(playerRef);
                    player.getHudManager().setCustomHud(playerRef, hud);
                    com.ecotale.systems.BalanceHudSystem.registerHud(playerRef.getUuid(), hud);
                }
            }
        });
        

        this.getLogger().at(Level.INFO).log("Ecotale Economy loaded - HUD balance display active!");
    }
    
    @Override
    protected void shutdown() {
        this.getLogger().at(Level.INFO).log("Ecotale shutting down - saving data...");
        if (this.economyManager != null) {
            this.economyManager.shutdown();
        }
        this.getLogger().at(Level.INFO).log("Ecotale shutdown complete!");
    }
    
    public static Main getInstance() {
        return instance;
    }
    
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }
}
