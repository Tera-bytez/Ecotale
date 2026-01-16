package com.ecotale.commands;

import com.ecotale.Main;
import com.ecotale.economy.PlayerBalance;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

/**
 * Balance command - shows player's current balance
 */
public class BalanceCommand extends AbstractAsyncCommand {
    
    public BalanceCommand() {
        super("bal", "Check your balance");
        this.addAliases("balance", "money");
        this.setPermissionGroup(GameMode.Adventure);
    }
    
    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        if (commandContext.sender() instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null) {
                        player.sendMessage(Message.raw("Error: Could not get player data").color(Color.RED));
                        return;
                    }
                    
                    Main.getInstance().getEconomyManager().ensureAccount(playerRef.getUuid());
                    PlayerBalance balance = Main.getInstance().getEconomyManager().getPlayerBalance(playerRef.getUuid());
                    
                    if (balance == null) {
                        player.sendMessage(Message.raw("Error: Could not load balance").color(Color.RED));
                        return;
                    }
                    
                    String formattedBalance = Main.CONFIG.get().format(balance.getBalance());
                    String earnedStr = Main.CONFIG.get().formatShort(balance.getTotalEarned());
                    String spentStr = Main.CONFIG.get().formatShort(balance.getTotalSpent());
                    
                    player.sendMessage(Message.raw("----- Your Balance -----").color(new Color(255, 215, 0)));
                    player.sendMessage(Message.join(
                        Message.raw("  Balance: ").color(Color.GRAY),
                        Message.raw(formattedBalance).color(new Color(50, 205, 50)).bold(true)
                    ));
                    
                }, player.getWorld());
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
