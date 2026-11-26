package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operational statuses persisted using their stable numeric codes. */
@Getter
@AllArgsConstructor
public enum WalletOperationMongoStatus {
    PENDING(1, "pending"),
    REVIEW_REQUIRED(2, "review_required"),
    RECEIPT_RECORDED(3, "receipt_recorded"),
    SUCCEEDED(4, "succeeded"),
    INSUFFICIENT_FUNDS(5, "insufficient_funds"),
    PAYMENT_REJECTED(6, "payment_rejected"),
    PROVIDER_UNAVAILABLE(7, "provider_unavailable"),
    WALLET_UNAVAILABLE(8, "wallet_unavailable");

    private final int value;
    /** Stable audit label, independent of the Java constant name. */
    private final String name;

    public static WalletOperationMongoStatus validate(WalletOperationMongoStatus candidate) {
        if (candidate == null) {
            throw new BusinessException(CommonErrorCode.ILLEGAL_ARGUMENT_EXCEPTION, "Wallet operation status requires an operational value");
        }
        return candidate;
    }

    public static WalletOperationMongoStatus ofName(String name) {
        return EnumLookup.find(values(), status -> status.name.equals(name), "Unknown wallet operation status name: " + name);
    }

    public static WalletOperationMongoStatus ofValue(int value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown wallet operation status code: " + value);
    }
}
