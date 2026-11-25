package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;

import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;

@Value
@Accessors(chain = true)
public class WalletOperationModel implements IModel {
    @Serial
    private static final long serialVersionUID = -2237459088894856881L;

    String id;
    String walletId;
    WalletOperationType type;
    BigDecimal amount;
    String currency;
    WalletOperationStatus status;
    BigDecimal resultingBalance;
    Instant updatedAt;

    public boolean matches(WalletOperationModel request) {
        return walletId.equals(request.walletId) && type == request.type
                && amount.compareTo(request.amount) == 0 && currency.equals(request.currency);
    }
}