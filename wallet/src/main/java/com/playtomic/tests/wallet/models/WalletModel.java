package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;

@Value
@Accessors(chain = true)
public class WalletModel implements IModel {
    @Serial
    private static final long serialVersionUID = -1355628255156333806L;

    String id;

    BigDecimal balance;

    String currency;

    public WalletModel(String id, @NonNull BigDecimal balance, String currency) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Wallet currency is required");
        }
        this.id = id;
        this.balance = balance;
        this.currency = currency;
    }
}