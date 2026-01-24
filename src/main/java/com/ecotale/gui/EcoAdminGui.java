package com.ecotale.gui;

import com.ecotale.Main;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.economy.TransactionEntry;
import com.ecotale.economy.TransactionLogger;
import com.ecotale.systems.BalanceHudSystem;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EcoAdminGui - Complete admin panel for economy management.
 * 
 * Triggered by: /eco (without subcommands)
 * 
 * Features:
 * - Dashboard with stats (total circulating, player count, average)
 * - Player list with give/take/set/reset actions
 * - Top 10 leaderboard
 * - Force save button
 */
public class EcoAdminGui extends InteractiveCustomUIPage<EcoAdminGui.AdminGuiData> {
    
    private enum Tab { DASHBOARD, PLAYERS, TOP, LOG, CONFIG }
    
    private static final int PAGE_SIZE = 20;
    private static final int LOG_SIZE = 50;
    
    // Available languages (scalable - add new languages here)
    private static final List<String> AVAILABLE_LANGUAGES = List.of(
        "en-US", "es-ES", "pt-BR", "fr-FR", "de-DE", "ru-RU"
    );
    
    // Store playerRef for per-player translations
    private final PlayerRef playerRef;
    
    private Tab currentTab = Tab.DASHBOARD;
    private String searchQuery = "";
    private String logFilter = "";
    private int currentPage = 0;
    private int logPage = 0;  // Separate pagination for LOG tab
    
    // Selection state
    private String selectedPlayerUuid = null;
    private String selectedPlayerName = null;
    private String amountInput = "";

    // Reset confirmation state
    private String confirmingResetUuid = null;
    
    // Post-action feedback
    private String lastFeedback = null;
    
