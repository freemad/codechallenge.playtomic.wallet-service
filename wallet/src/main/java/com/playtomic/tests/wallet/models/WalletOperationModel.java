package com.playtomic.tests.wallet.models;

import com.playtomic.tests.common.contracts.models.IModel;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.playtomic.tests.wallet.enums.WalletOperationStatus;
import com.playtomic.tests.wallet.enums.WalletOperationType;

import static com.playtomic.tests.wallet.configs.WalletPaymentProviderProps.STRIPE;
import static com.playtomic.tests.wallet.enums.WalletOperationType.WALLET_OPERATION_TYPE_TOP_UP;

/** Represents the request identity, state and result used to replay a wallet mutation. */
@Value
@RequiredArgsConstructor
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
    @lombok.experimental.NonFinal
    @lombok.Setter
    String providerReference;

    /** Returns the provider identifier, defaulting legacy top-ups without a provider to Stripe. */
    public String getProvider() {
        return type == WALLET_OPERATION_TYPE_TOP_UP && provider == null ? STRIPE : provider;
    }

    /** Compares wallet, operation type, numeric amount, currency and effective provider for idempotent replay. */
    public boolean matches(WalletOperationModel request) {
        return walletId.equals(request.walletId) && type == request.type
                && amount.compareTo(request.amount) == 0 && currency.equals(request.currency)
                && Objects.equals(getProvider(), request.getProvider());
    }
}