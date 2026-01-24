package com.ecotale.api;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Interface for physical coins provider addon.
 * 
 * <p>Ecotale Core exposes this interface but does NOT implement it.
 * The EcotaleCoins addon implements this and registers via 
 * {@link EcotaleAPI#registerPhysicalCoinsProvider(PhysicalCoinsProvider)}.
 * 
 * <p>Other plugins (like EcotaleJobs) can check if coins are available via
 * {@link EcotaleAPI#isPhysicalCoinsAvailable()} and use the provider.
 * 
 * @author Ecotale
 * @since 1.1.0
 */
public interface PhysicalCoinsProvider {
    
    // ========== Inventory Operations ==========
    
    /**
     * Count total coin value in player's inventory.
     * 
     * @param player Player entity (must be in-world)
     * @return Total value in base units (copper equivalent)
     */
    long countInInventory(@Nonnull Player player);
    
    /**
     * Check if player has enough coins in inventory.
     * 
     * @param player Player entity
     * @param amount Amount to check (in base units)
     * @return true if player has enough coins
     */
    boolean canAfford(@Nonnull Player player, long amount);
    
    /**
     * Check if coins can fit in player's inventory.
     * Uses intelligent stacking calculation.
     * 
     * @param player Player entity
     * @param amount Amount to check (in base units)
     * @return Result with slots needed/available info
     * @since 1.2.0
     */
    CoinOperationResult canFitAmount(@Nonnull Player player, long amount);
    
    /**
     * Give coins to player's inventory using optimal denomination.
     * Pre-checks space before attempting to avoid partial failures.
     * 
     * @param player Player entity
     * @param amount Amount in base units
     * @return CoinOperationResult with SUCCESS or NOT_ENOUGH_SPACE
     * @since 1.2.0
     */
    CoinOperationResult giveCoins(@Nonnull Player player, long amount);
    
    /**
     * Take coins from player's inventory.
     * 
     * @param player Player entity
     * @param amount Amount in base units
     * @return CoinOperationResult with SUCCESS or INSUFFICIENT_FUNDS
     * @since 1.2.0
     */
    CoinOperationResult takeCoins(@Nonnull Player player, long amount);

    
    // ========== World Drop Operations ==========
    
    /**
     * Drop coins at a specific position in the world.
     * Uses CommandBuffer for deferred spawning (ECS safe).
     * 
     * @param store Component accessor
     * @param commandBuffer CommandBuffer for deferred spawning
     * @param position World position to drop at
     * @param amount Total value in base units
     */
    void dropCoins(
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d position,
        long amount
    );
    
    /**
     * Drop coins at an entity's position (e.g., dead mob).
     * 
     * @param entityRef Entity reference
     * @param store Component accessor
     * @param commandBuffer CommandBuffer for deferred spawning
     * @param amount Total value in base units
     */
    void dropCoinsAtEntity(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        long amount
    );
    
    // ========== Bank Operations ==========
    
    /**
     * Get bank balance for a player.
     * 
     * @param playerUuid Player's UUID
     * @return Bank balance in base units
     */
    long getBankBalance(@Nonnull UUID playerUuid);
    
    /**
     * Check if player's bank has enough funds.
     * 
     * @param playerUuid Player's UUID
     * @param amount Amount to check
     * @return true if bank has enough
     */
    boolean canAffordFromBank(@Nonnull UUID playerUuid, long amount);
    
    /**
     * Deposit coins from inventory to bank.
     * Atomic operation - either fully succeeds or fully fails.
     * 
     * @param player Player entity
     * @param playerUuid Player's UUID
     * @param amount Amount to deposit
     * @return CoinOperationResult with SUCCESS or INSUFFICIENT_FUNDS
     * @since 1.2.0
     */
    CoinOperationResult bankDeposit(@Nonnull Player player, @Nonnull UUID playerUuid, long amount);
    
    /**
     * Withdraw coins from bank to inventory.
     * Atomic operation - either fully succeeds or fully fails.
     * 
     * @param player Player entity
     * @param playerUuid Player's UUID
     * @param amount Amount to withdraw
     * @return CoinOperationResult with SUCCESS, INSUFFICIENT_FUNDS, or NOT_ENOUGH_SPACE
     * @since 1.2.0
     */
    CoinOperationResult bankWithdraw(@Nonnull Player player, @Nonnull UUID playerUuid, long amount);
    
    /**
     * Get total wealth (bank + physical coins in inventory).
     * 
     * @param player Player entity
     * @param playerUuid Player's UUID
     * @return Total wealth in base units
     */
    long getTotalWealth(@Nonnull Player player, @Nonnull UUID playerUuid);
}
