package com.ecotale.gui;

import com.ecotale.Main;
import com.ecotale.economy.EconomyManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.UUID;

/**
 * PayGui - Interactive GUI for sending payments to other players.
 * 
 * Triggered by: /pay (without arguments)
 * 
 * Features:
 * - List of online players with search
 * - Amount input with fee preview
 * - Confirmation before sending
 */
public class PayGui extends InteractiveCustomUIPage<PayGui.PayGuiData> {
    
    private String searchQuery = "";
    private String selectedPlayerUuid = null;
    private String amountInput = "";
    
    public PayGui(@NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PayGuiData.CODEC);
    }
    
    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, 
                      @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        // Load main page layout
        cmd.append("Pages/Ecotale_PayPage.ui");
        
        // Set initial balance
        double balance = Main.getInstance().getEconomyManager().getBalance(playerRef.getUuid());
        cmd.set("#BalanceValue.Text", Main.CONFIG.get().format(balance));
        
        cmd.set("#SearchInput.Value", this.searchQuery);
        cmd.set("#AmountInput.Value", this.amountInput);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#AmountInput",
            EventData.of("@AmountInput", "#AmountInput.Value"), false);
        
        // Setup buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", 
            EventData.of("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", 
            EventData.of("Action", "Confirm"), false);
        
        // Build player list
        buildPlayerList(cmd, events, store);
        
        // Update fee preview
        updateFeePreview(cmd);
        
        // Translate UI elements based on server config language
        translateUI(cmd);
    }
    
    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, 
                                @NonNullDecl PayGuiData data) {
        super.handleDataEvent(ref, store, data);
        
        // Handle search query change
        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            refreshUI(ref, store);
            return;
        }
        
        // Handle amount input change
        if (data.amountInput != null) {
            this.amountInput = data.amountInput;
            UICommandBuilder cmd = new UICommandBuilder();
            updateFeePreview(cmd);
            this.sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        
        // Handle player selection
        if (data.selectedPlayer != null) {
            this.selectedPlayerUuid = data.selectedPlayer;
            refreshUI(ref, store);
            return;
        }
        
        // Handle actions
        if (data.action != null) {
            switch (data.action) {
                case "Close" -> {
                    this.close();
                    return;
                }
                case "Confirm" -> {
                    handleConfirmPayment(ref, store);
                    return;
                }
            }
        }
        
        this.sendUpdate();
    }
    
    private void buildPlayerList(@NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events,
                                  @NonNullDecl Store<EntityStore> store) {
        cmd.clear("#PlayerList");
        
        int index = 0;
        for (PlayerRef player : Universe.get().getPlayers()) {
            // Skip self
            if (player.getUuid().equals(playerRef.getUuid())) {
                continue;
            }
            
            String username = player.getUsername();
            
            // Apply search filter
            if (!searchQuery.isEmpty() && !username.toLowerCase().contains(searchQuery)) {
                continue;
            }
            
            String uuid = player.getUuid().toString();
            
            // Append player entry
            cmd.append("#PlayerList", "Pages/Ecotale_PayPlayerEntry.ui");
            cmd.set("#PlayerList[" + index + "] #PlayerName.Text", username);
            
            // Show selection indicator
            boolean isSelected = uuid.equals(selectedPlayerUuid);
            cmd.set("#PlayerList[" + index + "] #SelectedIcon.Visible", isSelected);
            
            // Bind click event
            events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerList[" + index + "]",
                EventData.of("SelectedPlayer", uuid), false);
            
            index++;
        }
        
        // Show empty message if no players
        if (index == 0) {
            cmd.appendInline("#PlayerList", "Group { LayoutMode: Center; Anchor: (Height: 80); " +
                "Label { Text: \"No players online\"; Style: (FontSize: 14, TextColor: #888888, HorizontalAlignment: Center); } }");
        }
    }
    
    private void updateFeePreview(@NonNullDecl UICommandBuilder cmd) {
        double amount = parseAmount(amountInput);
        double feePercent = Main.CONFIG.get().getTransferFee();
        double fee = amount * feePercent;
        double currentBalance = Main.getInstance().getEconomyManager().getBalance(playerRef.getUuid());
        double balanceAfter = currentBalance - amount - fee;
        
        cmd.set("#FeeValue.Text", Main.CONFIG.get().format(fee));
        
        // Balance after: green if positive, red if negative
        if (balanceAfter >= 0) {
            cmd.set("#BalanceAfterValue.Text", Main.CONFIG.get().format(balanceAfter));
        } else {
            cmd.set("#BalanceAfterValue.Text", "Insufficient");
        }
    }
    
    private void handleConfirmPayment(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
        // Validate selection
        if (selectedPlayerUuid == null) {
            playerRef.sendMessage(Message.raw("Please select a player first").color(Color.RED));
            return;
        }
        
        // Validate amount
        double amount = parseAmount(amountInput);
        if (amount <= 0) {
            playerRef.sendMessage(Message.raw("Please enter a valid amount").color(Color.RED));
            return;
        }
        
        // Check minimum transaction
        double minTx = Main.CONFIG.get().getMinimumTransaction();
        if (amount < minTx) {
            playerRef.sendMessage(Message.join(
                Message.raw("Minimum transaction is ").color(Color.RED),
                Message.raw(Main.CONFIG.get().format(minTx)).color(Color.WHITE)
            ));
            return;
        }
        
        // Execute transfer
        UUID targetUuid = UUID.fromString(selectedPlayerUuid);
        EconomyManager.TransferResult result = Main.getInstance().getEconomyManager()
            .transfer(playerRef.getUuid(), targetUuid, amount, "GUI payment");
        
        switch (result) {
            case SUCCESS -> {
                double fee = amount * Main.CONFIG.get().getTransferFee();
                playerRef.sendMessage(Message.join(
                    Message.raw("Payment sent! ").color(Color.GREEN),
                    Message.raw(Main.CONFIG.get().format(amount)).color(new Color(50, 205, 50)).bold(true),
                    fee > 0 ? Message.raw(" (Fee: " + Main.CONFIG.get().format(fee) + ")").color(Color.GRAY) : Message.raw("")
                ));
                this.close();
            }
            case INSUFFICIENT_FUNDS -> {
                playerRef.sendMessage(Message.raw("Insufficient funds!").color(Color.RED));
            }
            case SELF_TRANSFER -> {
                playerRef.sendMessage(Message.raw("Cannot send money to yourself!").color(Color.RED));
            }
            case RECIPIENT_MAX_BALANCE -> {
                playerRef.sendMessage(Message.raw("Recipient has reached maximum balance!").color(Color.RED));
            }
            default -> {
                playerRef.sendMessage(Message.raw("Transfer failed!").color(Color.RED));
            }
        }
    }
    
    private void refreshUI(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        buildPlayerList(cmd, events, store);
        updateFeePreview(cmd);
        this.sendUpdate(cmd, events, false);
    }

    
    private double parseAmount(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(input.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /** Helper to get translated text with fallback */
    private String getTranslation(String lang, String key, String fallback) {
        String value = I18nModule.get().getMessage(lang, key);
        return value != null ? value : fallback;
    }
    
    /** Translate all static UI elements based on server config language */
    private void translateUI(@NonNullDecl UICommandBuilder cmd) {
        String lang = Main.CONFIG.get().getLanguage();
        
        cmd.set("#Title.Text", getTranslation(lang, "ecotale.gui.pay.title", "Send Payment"));
        cmd.set("#BalanceLabel.Text", getTranslation(lang, "ecotale.gui.pay.your_balance_label", "Your Balance"));
        cmd.set("#SearchLabel.Text", getTranslation(lang, "ecotale.gui.pay.search", "Search"));
        cmd.set("#AmountLabel.Text", getTranslation(lang, "ecotale.gui.pay.amount", "Amount"));
        cmd.set("#FeeLabel.Text", getTranslation(lang, "ecotale.gui.pay.fee_label", "Transfer Fee"));
        cmd.set("#ConfirmButton.Text", getTranslation(lang, "ecotale.gui.pay.send", "SEND PAYMENT"));
    }
    
    // ========== Data Codec ==========
    
    public static class PayGuiData {
        private static final String KEY_SEARCH = "@SearchQuery";
        private static final String KEY_AMOUNT = "@AmountInput";
        private static final String KEY_SELECTED = "SelectedPlayer";
        private static final String KEY_ACTION = "Action";
        
        public static final BuilderCodec<PayGuiData> CODEC = BuilderCodec.<PayGuiData>builder(PayGuiData.class, PayGuiData::new)
            .append(new KeyedCodec<>(KEY_SEARCH, Codec.STRING), (d, v, e) -> d.searchQuery = v, (d, e) -> d.searchQuery).add()
            .append(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (d, v, e) -> d.amountInput = v, (d, e) -> d.amountInput).add()
            .append(new KeyedCodec<>(KEY_SELECTED, Codec.STRING), (d, v, e) -> d.selectedPlayer = v, (d, e) -> d.selectedPlayer).add()
            .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v, e) -> d.action = v, (d, e) -> d.action).add()
            .build();
        
        private String searchQuery;
        private String amountInput;
        private String selectedPlayer;
        private String action;
    }
}