    public EcoAdminGui(@NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminGuiData.CODEC);
        this.playerRef = playerRef;
    }
    
    /**
     * SECURITY: Centralized permission check for admin actions.
     * Matches the permission required by /eco command.
     */
    private boolean hasAdminPermission() {
        return com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
            .hasPermission(playerRef.getUuid(), "ecotale.admin");
    }
    
    // Per-player translation helper
    private String t(String key, String fallback) {
        return com.ecotale.util.TranslationHelper.t(playerRef, key, fallback);
    }
    
    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, 
                      @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/Ecotale_AdminPage.ui");
        
        // Setup header buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", 
            EventData.of("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ForceSaveButton", 
            EventData.of("Action", "ForceSave"), false);
        
        // Setup tab buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDashboard", 
            EventData.of("Tab", "Dashboard"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabPlayers", 
            EventData.of("Tab", "Players"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabTop", 
            EventData.of("Tab", "Top"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabLog", 
            EventData.of("Tab", "Log"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabConfig", 
            EventData.of("Tab", "Config"), false);
        
        // Config tab bindings
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadConfigButton", 
            EventData.of("Action", "ReloadConfig"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveConfigButton", 
            EventData.of("Action", "SaveConfig"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#HudToggle", 
            EventData.of("Action", "ToggleHud"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LangToggle", 
            EventData.of("Action", "ToggleLang"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PerPlayerToggle", 
            EventData.of("Action", "TogglePerPlayer"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetDefaultsButton", 
            EventData.of("Action", "ResetDefaults"), false);
        
        // Config text field bindings (following ImageImportPage pattern)
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CurrencySymbolInput",
            EventData.of("@ConfigSymbol", "#CurrencySymbolInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CurrencyNameInput",
            EventData.of("@ConfigName", "#CurrencyNameInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#StartingBalanceInput",
            EventData.of("@ConfigStarting", "#StartingBalanceInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MaxBalanceInput",
            EventData.of("@ConfigMax", "#MaxBalanceInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DecimalsInput",
            EventData.of("@ConfigDecimals", "#DecimalsInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TransferFeeInput",
            EventData.of("@ConfigFee", "#TransferFeeInput.Value"), false);
        
        cmd.set("#PlayerSearch.Value", this.searchQuery);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#PlayerSearch",
            EventData.of("@SearchQuery", "#PlayerSearch.Value"), false);
        
        // Log filter binding
        cmd.set("#LogFilter.Value", this.logFilter);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#LogFilter",
            EventData.of("@LogFilter", "#LogFilter.Value"), false);
        

        // Action panel bindings
        cmd.set("#ActionAmount.Value", this.amountInput);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ActionAmount",
            EventData.of("@AmountInput", "#ActionAmount.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GiveButton", 
            EventData.of("Action", "Give"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeButton", 
            EventData.of("Action", "Take"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SetButton", 
            EventData.of("Action", "Set"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton", 
            EventData.of("Action", "Reset"), false);
        
        // Pagination bindings
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton", 
            EventData.of("Action", "PrevPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton", 
            EventData.of("Action", "NextPage"), false);
        
        // Build current tab content
        buildDashboard(cmd);
        buildPlayersTab(cmd, events);
        buildTopTab(cmd);
        buildLogTab(cmd);
        buildConfigTab(cmd);
        
        // Translate all UI elements based on server config language
        translateUI(cmd);
        
        // Show/hide tabs based on current selection
        updateTabVisibility(cmd);
    }
    
    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, 
                                @NonNullDecl AdminGuiData data) {
        super.handleDataEvent(ref, store, data);
        
        // === SECURITY: Verify permission before processing ANY event ===
        if (!hasAdminPermission()) {
            com.ecotale.security.SecurityLogger logger = com.ecotale.security.SecurityLogger.getInstance();
            if (logger != null) {
                String details = data.tab != null ? "Tab: " + data.tab : 
                                (data.action != null ? "Action: " + data.action : "Data update");
                logger.logUnauthorizedAccess(playerRef, "EcoAdminGui", "Interaction", details);
            }
            
            this.close(); // Force close the GUI
            playerRef.sendMessage(Message.raw("[Ecotale] Access denied. This incident has been logged.").color(Color.RED));
            return;
        }
        
        // Handle tab change
        if (data.tab != null) {
            switch (data.tab) {
                case "Dashboard" -> currentTab = Tab.DASHBOARD;
                case "Players" -> currentTab = Tab.PLAYERS;
                case "Top" -> currentTab = Tab.TOP;
                case "Log" -> currentTab = Tab.LOG;
                case "Config" -> currentTab = Tab.CONFIG;
            }
            refreshUI(ref, store);
            return;
        }
        
        // Handle search - reset to page 0 when search changes
        if (data.searchQuery != null) {
            String newQuery = data.searchQuery.trim().toLowerCase();
            if (!newQuery.equals(this.searchQuery)) {
                this.currentPage = 0;  // Reset pagination on new search
            }
            this.searchQuery = newQuery;
            refreshUI(ref, store);
            return;
        }
        
        // Handle amount input
        if (data.amountInput != null) {
            this.amountInput = data.amountInput;
            return;
        }
        
        // Handle log filter
        if (data.logFilter != null) {
            this.logFilter = data.logFilter.trim().toLowerCase();
            refreshUI(ref, store);
            return;
        }
        
        // Handle config field changes - Silent Rejection pattern
        // Invalid input is simply not applied; UI refreshes with previous valid value
        
        if (data.configSymbol != null && !data.configSymbol.isEmpty()) {
            // Validate: ASCII only, max 5 chars
            if (data.configSymbol.length() <= 5 && isAsciiPrintable(data.configSymbol)) {
                Main.CONFIG.get().setCurrencySymbol(data.configSymbol);
            }
            // Always refresh to show current (valid) value
            refreshUI(ref, store);
            return;
        }
        
        if (data.configName != null) {
            // Validate: max 20 chars
            if (data.configName.length() <= 20) {
                Main.CONFIG.get().setHudPrefix(data.configName);
            }
            refreshUI(ref, store);
            return;
        }
        
        // Numeric fields - silent rejection on invalid input
        if (data.configStarting != null && !data.configStarting.isEmpty()) {
            try {
                double value = Double.parseDouble(data.configStarting.replace(",", ""));
                if (value >= 0 && value <= Main.CONFIG.get().getMaxBalance()) {
                    Main.CONFIG.get().setStartingBalance(value);
                }
            } catch (NumberFormatException ignored) {}
            refreshUI(ref, store);
            return;
        }
        
        if (data.configMax != null && !data.configMax.isEmpty()) {
            try {
                double value = Double.parseDouble(data.configMax.replace(",", ""));
                if (value >= 1 && value <= 1e12) {
                    Main.CONFIG.get().setMaxBalance(value);
                }
            } catch (NumberFormatException ignored) {}
            refreshUI(ref, store);
            return;
        }
        
        if (data.configDecimals != null && !data.configDecimals.isEmpty()) {
            try {
                int value = Integer.parseInt(data.configDecimals.trim());
                if (value >= 0 && value <= 4) {
                    Main.CONFIG.get().setDecimalPlaces(value);
                }
            } catch (NumberFormatException ignored) {}
            refreshUI(ref, store);
            return;
        }
        
        if (data.configFee != null && !data.configFee.isEmpty()) {
            try {
                double value = Double.parseDouble(data.configFee.replace("%", "").trim());
                if (value >= 0 && value <= 100) {
                    Main.CONFIG.get().setTransferFee(value / 100.0);
                }
            } catch (NumberFormatException ignored) {}
            refreshUI(ref, store);
            return;
        }
        
        // Handle player selection
        if (data.playerAction != null && data.playerAction.equals("Select") && data.playerUuid != null) {
            selectedPlayerUuid = data.playerUuid;
            selectedPlayerName = data.playerName;
            refreshUI(ref, store);
            return;
        }
        
        // Handle reset confirmation cancel (mouse exited)
        if (data.action != null && data.action.equals("CancelResetConfirm")) {
            confirmingResetUuid = null;
            refreshUI(ref, store);
            return;
        }
        
        // Handle action buttons from the action panel
        if (data.action != null) {
            Main.getInstance().getLogger().at(java.util.logging.Level.INFO).log("EcoAdminGui action received: %s", data.action);
            switch (data.action) {
                case "Close" -> {
                    this.close();
                    return;
                }
                case "ForceSave" -> {
                    Main.getInstance().getEconomyManager().forceSave();
                    playerRef.sendMessage(Message.raw("Economy data saved!").color(Color.GREEN));
                    return;
                }
                case "Give", "Take", "Set" -> {
                    if (selectedPlayerUuid != null) {
                        double amount = parseAmount(amountInput);
                        if (amount >= 0) {  // -1 means invalid; 0 is valid for Set
                            executeAction(data.action, selectedPlayerUuid, selectedPlayerName, amount, ref, store);
                            amountInput = "";
                        } else {
                            playerRef.sendMessage(Message.raw("Invalid amount (max: " + 
                                Main.CONFIG.get().format(Main.CONFIG.get().getMaxBalance()) + ")").color(Color.RED));
                        }
                    } else {
                        playerRef.sendMessage(Message.raw("Select a player first").color(Color.RED));
                    }
                    refreshUI(ref, store);
                    return;
                }
                case "Reset" -> {
                    if (selectedPlayerUuid != null) {
                        if (selectedPlayerUuid.equals(confirmingResetUuid)) {
                            // Second click - execute reset
                            executeReset(selectedPlayerUuid, selectedPlayerName, ref, store);
                            confirmingResetUuid = null;
                        } else {
                            // First click - enter confirmation mode
                            confirmingResetUuid = selectedPlayerUuid;
                        }
                    } else {
                        playerRef.sendMessage(Message.raw("Select a player first").color(Color.RED));
                    }
                    refreshUI(ref, store);
                    return;
                }
                case "PrevPage" -> {
                    if (currentPage > 0) {
                        currentPage--;
                    }
                    refreshUI(ref, store);
                    return;
                }
                case "NextPage" -> {
                    // Max page check happens in buildPlayersTab
                    currentPage++;
                    refreshUI(ref, store);
                    return;
                }
                // Config actions
                case "ReloadConfig" -> {
                    Main.CONFIG.load();
                    com.ecotale.util.TranslationHelper.invalidateCache(); // Invalidate translation cache
                    playerRef.sendMessage(Message.raw("Configuration reloaded!").color(Color.GREEN));
                    refreshUI(ref, store);
                    return;
                }
                case "SaveConfig" -> {
                    Main.CONFIG.save();
                    BalanceHudSystem.refreshAllHuds(); // Apply config changes to all HUDs
                    playerRef.sendMessage(Message.raw("Configuration saved to file!").color(Color.GREEN));
                    refreshUI(ref, store);
                    return;
                }
                case "ToggleHud" -> {
                    var config = Main.CONFIG.get();
                    boolean newValue = !config.isEnableHudDisplay();
                    config.setEnableHudDisplay(newValue);
                    playerRef.sendMessage(Message.raw("HUD: " + (newValue ? "Enabled" : "Disabled")).color(Color.GREEN));
                    refreshUI(ref, store);
                    return;
                }
                case "ToggleLang" -> {
                    var config = Main.CONFIG.get();
                    String current = config.getLanguage();
                    int currentIndex = AVAILABLE_LANGUAGES.indexOf(current);
                    int nextIndex = (currentIndex + 1) % AVAILABLE_LANGUAGES.size();
                    String next = AVAILABLE_LANGUAGES.get(nextIndex);
                    config.setLanguage(next);
                    com.ecotale.util.TranslationHelper.invalidateCache(); // Invalidate translation cache
                    playerRef.sendMessage(Message.raw("Language: " + next).color(Color.GREEN));
                    refreshUI(ref, store);
                    return;
                }
                case "TogglePerPlayer" -> {
                    var config = Main.CONFIG.get();
                    boolean newValue = !config.isUsePlayerLanguage();
                    config.setUsePlayerLanguage(newValue);
                    playerRef.sendMessage(Message.raw("Per-player language: " + (newValue ? "Enabled" : "Disabled")).color(Color.GREEN));
                    refreshUI(ref, store);
                    return;
                }
                case "ResetDefaults" -> {
                    var config = Main.CONFIG.get();
                    // Reset to defaults
                    config.setCurrencySymbol("$");
                    config.setHudPrefix("Bank");
                    config.setStartingBalance(100);
                    config.setMaxBalance(1_000_000_000);
                    config.setDecimalPlaces(2);
                    config.setEnableHudDisplay(true);
                    config.setLanguage("en-US");
                    config.setUsePlayerLanguage(false);
                    BalanceHudSystem.refreshAllHuds();
                    playerRef.sendMessage(Message.raw("Config reset to defaults!").color(Color.YELLOW));
                    refreshUI(ref, store);
                    return;
                }

            }
        }
        
        this.sendUpdate();
    }
    
    private void buildDashboard(@NonNullDecl UICommandBuilder cmd) {
        var allBalances = Main.getInstance().getEconomyManager().getAllBalances();
        
        double totalCirculating = allBalances.values().stream()
            .mapToDouble(PlayerBalance::getBalance)
            .sum();
        
        int playerCount = allBalances.size();
        double average = playerCount > 0 ? totalCirculating / playerCount : 0;
        
        cmd.set("#TotalCirculating.Text", Main.CONFIG.get().format(totalCirculating));
        cmd.set("#TotalPlayers.Text", String.valueOf(playerCount));
        cmd.set("#AverageBalance.Text", Main.CONFIG.get().format(average));
        
        // Config info
        cmd.set("#ConfigMaxBalance.Text", Main.CONFIG.get().formatShort(Main.CONFIG.get().getMaxBalance()));
        cmd.set("#ConfigTransferFee.Text", String.format("%.1f%%", Main.CONFIG.get().getTransferFee() * 100));
        cmd.set("#ConfigAutoSave.Text", (Main.CONFIG.get().getAutoSaveInterval() / 60) + " min");
        
        // Activity Log - show last 15 transactions
        cmd.clear("#ActivityLog");
        var recentActivity = TransactionLogger.getInstance().getRecent(15);
        
        if (recentActivity.isEmpty()) {
            cmd.appendInline("#ActivityLog", 
                "Label { Text: \"No recent activity\"; Style: (FontSize: 11, TextColor: #888888); }");
        } else {
            for (TransactionEntry entry : recentActivity) {
                String displayText = entry.toDisplayString();
                // Escape quotes in display text
                displayText = displayText.replace("\"", "'");
                cmd.appendInline("#ActivityLog", 
                    "Label { Text: \"" + displayText + "\"; Style: (FontSize: 11, TextColor: #aaaaaa); Anchor: (Bottom: 2); }");
            }
        }
    }
    
    private void buildPlayersTab(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events) {
        cmd.clear("#PlayerList");
        
        var allBalances = Main.getInstance().getEconomyManager().getAllBalances();
        
        // Filter and sort
        List<Map.Entry<UUID, PlayerBalance>> filtered = allBalances.entrySet().stream()
            .filter(e -> {
                if (searchQuery.isEmpty()) return true;
                String playerName = getPlayerName(e.getKey());
                return playerName.toLowerCase().contains(searchQuery);
            })
            .sorted((a, b) -> Double.compare(b.getValue().getBalance(), a.getValue().getBalance()))
            .collect(Collectors.toList());
        
        // Calculate pagination
        int totalFiltered = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalFiltered / PAGE_SIZE));
        
        // Clamp currentPage to valid range
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
        
        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalFiltered);
        
        // Update page info
        cmd.set("#PageInfo.Text", "Page " + (currentPage + 1) + " of " + totalPages);
        
        // Render current page entries
        int displayIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            var entry = filtered.get(i);
            UUID uuid = entry.getKey();
            PlayerBalance balance = entry.getValue();
            String playerName = getPlayerName(uuid);
            
            String uuidStr = uuid.toString();
            boolean isSelected = uuidStr.equals(selectedPlayerUuid);
            
            cmd.append("#PlayerList", "Pages/Ecotale_AdminPlayerEntry.ui");
            cmd.set("#PlayerList[" + displayIndex + "] #PlayerName.Text", playerName);
            cmd.set("#PlayerList[" + displayIndex + "] #PlayerBalance.Text", Main.CONFIG.get().format(balance.getBalance()));
            cmd.set("#PlayerList[" + displayIndex + "] #SelectionIndicator.Visible", isSelected);
            
            // Bind click to select
            events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerList[" + displayIndex + "]",
                EventData.of("PlayerAction", "Select").append("PlayerUuid", uuidStr).append("PlayerName", playerName), false);
            
            displayIndex++;
        }
        
        // Update selected player info in action panel
        if (selectedPlayerUuid != null && selectedPlayerName != null) {
            cmd.set("#SelectedLabel.Text", "Selected:");
            cmd.set("#SelectedName.Text", selectedPlayerName);
            
            // Update Reset button text based on confirmation state
            if (selectedPlayerUuid.equals(confirmingResetUuid)) {
                cmd.set("#ResetButton.Text", "OK?");
            } else {
                cmd.set("#ResetButton.Text", "RESET");
            }
        } else {
            cmd.set("#SelectedLabel.Text", "Select a player");
            cmd.set("#SelectedName.Text", "");
            cmd.set("#ResetButton.Text", "RESET");
        }
        
        // Show feedback if any
        if (lastFeedback != null) {
            cmd.set("#SelectedName.Text", selectedPlayerName + " " + lastFeedback);
            lastFeedback = null;
        }
        
        if (displayIndex == 0) {
            cmd.appendInline("#PlayerList", "Group { LayoutMode: Center; Anchor: (Height: 60); " +
                "Label { Text: \"No players found\"; Style: (FontSize: 14, TextColor: #888888, HorizontalAlignment: Center); } }");
        }
    }
    
    private void buildTopTab(@NonNullDecl UICommandBuilder cmd) {
        cmd.clear("#TopList");
        
        var allBalances = Main.getInstance().getEconomyManager().getAllBalances();
        
        // Sort by balance descending, take top 10
        List<Map.Entry<UUID, PlayerBalance>> top10 = allBalances.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().getBalance(), a.getValue().getBalance()))
            .limit(10)
            .collect(Collectors.toList());
        
        int rank = 1;
        for (var entry : top10) {
            UUID uuid = entry.getKey();
            PlayerBalance balance = entry.getValue();
            String playerName = getPlayerName(uuid);
            
            cmd.append("#TopList", "Pages/Ecotale_AdminTopEntry.ui");
            cmd.set("#TopList[" + (rank-1) + "] #Rank.Text", "#" + rank);
            cmd.set("#TopList[" + (rank-1) + "] #PlayerName.Text", playerName);
            cmd.set("#TopList[" + (rank-1) + "] #PlayerBalance.Text", Main.CONFIG.get().format(balance.getBalance()));
            
            rank++;
        }
        
        if (top10.isEmpty()) {
            cmd.appendInline("#TopList", "Label { Text: \"No data available\"; Style: (FontSize: 14, TextColor: #888888); Padding: (Top: 20); }");
        }
    }
    
    private void buildLogTab(@NonNullDecl UICommandBuilder cmd) {
        cmd.clear("#LogList");
        
        var h2Storage = Main.getInstance().getEconomyManager().getH2Storage();
        
        // LOG tab only works with H2 storage
        if (h2Storage == null) {
            cmd.appendInline("#LogList", "Label { Text: \"Transaction log requires H2 storage provider.\"; Style: (FontSize: 14, TextColor: #888888); Padding: (Top: 20); }");
            return;
        }
        
        // Show loading state
        cmd.set("#LogCountInfo.Text", "Loading...");
        
        // Query H2 asynchronously to avoid blocking main thread
        String filter = logFilter.isEmpty() ? null : logFilter;
        int offset = logPage * LOG_SIZE;
        
        // Combine both queries into one async operation
        // IMPORTANT: Use thenCombineAsync to run callback on ForkJoinPool, NOT on H2 executor
        // Otherwise the callback blocks the H2 executor and causes deadlock on shutdown
        h2Storage.countTransactionsAsync(filter).thenCombineAsync(
            h2Storage.queryTransactionsAsync(filter, LOG_SIZE, offset),
            (totalCount, entries) -> {
                // Build UI update on the result
                UICommandBuilder asyncCmd = new UICommandBuilder();
                asyncCmd.clear("#LogList");
                
                // Calculate total pages
                int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / LOG_SIZE));
                
                // Update count and page info
                int showing = entries.size();
                int startIdx = logPage * LOG_SIZE + 1;
                int endIdx = startIdx + showing - 1;
                if (totalCount == 0) {
                    asyncCmd.set("#LogCountInfo.Text", "No transactions");
                } else {
                    asyncCmd.set("#LogCountInfo.Text", 
                        String.format("Showing %d-%d of %d (Page %d/%d)", startIdx, endIdx, totalCount, logPage + 1, totalPages));
                }
                
                if (entries.isEmpty()) {
                    if (logFilter.isEmpty()) {
                        asyncCmd.appendInline("#LogList", 
                            "Label { Text: \"No transactions recorded yet\"; Style: (FontSize: 12, TextColor: #888888); Anchor: (Top: 20); }");
                    } else {
                        asyncCmd.appendInline("#LogList", 
                            "Label { Text: \"No matches for '" + logFilter + "'\"; Style: (FontSize: 12, TextColor: #888888); Anchor: (Top: 20); }");
                    }
                } else {
                    for (TransactionEntry entry : entries) {
                        String displayText = entry.toDisplayString().replace("\"", "'");
                        asyncCmd.appendInline("#LogList", 
                            "Label { Text: \"" + displayText + "\"; Style: (FontSize: 11, TextColor: #cccccc); Anchor: (Bottom: 3); }");
                    }
                }
                
                // Send async update to client
                this.sendUpdate(asyncCmd, new UIEventBuilder(), false);
                return null;
            }
        );
    }
    
    private void buildConfigTab(@NonNullDecl UICommandBuilder cmd) {
        var config = Main.CONFIG.get();
        String lang = config.getLanguage(); // Get server language setting
        
        // Currency fields
        cmd.set("#CurrencySymbolInput.Value", config.getCurrencySymbol());
        cmd.set("#CurrencyNameInput.Value", config.getHudPrefix());
        
        // Balance fields
        cmd.set("#StartingBalanceInput.Value", String.valueOf((long) config.getStartingBalance()));
        cmd.set("#MaxBalanceInput.Value", String.valueOf((long) config.getMaxBalance()));
        
        // Transfer fee (displayed as percentage, stored as decimal)
        cmd.set("#TransferFeeInput.Value", String.valueOf((int) (config.getTransferFee() * 100)));
        
        // Display fields - use per-player t() helper
        String yesText = t("gui.config.yes", "Yes");
        String noText = t("gui.config.no", "No");
        
        cmd.set("#HudToggle.Text", config.isEnableHudDisplay() ? yesText : noText);
        cmd.set("#DecimalsInput.Value", String.valueOf(config.getDecimalPlaces()));
        
        // Language fields
        cmd.set("#LangToggle.Text", config.getLanguage());
        cmd.set("#PerPlayerToggle.Text", config.isUsePlayerLanguage() ? yesText : noText);
    }
    
    /** Check if string contains only ASCII printable characters (32-126) */
    private boolean isAsciiPrintable(String str) {
        for (char c : str.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }
    
    /** Translate all static UI elements using shared TranslationHelper */
    private void translateUI(@NonNullDecl UICommandBuilder cmd) {
        // Header
        cmd.set("#Title.Text", t("gui.admin.title", "Economy Admin Panel"));
        cmd.set("#ForceSaveButton.Text", t("gui.admin.force_save", "FORCE SAVE"));
        
        // Tab buttons
        cmd.set("#TabDashboard.Text", t("gui.admin.tab.dashboard", "DASHBOARD"));
        cmd.set("#TabPlayers.Text", t("gui.admin.tab.players", "PLAYERS"));
        cmd.set("#TabTop.Text", t("gui.admin.tab.top", "TOP"));
        cmd.set("#TabLog.Text", t("gui.admin.tab.log", "LOG"));
        cmd.set("#TabConfig.Text", t("gui.admin.tab.config", "CONFIG"));
        
        // Dashboard labels
        cmd.set("#LblTotalCirculating.Text", t("gui.dashboard.total_circulating", "Total Circulating"));
        cmd.set("#LblPlayersWithBalance.Text", t("gui.dashboard.total_players", "Players with Balance"));
        cmd.set("#LblAverageBalance.Text", t("gui.dashboard.avg_balance", "Average Balance"));
        cmd.set("#LblCurrentConfig.Text", t("gui.dashboard.current_config", "Current Configuration"));
        cmd.set("#LblRecentActivity.Text", t("gui.dashboard.recent_activity", "Recent Activity"));
        
        // Players tab
        cmd.set("#LblSearch.Text", t("gui.players.search_label", "Search"));
        cmd.set("#LblAmount.Text", t("gui.players.amount", "Amount:"));
        cmd.set("#SelectedLabel.Text", t("gui.players.select_player", "Select a player"));
        cmd.set("#GiveButton.Text", t("gui.players.give", "GIVE"));
        cmd.set("#TakeButton.Text", t("gui.players.take", "TAKE"));
        cmd.set("#SetButton.Text", t("gui.players.set", "SET"));
        cmd.set("#ResetButton.Text", t("gui.players.reset", "RESET"));
        
        // Config tab section headers
        cmd.set("#LblCurrency.Text", t("gui.config.currency", "Currency"));
        cmd.set("#LblLimits.Text", t("gui.config.limits", "Balance Limits"));
        cmd.set("#LblDisplay.Text", t("gui.config.display", "Display"));
        cmd.set("#LblLanguage.Text", t("gui.config.language", "Language"));
        
        // Config tab buttons
        cmd.set("#SaveConfigButton.Text", t("gui.config.save", "SAVE CONFIG"));
        cmd.set("#ResetDefaultsButton.Text", t("gui.config.reset_defaults", "RESET DEFAULTS"));
    }
    
    private void updateTabVisibility(@NonNullDecl UICommandBuilder cmd) {
        cmd.set("#DashboardContent.Visible", currentTab == Tab.DASHBOARD);
        cmd.set("#PlayersContent.Visible", currentTab == Tab.PLAYERS);
        cmd.set("#TopContent.Visible", currentTab == Tab.TOP);
        cmd.set("#LogContent.Visible", currentTab == Tab.LOG);
        cmd.set("#ConfigContent.Visible", currentTab == Tab.CONFIG);
    }
    
    private void executeAction(String action, String uuidStr, String displayName, double amount, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID targetUuid = UUID.fromString(uuidStr);
        var economyManager = Main.getInstance().getEconomyManager();
        String resolvedName = (displayName != null && !displayName.isBlank()) ? displayName : getPlayerName(targetUuid);
        
        switch (action) {
            case "Give" -> {
                economyManager.deposit(targetUuid, amount, "Admin give via GUI");
                playerRef.sendMessage(Message.join(
                    Message.raw("Gave ").color(Color.GREEN),
                    Message.raw(Main.CONFIG.get().format(amount)).color(new Color(50, 205, 50)),
                    Message.raw(" to " + resolvedName).color(Color.GREEN)
                ));
                lastFeedback = "+" + Main.CONFIG.get().formatShort(amount);
            }
            case "Take" -> {
                boolean success = economyManager.withdraw(targetUuid, amount, "Admin take via GUI");
                if (success) {
                    playerRef.sendMessage(Message.join(
                        Message.raw("Took ").color(Color.ORANGE),
                        Message.raw(Main.CONFIG.get().format(amount)).color(new Color(255, 165, 0)),
                        Message.raw(" from " + resolvedName).color(Color.ORANGE)
                    ));
                    lastFeedback = "-" + Main.CONFIG.get().formatShort(amount);
                } else {
                    playerRef.sendMessage(Message.raw("Failed - insufficient funds").color(Color.RED));
                }
            }
            case "Set" -> {
                economyManager.setBalance(targetUuid, amount, "Admin set via GUI");
                playerRef.sendMessage(Message.join(
                    Message.raw("Set " + resolvedName + " to ").color(Color.YELLOW),
                    Message.raw(Main.CONFIG.get().format(amount)).color(new Color(255, 215, 0))
                ));
                lastFeedback = "=" + Main.CONFIG.get().formatShort(amount);
            }
        }
    }
    
    private void executeReset(String uuidStr, String displayName, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID targetUuid = UUID.fromString(uuidStr);
        var economyManager = Main.getInstance().getEconomyManager();
        String resolvedName = (displayName != null && !displayName.isBlank()) ? displayName : getPlayerName(targetUuid);
        double startingBalance = Main.CONFIG.get().getStartingBalance();
        
        economyManager.setBalance(targetUuid, startingBalance, "Admin reset via GUI");
        playerRef.sendMessage(Message.join(
            Message.raw("Reset " + resolvedName + " to ").color(Color.GRAY),
            Message.raw(Main.CONFIG.get().format(startingBalance)).color(Color.WHITE)
        ));
        lastFeedback = "RESET";
    }
    
    /**
     * Parse amount input with validation.
     * Returns -1 for invalid input (allows 0 to be valid for Set action).
     */
    private double parseAmount(String input) {
        if (input == null || input.trim().isEmpty()) return -1;
        try {
            // Cleanup input - allow comma as decimal separator
            String cleaned = input.trim().replace(",", ".").replace("$", "").replace(" ", "");
            double amount = Double.parseDouble(cleaned);
            
            // Validate bounds
            if (amount < 0) return -1;
            if (amount > Main.CONFIG.get().getMaxBalance()) {
                return -1; // Exceeds max balance
            }
            
            return amount;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private String getPlayerName(UUID uuid) {
        PlayerRef onlinePlayer = Universe.get().getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getUsername();
        }
        return uuid.toString().substring(0, 8) + "...";
    }
    
    private void refreshUI(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        
        buildDashboard(cmd);
        buildPlayersTab(cmd, events);
        buildTopTab(cmd);
        buildLogTab(cmd);
        buildConfigTab(cmd);
        
        // Re-translate UI elements (for language changes)
        translateUI(cmd);
        
        updateTabVisibility(cmd);
        
        // Update action panel bindings
        cmd.set("#ActionAmount.Value", this.amountInput);
        
        this.sendUpdate(cmd, events, false);
    }
    
    // ========== Data Codec ==========
    
    public static class AdminGuiData {
        private static final String KEY_TAB = "Tab";
        private static final String KEY_SEARCH = "@SearchQuery";
        private static final String KEY_AMOUNT = "@AmountInput";
        private static final String KEY_LOG_FILTER = "@LogFilter";
        private static final String KEY_ACTION = "Action";
        private static final String KEY_PLAYER_ACTION = "PlayerAction";
        private static final String KEY_PLAYER_UUID = "PlayerUuid";
        private static final String KEY_PLAYER_NAME = "PlayerName";
        // Config input keys
        private static final String KEY_CONFIG_SYMBOL = "@ConfigSymbol";
        private static final String KEY_CONFIG_NAME = "@ConfigName";
        private static final String KEY_CONFIG_STARTING = "@ConfigStarting";
        private static final String KEY_CONFIG_MAX = "@ConfigMax";
        private static final String KEY_CONFIG_DECIMALS = "@ConfigDecimals";
        private static final String KEY_CONFIG_FEE = "@ConfigFee";
        
        public static final BuilderCodec<AdminGuiData> CODEC = BuilderCodec.<AdminGuiData>builder(AdminGuiData.class, AdminGuiData::new)
            .append(new KeyedCodec<>(KEY_TAB, Codec.STRING), (d, v, e) -> d.tab = v, (d, e) -> d.tab).add()
            .append(new KeyedCodec<>(KEY_SEARCH, Codec.STRING), (d, v, e) -> d.searchQuery = v, (d, e) -> d.searchQuery).add()
            .append(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (d, v, e) -> d.amountInput = v, (d, e) -> d.amountInput).add()
            .append(new KeyedCodec<>(KEY_LOG_FILTER, Codec.STRING), (d, v, e) -> d.logFilter = v, (d, e) -> d.logFilter).add()
            .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v, e) -> d.action = v, (d, e) -> d.action).add()
            .append(new KeyedCodec<>(KEY_PLAYER_ACTION, Codec.STRING), (d, v, e) -> d.playerAction = v, (d, e) -> d.playerAction).add()
            .append(new KeyedCodec<>(KEY_PLAYER_UUID, Codec.STRING), (d, v, e) -> d.playerUuid = v, (d, e) -> d.playerUuid).add()
            .append(new KeyedCodec<>(KEY_PLAYER_NAME, Codec.STRING), (d, v, e) -> d.playerName = v, (d, e) -> d.playerName).add()
            // Config fields - editable from GUI
            .append(new KeyedCodec<>(KEY_CONFIG_SYMBOL, Codec.STRING), (d, v, e) -> d.configSymbol = v, (d, e) -> d.configSymbol).add()
            .append(new KeyedCodec<>(KEY_CONFIG_NAME, Codec.STRING), (d, v, e) -> d.configName = v, (d, e) -> d.configName).add()
            .append(new KeyedCodec<>(KEY_CONFIG_STARTING, Codec.STRING), (d, v, e) -> d.configStarting = v, (d, e) -> d.configStarting).add()
            .append(new KeyedCodec<>(KEY_CONFIG_MAX, Codec.STRING), (d, v, e) -> d.configMax = v, (d, e) -> d.configMax).add()
            .append(new KeyedCodec<>(KEY_CONFIG_DECIMALS, Codec.STRING), (d, v, e) -> d.configDecimals = v, (d, e) -> d.configDecimals).add()
            .append(new KeyedCodec<>(KEY_CONFIG_FEE, Codec.STRING), (d, v, e) -> d.configFee = v, (d, e) -> d.configFee).add()
            .build();
        
        private String tab;
        private String searchQuery;
        private String amountInput;
        private String logFilter;
        private String action;
        private String playerAction;
        private String playerUuid;
        private String playerName;
        // Config fields
        private String configSymbol;
        private String configName;
        private String configStarting;
        private String configMax;
        private String configDecimals;
        private String configFee;
    }
}

