package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operational statuses persisted using their stable numeric codes. */
@Getter
@AllArgsConstructor
public enum WalletOperationMongoStatus {
    PENDING(1),
    REVIEW_REQUIRED(2),
    RECEIPT_RECORDED(3),
    SUCCEEDED(4),
    INSUFFICIENT_FUNDS(5),
    PAYMENT_REJECTED(6),
    PROVIDER_UNAVAILABLE(7),
    WALLET_UNAVAILABLE(8);

    private final int value;

    public static WalletOperationMongoStatus ofValue(int value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown wallet operation status code: " + value);
    }
}
