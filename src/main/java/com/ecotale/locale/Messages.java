package com.ecotale.locale;

import com.ecotale.Main;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Centralized message system for Ecotale.
 * 
 * Uses Hytale's native I18nModule for translations.
 * Supports both server-wide language and per-player language modes.
 * 
 * Usage:
 *   Messages.get(player, "ecotale.pay.success", amount, targetName);
 *   Messages.get("ecotale.admin.save"); // Uses server language
 */
public final class Messages {
    
    private Messages() {} // Static utility class
    
    // ========== Message Keys ==========
    
    // Balance command
    public static final String BALANCE_HEADER = "ecotale.balance.header";
    public static final String BALANCE_CURRENT = "ecotale.balance.current";
    public static final String BALANCE_EARNED = "ecotale.balance.earned";
    public static final String BALANCE_SPENT = "ecotale.balance.spent";
    
    // Pay command
    public static final String PAY_SUCCESS = "ecotale.pay.success";
    public static final String PAY_RECEIVED = "ecotale.pay.received";
    public static final String PAY_FEE = "ecotale.pay.fee";
    public static final String PAY_INSUFFICIENT = "ecotale.pay.insufficient";
    public static final String PAY_INVALID_AMOUNT = "ecotale.pay.invalid_amount";
    public static final String PAY_PLAYER_NOT_FOUND = "ecotale.pay.player_not_found";
    public static final String PAY_SELF = "ecotale.pay.self";
    public static final String PAY_MINIMUM = "ecotale.pay.minimum";
    
    // Admin commands
    public static final String ADMIN_GIVE = "ecotale.admin.give";
    public static final String ADMIN_TAKE = "ecotale.admin.take";
    public static final String ADMIN_SET = "ecotale.admin.set";
    public static final String ADMIN_RESET = "ecotale.admin.reset";
    public static final String ADMIN_SAVE = "ecotale.admin.save";
    public static final String ADMIN_RELOAD = "ecotale.admin.reload";
    public static final String ADMIN_NO_DATA = "ecotale.admin.no_data";
    
    // Top command
    public static final String TOP_HEADER = "ecotale.top.header";
    public static final String TOP_ENTRY = "ecotale.top.entry";
    public static final String TOP_EMPTY = "ecotale.top.empty";
    
    // GUI - Admin
    public static final String GUI_ADMIN_TITLE = "ecotale.gui.admin.title";
    public static final String GUI_TAB_DASHBOARD = "ecotale.gui.admin.tab.dashboard";
    public static final String GUI_TAB_PLAYERS = "ecotale.gui.admin.tab.players";
    public static final String GUI_TAB_TOP = "ecotale.gui.admin.tab.top";
    public static final String GUI_TAB_LOG = "ecotale.gui.admin.tab.log";
    public static final String GUI_TAB_CONFIG = "ecotale.gui.admin.tab.config";
    public static final String GUI_FORCE_SAVE = "ecotale.gui.admin.force_save";
    
    // GUI - Dashboard
    public static final String GUI_ECONOMY_STATS = "ecotale.gui.dashboard.economy_stats";
    public static final String GUI_TOTAL_PLAYERS = "ecotale.gui.dashboard.total_players";
    public static final String GUI_TOTAL_MONEY = "ecotale.gui.dashboard.total_money";
    public static final String GUI_AVG_BALANCE = "ecotale.gui.dashboard.avg_balance";
    public static final String GUI_RECENT_ACTIVITY = "ecotale.gui.dashboard.recent_activity";
    public static final String GUI_NO_ACTIVITY = "ecotale.gui.dashboard.no_activity";
    
    // GUI - Players
    public static final String GUI_SEARCH = "ecotale.gui.players.search";
    public static final String GUI_SELECT_PLAYER = "ecotale.gui.players.select_player";
    public static final String GUI_AMOUNT = "ecotale.gui.players.amount";
    public static final String GUI_GIVE = "ecotale.gui.players.give";
    public static final String GUI_TAKE = "ecotale.gui.players.take";
    public static final String GUI_SET = "ecotale.gui.players.set";
    public static final String GUI_RESET = "ecotale.gui.players.reset";
    public static final String GUI_RESET_CONFIRM = "ecotale.gui.players.reset_confirm";
    public static final String GUI_BALANCE = "ecotale.gui.players.balance";
    public static final String GUI_ONLINE = "ecotale.gui.players.online";
    public static final String GUI_OFFLINE = "ecotale.gui.players.offline";
    
