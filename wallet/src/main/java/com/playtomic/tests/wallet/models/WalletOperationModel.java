package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;

import static com.playtomic.tests.wallet.configs.WalletPaymentProviderProps.STRIPE;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;

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
    String provider;

    public String getProvider() {
        return type == WALLET_OPERATION_TYPE_TOP_UP && provider == null ? STRIPE : provider;
    }

    public boolean matches(WalletOperationModel request) {
        return walletId.equals(request.walletId) && type == request.type
                && amount.compareTo(request.amount) == 0 && currency.equals(request.currency)
                && Objects.equals(getProvider(), request.getProvider());
    }
}