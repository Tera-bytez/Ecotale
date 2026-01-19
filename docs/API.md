# Ecotale API Documentation

## Overview

Ecotale provides a public API for other plugins to interact with the economy system.

## Getting Started

```java
import com.ecotale.api.EcotaleAPI;

// Check if API is available
if (EcotaleAPI.isAvailable()) {
    // API is ready
}
```

## API Version

```java
// Check API version for compatibility
int version = EcotaleAPI.getAPIVersion();  // Returns 2
String pluginVersion = EcotaleAPI.getPluginVersion();  // Returns "1.0.1"
```

---

## Read Operations (No Rate Limit)

### Balance Operations

```java
// Get a player's balance
double balance = EcotaleAPI.getBalance(UUID playerUuid);

// Check if player has enough balance
boolean canAfford = EcotaleAPI.hasBalance(UUID playerUuid, double amount);
```

### Configuration

```java
// Get currency symbol (e.g., "$")
String symbol = EcotaleAPI.getCurrencySymbol();

// Get HUD prefix label (e.g., "Bank")
String hudPrefix = EcotaleAPI.getHudPrefix();

// Format amount with currency symbol
String formatted = EcotaleAPI.format(1500.0);  // "$1,500.00"

// Get language settings
String language = EcotaleAPI.getLanguage();  // "en-US"
boolean usePlayerLang = EcotaleAPI.isUsePlayerLanguage();

// Get max balance limit
double maxBalance = EcotaleAPI.getMaxBalance();
```

### Leaderboards & Statistics

```java
// Get top N balances
List<PlayerBalance> top10 = EcotaleAPI.getTopBalances(10);

// Get all player UUIDs with accounts
Set<UUID> allPlayers = EcotaleAPI.getAllPlayerUUIDs();

// Get total economy circulation
double totalMoney = EcotaleAPI.getTotalCirculating();

// Get transaction history for a player
List<TransactionEntry> history = EcotaleAPI.getTransactionHistory(playerUuid, 50);
```

---

## Write Operations (Rate Limited)

All write operations are rate limited: **50 burst capacity, 10 operations/second sustained**.

```java
try {
    // Deposit money
    boolean success = EcotaleAPI.deposit(playerUuid, 100.0, "Quest reward");
    
    // Withdraw money
    boolean success = EcotaleAPI.withdraw(playerUuid, 50.0, "Shop purchase");
    
    // Transfer between players (atomic)
    TransferResult result = EcotaleAPI.transfer(fromUuid, toUuid, 100.0, "Trade");
    
    // Set exact balance (admin use)
    EcotaleAPI.setBalance(playerUuid, 1000.0, "Admin set");
    
    // Reset to starting balance
    EcotaleAPI.resetBalance(playerUuid, "Admin reset");
    
} catch (EcotaleRateLimitException e) {
    // Rate limit exceeded - wait and retry
    Thread.sleep(100);
}
```

---

## Physical Coins Integration

For plugins that want to drop physical coins (requires EcotaleCoins addon):

```java
import com.ecotale.api.PhysicalCoinsProvider;

// Check if EcotaleCoins is available
if (EcotaleAPI.isPhysicalCoinsAvailable()) {
    PhysicalCoinsProvider coins = EcotaleAPI.getPhysicalCoins();
    
    // Drop coins at entity position
    coins.dropCoinsAtEntity(entityRef, store, commandBuffer, 500L);
    
    // Drop coins at world position
    coins.dropCoinsAtPosition(world, position, 1000L);
}
```

---

## Thread Safety

All API methods are thread-safe and use per-player locking internally. Atomic transfers prevent race conditions.

## Rate Limiting

Write operations are rate-limited per player:
- **Burst capacity:** 50 operations
- **Refill rate:** 10 operations/second
- Read operations are NOT rate limited