    // GUI - Config
    public static final String GUI_CONFIG_TITLE = "ecotale.gui.config.title";
    public static final String GUI_CONFIG_RELOAD = "ecotale.gui.config.reload";
    public static final String GUI_CONFIG_SAVE = "ecotale.gui.config.save";
    public static final String GUI_CONFIG_SAVED = "ecotale.gui.config.saved";
    
    // Errors
    public static final String ERROR_INVALID_AMOUNT = "ecotale.error.invalid_amount";
    public static final String ERROR_MAX_EXCEEDED = "ecotale.error.max_exceeded";
    public static final String ERROR_SELECT_PLAYER = "ecotale.error.select_player";
    public static final String ERROR_NO_PERMISSION = "ecotale.error.no_permission";
    
    // ========== Core Methods ==========
    
    /**
     * Get the language to use for a player.
     * If usePlayerLanguage is enabled, uses PlayerRef.getLanguage().
     * Otherwise uses the server-configured language.
     */
    @Nonnull
    public static String getLanguageFor(@Nullable PlayerRef player) {
        var config = Main.CONFIG.get();
        if (config.isUsePlayerLanguage() && player != null) {
            return player.getLanguage();
        }
        return config.getLanguage();
    }
    
    /**
     * Get the language to use for a player by UUID.
     */
    @Nonnull
    public static String getLanguageFor(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return Main.CONFIG.get().getLanguage();
        }
        PlayerRef player = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(playerUuid);
        return getLanguageFor(player);
    }
    
    /**
     * Get a translated message for a specific player.
     * Uses the player's language if per-player mode is enabled.
     * 
     * @param player The player to get the message for (can be null for server language)
     * @param key The message key
     * @param args Replacement arguments for {0}, {1}, etc.
     * @return Translated and formatted message
     */
    @Nonnull
    public static String get(@Nullable PlayerRef player, @Nonnull String key, Object... args) {
        String lang = getLanguageFor(player);
        return getForLanguage(lang, key, args);
    }
    
    /**
     * Get a translated message using server language.
     * 
     * @param key The message key
     * @param args Replacement arguments for {0}, {1}, etc.
     * @return Translated and formatted message
     */
    @Nonnull
    public static String get(@Nonnull String key, Object... args) {
        return get((PlayerRef) null, key, args);
    }
    
    /**
     * Get a translated message for a specific language.
     * 
     * @param language The language code (e.g., "en-US", "es-ES")
     * @param key The message key
     * @param args Replacement arguments
     * @return Translated and formatted message
     */
    @Nonnull
    public static String getForLanguage(@Nonnull String language, @Nonnull String key, Object... args) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            // Fallback if I18nModule not available
            return formatFallback(key, args);
        }
        
        String message = i18n.getMessage(language, key);
        if (message == null) {
            // Try fallback to en-US
            message = i18n.getMessage("en-US", key);
        }
        if (message == null) {
            // Key not found - return key itself for debugging
            return key;
        }
        
        return format(message, args);
    }
    
    /**
     * Format a message with arguments.
     * Replaces {0}, {1}, etc. with provided arguments.
     */
    @Nonnull
    private static String format(@Nonnull String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        
        String result = message;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String replacement = args[i] != null ? args[i].toString() : "";
            result = result.replace(placeholder, replacement);
        }
        return result;
    }
    
    /**
     * Fallback formatting when I18n is not available.
     */
    @Nonnull
    private static String formatFallback(@Nonnull String key, Object... args) {
        StringBuilder sb = new StringBuilder(key);
        if (args != null && args.length > 0) {
            sb.append(": ");
            for (Object arg : args) {
                sb.append(arg).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
