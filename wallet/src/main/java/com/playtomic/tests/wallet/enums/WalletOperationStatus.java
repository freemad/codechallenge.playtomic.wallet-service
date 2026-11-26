package com.playtomic.tests.wallet.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.common.constants.CommonParam;
import com.playtomic.tests.common.utils.EnumLookup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Tracks a wallet mutation from reservation to its recorded outcome. */
@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum WalletOperationStatus {
    WALLET_OPERATION_STATUS_UNSPECIFIED(CommonParam.ENUM_UNSPECIFIED_VALUE, CommonParam.ENUM_UNSPECIFIED_NAME),
    WALLET_OPERATION_STATUS_PENDING((short) 1, "pending"),
    WALLET_OPERATION_STATUS_REVIEW_REQUIRED((short) 2, "review_required"),
    WALLET_OPERATION_STATUS_RECEIPT_RECORDED((short) 3, "receipt_recorded"),
    WALLET_OPERATION_STATUS_SUCCEEDED((short) 4, "succeeded"),
    WALLET_OPERATION_STATUS_INSUFFICIENT_FUNDS((short) 5, "insufficient_funds"),
    WALLET_OPERATION_STATUS_PAYMENT_REJECTED((short) 6, "payment_rejected"),
    WALLET_OPERATION_STATUS_PROVIDER_UNAVAILABLE((short) 7, "provider_unavailable"),
    WALLET_OPERATION_STATUS_WALLET_UNAVAILABLE((short) 8, "wallet_unavailable"),
    WALLET_OPERATION_STATUS_ERROR(CommonParam.ENUM_ERROR_VALUE, CommonParam.ENUM_ERROR_NAME);

    private final short value;
    private final String name;

    /** Returns the candidate only if it is an operational value rather than null or a sentinel. */
    public static WalletOperationStatus validate(WalletOperationStatus candidate) {
        return EnumLookup.requireOperational(candidate, WALLET_OPERATION_STATUS_UNSPECIFIED,
                WALLET_OPERATION_STATUS_ERROR, "Wallet operation status requires an operational value");
    }

    /** Resolves the numeric representation to an enum member, rejecting unknown values. */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static WalletOperationStatus ofValue(@JsonProperty(value = "value", required = true) short value) {
        return EnumLookup.find(values(), status -> status.value == value, "Unknown WalletOperationStatus value: " + value);
    }

    /** Resolves the external name to an enum member, rejecting unknown names. */
    public static WalletOperationStatus ofName(String name) {
        return EnumLookup.find(values(), status -> status.name.equals(name), "Unknown WalletOperationStatus name: " + name);
    }
}

