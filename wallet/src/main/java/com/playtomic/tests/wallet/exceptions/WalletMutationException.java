package com.playtomic.tests.wallet.exceptions;

import com.playtomic.tests.common.exceptions.BusinessException;
import com.playtomic.tests.wallet.enums.WalletErrorCode;
import lombok.Getter;

import java.io.Serial;

@Getter
public class WalletMutationException extends BusinessException {
    @Serial
    private static final long serialVersionUID = 3783678261205046319L;

    private final Reason reason;

    public WalletMutationException(Reason reason, String message) {
        super(reason.code, reason == Reason.INVALID_REQUEST ? message : "");
        this.reason = reason;
    }

    public enum Reason {
        INVALID_REQUEST(WalletErrorCode.INVALID_REQUEST),
        CURRENCY_MISMATCH(WalletErrorCode.CURRENCY_MISMATCH),
        IDEMPOTENCY_CONFLICT(WalletErrorCode.IDEMPOTENCY_CONFLICT);

        private final WalletErrorCode code;

        Reason(WalletErrorCode code) {
            this.code = code;
        }
    }
}
