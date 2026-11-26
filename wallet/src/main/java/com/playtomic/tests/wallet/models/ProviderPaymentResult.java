package com.playtomic.tests.wallet.models;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Optional;

/** A confirmed fixture result. Unconfirmed responses are errors, not declines. */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderPaymentResult {
    Optional<String> receiptId;

    /** Creates an approved result with a nonblank provider receipt identifier. */
    public static ProviderPaymentResult approved(String receiptId) {
        if (receiptId == null || receiptId.isBlank()) {
            throw new IllegalArgumentException("An approved payment requires a provider receipt ID");
        }
        return new ProviderPaymentResult(Optional.of(receiptId));
    }

    /** Creates a confirmed decline without a receipt identifier. */
    public static ProviderPaymentResult declined() {
        return new ProviderPaymentResult(Optional.empty());
    }

    /** Reports whether the provider result contains a receipt identifier. */
    public boolean isApproved() {
        return receiptId.isPresent();
    }
}
