# Ecotale API Documentation

## Overview

Ecotale provides a public API for other plugins to interact with the economy system.

## Getting Started

```java
import com.ecotale.api.EcotaleAPI;
```

## Methods

### Balance Operations

```java
// Get a player's balance
double balance = EcotaleAPI.getBalance(UUID playerUuid);

// Deposit money
boolean success = EcotaleAPI.deposit(UUID playerUuid, double amount, String reason);

// Withdraw money
boolean success = EcotaleAPI.withdraw(UUID playerUuid, double amount, String reason);

// Transfer between players
boolean success = EcotaleAPI.transfer(UUID from, UUID to, double amount, String reason);
```

### Configuration

```java
// Get currency symbol
String symbol = EcotaleAPI.getCurrencySymbol();

// Get currency name
String name = EcotaleAPI.getCurrencyName();
```

## Events

Register listeners for economy events:

```java
import com.ecotale.api.events.*;

// Balance changes (cancellable)
EcotaleEvents.register(BalanceChangeEvent.class, event -> {
    UUID player = event.getPlayerUuid();
    double oldBalance = event.getOldBalance();
    double newBalance = event.getNewBalance();
    
    if (event.getNewBalance() > 1000000) {
        event.cancel();
    }
});

// Transactions (after completion)
EcotaleEvents.register(TransactionEvent.class, event -> {
    UUID source = event.getSourceUuid();
    UUID target = event.getTargetUuid();
    double amount = event.getAmount();
    double fee = event.getFee();
});
```

## Rate Limiting

API calls are rate-limited to prevent abuse. Configure in `config.json`:

```json
{
    "rateLimitBurst": 10,
    "rateLimitRefill": 100
}
```

## Thread Safety

All API methods are thread-safe and use per-player locking internally.
