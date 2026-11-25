package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.enums.CommonErrorCode;

import java.io.Serial;

public class StripeAmountTooSmallException extends StripeServiceException {

    @Serial
    private static final long serialVersionUID = -5622710290810842994L;

    public StripeAmountTooSmallException() {
        this(null);
    }

    public StripeAmountTooSmallException(Throwable cause) {
        super(CommonErrorCode.BAD_REQUEST_EXCEPTION, "Payment provider rejected the amount as too small", cause);
    }
}