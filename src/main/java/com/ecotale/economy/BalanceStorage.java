package com.ecotale.economy;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Storage wrapper for serializing player balances
 * NOTE: BuilderCodec keys MUST start with uppercase (PascalCase)
 */
public class BalanceStorage {
    
    public static final BuilderCodec<BalanceStorage> CODEC = BuilderCodec.builder(BalanceStorage.class, BalanceStorage::new)
        .append(new KeyedCodec<>("Balances", PlayerBalance.ARRAY_CODEC),
            (s, v, extraInfo) -> s.balances = v, 
            (s, extraInfo) -> s.balances).add()
        .build();
    
    private PlayerBalance[] balances = new PlayerBalance[0];
    
    public BalanceStorage() {}
    
    public BalanceStorage(PlayerBalance[] balances) {
        this.balances = balances;
    }
    
    public PlayerBalance[] getBalances() {
        return balances;
    }
}
