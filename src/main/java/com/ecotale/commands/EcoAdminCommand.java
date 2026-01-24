package com.ecotale.commands;

import com.ecotale.Main;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.gui.EcoAdminGui;
import com.ecotale.hud.BalanceHud;
import com.ecotale.systems.BalanceHudSystem;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy admin commands for managing and testing balances.
 * 
 * Commands:
 * - /eco set <amount> - Set your balance
 * - /eco give <amount> - Add to your balance
 * - /eco take <amount> - Remove from your balance  
 * - /eco reset - Reset to starting balance
 * - /eco top - Show top balances
 * - /eco save - Force save data
 */
public class EcoAdminCommand extends AbstractAsyncCommand {
    
    public EcoAdminCommand() {
        super("eco", "Economy administration commands");
        this.addAliases("economy", "ecoadmin");
        this.setPermissionGroup(null); // Admin only - requires ecotale.ecotale.command.eco permission
        
        this.addSubCommand(new EcoSetCommand());
        this.addSubCommand(new EcoGiveCommand());
        this.addSubCommand(new EcoTakeCommand());
        this.addSubCommand(new EcoResetCommand());
        this.addSubCommand(new EcoTopCommand());
        this.addSubCommand(new EcoSaveCommand());
        this.addSubCommand(new EcoHudCommand());
        this.addSubCommand(new EcoMetricsCommand());
    }
    
    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        
        // Open admin GUI if player
        if (sender instanceof Player player) {
            var ref = player.getReference();
            if (ref != null && ref.isValid()) {
                var store = ref.getStore();
                var world = store.getExternalData().getWorld();
                if (world == null) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> future = new CompletableFuture<>();
                world.execute(() -> {
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef != null) {
                        player.getPageManager().openCustomPage(ref, store, new EcoAdminGui(playerRef));
                    }
                    future.complete(null);
                });
                return future;
            }
        }
        
        // Fallback: show help text
        commandContext.sender().sendMessage(Message.raw("=== Ecotale Economy Admin ===").color(new Color(255, 215, 0)));
        commandContext.sender().sendMessage(Message.raw("  /eco set <amount> - Set your balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco give <amount> - Add to balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco take <amount> - Remove from balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco reset - Reset to starting balance").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco top - Show top balances").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco metrics - Show performance stats").color(Color.GRAY));
        commandContext.sender().sendMessage(Message.raw("  /eco save - Force save data").color(Color.GRAY));
        return CompletableFuture.completedFuture(null);
    }
    
    // ========== SET COMMAND ==========
    private static class EcoSetCommand extends AbstractAsyncCommand {
        private final RequiredArg<Double> amountArg;
        
        public EcoSetCommand() {
            super("set", "Set your balance to a specific amount");
            this.amountArg = this.withRequiredArg("amount", "The amount to set", ArgTypes.DOUBLE);
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            Double amount = amountArg.get(ctx);
            if (amount == null || amount < 0) {
                ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return CompletableFuture.completedFuture(null);
            
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                double oldBalance = Main.getInstance().getEconomyManager().getBalance(playerRef.getUuid());
                Main.getInstance().getEconomyManager().setBalance(playerRef.getUuid(), amount, "Admin set");
                updateHud(playerRef.getUuid(), amount);
                
                player.sendMessage(Message.join(
                    Message.raw("Balance set: ").color(Color.GREEN),
                    Message.raw(Main.CONFIG.get().format(oldBalance)).color(Color.GRAY),
                    Message.raw(" -> ").color(Color.WHITE),
                    Message.raw(Main.CONFIG.get().format(amount)).color(new Color(50, 205, 50))
                ));
            }, world);
        }
    }
    
    // ========== GIVE COMMAND ==========
    private static class EcoGiveCommand extends AbstractAsyncCommand {
        private final RequiredArg<Double> amountArg;
        
        public EcoGiveCommand() {
            super("give", "Add money to your balance");
            this.addAliases("add");
            this.amountArg = this.withRequiredArg("amount", "The amount to add", ArgTypes.DOUBLE);
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            Double amount = amountArg.get(ctx);
            if (amount == null || amount <= 0) {
                ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return CompletableFuture.completedFuture(null);
            }
            
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            
            // Run on world thread to avoid threading issues
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                Main.getInstance().getEconomyManager().deposit(playerRef.getUuid(), amount, "Admin give");
                double newBalance = Main.getInstance().getEconomyManager().getBalance(playerRef.getUuid());
                updateHud(playerRef.getUuid(), newBalance);
                
                player.sendMessage(Message.join(
                    Message.raw("Added ").color(Color.GREEN),
                    Message.raw("+" + Main.CONFIG.get().format(amount)).color(new Color(50, 205, 50)),
                    Message.raw(" | New balance: ").color(Color.GRAY),
                    Message.raw(Main.CONFIG.get().format(newBalance)).color(Color.WHITE)
                ));
            }, world);
        }
    }
    
    // ========== TAKE COMMAND ==========
    private static class EcoTakeCommand extends AbstractAsyncCommand {
        private final RequiredArg<Double> amountArg;
        
        public EcoTakeCommand() {
            super("take", "Remove money from your balance");
            this.addAliases("remove");
            this.amountArg = this.withRequiredArg("amount", "The amount to remove", ArgTypes.DOUBLE);
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            Double amount = amountArg.get(ctx);
            if (amount == null || amount <= 0) {
                ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return CompletableFuture.completedFuture(null);
            
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                boolean success = Main.getInstance().getEconomyManager().withdraw(playerRef.getUuid(), amount, "Admin take");
                double newBalance = Main.getInstance().getEconomyManager().getBalance(playerRef.getUuid());
                updateHud(playerRef.getUuid(), newBalance);
                
                if (success) {
                    player.sendMessage(Message.join(
                        Message.raw("Removed ").color(Color.YELLOW),
                        Message.raw("-" + Main.CONFIG.get().format(amount)).color(new Color(255, 99, 71)),
                        Message.raw(" | New balance: ").color(Color.GRAY),
                        Message.raw(Main.CONFIG.get().format(newBalance)).color(Color.WHITE)
                    ));
                } else {
                    player.sendMessage(Message.raw("Insufficient funds").color(Color.RED));
                }
            }, world);
        }
    }
    
    // ========== RESET COMMAND ==========
    private static class EcoResetCommand extends AbstractAsyncCommand {
        public EcoResetCommand() {
            super("reset", "Reset balance to starting amount");
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return CompletableFuture.completedFuture(null);
            
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                double startingBalance = Main.CONFIG.get().getStartingBalance();
                Main.getInstance().getEconomyManager().setBalance(playerRef.getUuid(), startingBalance, "Admin reset");
                updateHud(playerRef.getUuid(), startingBalance);
                
                player.sendMessage(Message.join(
                    Message.raw("Balance reset to ").color(Color.GREEN),
                    Message.raw(Main.CONFIG.get().format(startingBalance)).color(new Color(50, 205, 50))
                ));
            }, world);
        }
    }
    
    // ========== TOP COMMAND ==========
    private static class EcoTopCommand extends AbstractAsyncCommand {
        public EcoTopCommand() {
            super("top", "Show top balances");
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            Map<UUID, PlayerBalance> balances = Main.getInstance().getEconomyManager().getAllBalances();
            
            if (balances.isEmpty()) {
                ctx.sendMessage(Message.raw("No player balances found").color(Color.GRAY));
                return CompletableFuture.completedFuture(null);
            }
            
            ctx.sendMessage(Message.raw("=== Top Balances ===").color(new Color(255, 215, 0)));
            
            var h2Storage = Main.getInstance().getEconomyManager().getH2Storage();
            
            // Get top 10 sorted by balance
            List<PlayerBalance> top10 = balances.values().stream()
                .sorted(Comparator.comparingDouble(PlayerBalance::getBalance).reversed())
                .limit(10)
                .toList();
            
            // Build name resolution futures
            List<CompletableFuture<String>> nameFutures = top10.stream()
                .map(balance -> {
                    if (h2Storage != null) {
                        return h2Storage.getPlayerNameAsync(balance.getPlayerUuid())
                            .thenApply(name -> name != null ? name : balance.getPlayerUuid().toString().substring(0, 8) + "...");
                    } else {
                        return CompletableFuture.completedFuture(balance.getPlayerUuid().toString().substring(0, 8) + "...");
                    }
                })
                .toList();
            
            // Wait for all names to resolve, then display
            return CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    for (int i = 0; i < top10.size(); i++) {
                        PlayerBalance balance = top10.get(i);
                        String displayName = nameFutures.get(i).join(); // Already completed
                        String formatted = Main.CONFIG.get().format(balance.getBalance());
                        ctx.sendMessage(Message.join(
                            Message.raw("#" + (i + 1) + " ").color(Color.GRAY),
                            Message.raw(displayName).color(Color.WHITE),
                            Message.raw(" - ").color(Color.GRAY),
                            Message.raw(formatted).color(new Color(50, 205, 50))
                        ));
                    }
                });
        }
    }
    
    // ========== SAVE COMMAND ==========
    private static class EcoSaveCommand extends AbstractAsyncCommand {
        public EcoSaveCommand() {
            super("save", "Force save all data");
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            Main.getInstance().getEconomyManager().forceSave();
            ctx.sendMessage(Message.raw("✓ Economy data saved successfully").color(Color.GREEN));
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // ========== HUD COMMAND ==========
    private static class EcoHudCommand extends AbstractAsyncCommand {
        public EcoHudCommand() {
            super("hud", "Toggle HUD display on/off");
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            var config = Main.CONFIG.get();
            boolean newValue = !config.isEnableHudDisplay();
            config.setEnableHudDisplay(newValue);
            
            String status = newValue ? "§aEnabled" : "§cDisabled";
            ctx.sendMessage(Message.raw("HUD Display: " + status).color(newValue ? Color.GREEN : Color.RED));
            ctx.sendMessage(Message.raw("Use /eco save to persist this change").color(Color.GRAY));
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // ========== METRICS COMMAND ==========
    private static class EcoMetricsCommand extends AbstractAsyncCommand {
        public EcoMetricsCommand() {
            super("metrics", "Show performance and scaling metrics");
            this.addAliases("stats", "perf");
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            var monitor = com.ecotale.util.PerformanceMonitor.getInstance();
            if (monitor != null) {
                Color gold = new Color(255, 215, 0);
                Color white = Color.WHITE;
                Color green = new Color(50, 205, 50);

                ctx.sendMessage(Message.raw("--- Ecotale Economy Metrics ---").color(gold));
                
                ctx.sendMessage(Message.join(
                    Message.raw("Cached Balances: ").color(white),
                    Message.raw(monitor.getCachedPlayers() + " / 1000").color(green)
                ));
                
                ctx.sendMessage(Message.raw("---------------------------------").color(gold));
                ctx.sendMessage(Message.raw("System metrics moved to /guard metrics").color(Color.GRAY));
            } else {
                ctx.sendMessage(Message.raw("Performance monitor is not active.").color(Color.RED));
            }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // ========== HELPER METHODS ==========
    private static void updateHud(UUID playerUuid, double newBalance) {
        BalanceHud hud = BalanceHudSystem.getHud(playerUuid);
        if (hud != null) {
            hud.updateBalance(newBalance);
        }
    }
}
