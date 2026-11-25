package com.playtomic.tests.wallet.enums;

import com.playtomic.tests.common.contracts.exceptions.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.playtomic.tests.common.constants.ErrorCode.WALLET_ERROR_CODE_PREFIX;

@Getter
@AllArgsConstructor
public enum WalletErrorCode implements IErrorCode {
    INVALID_WALLET_ID(WALLET_ERROR_CODE_PREFIX + 1, "Wallet ID is required", "Missing or blank wallet ID"),
    WALLET_NOT_FOUND(WALLET_ERROR_CODE_PREFIX + 2, "Wallet not found", "No wallet with the requested ID"),
    INVALID_REQUEST(WALLET_ERROR_CODE_PREFIX + 3, "Invalid wallet operation", "Invalid mutation input"),
    CURRENCY_MISMATCH(WALLET_ERROR_CODE_PREFIX + 4, "Operation currency must match the wallet", "Currency mismatch"),
    IDEMPOTENCY_CONFLICT(WALLET_ERROR_CODE_PREFIX + 5,
            "Idempotency key already identifies a different operation", "Conflicting replay"),
    BALANCE_UPDATE_NOT_MATCHED(WALLET_ERROR_CODE_PREFIX + 6,
            "Wallet update did not match its snapshot", "The guarded balance update matched no wallet");

    private final int code;
    private final String message;
    private final String description;
}