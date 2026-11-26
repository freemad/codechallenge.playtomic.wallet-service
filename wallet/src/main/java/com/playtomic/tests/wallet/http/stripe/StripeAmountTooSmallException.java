package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.enums.CommonErrorCode;

import java.io.Serial;

/** Signals a confirmed amount rejection from the Stripe simulator. */
public class StripeAmountTooSmallException extends StripeServiceException {

    @Serial
    private static final long serialVersionUID = -5622710290810842994L;

    /** Creates an exception reporting a rejected charge amount. */
    public StripeAmountTooSmallException() {
        this(null);
    }

    /** Creates an exception reporting a rejected charge amount. */
    public StripeAmountTooSmallException(Throwable cause) {
        super(CommonErrorCode.BAD_REQUEST_EXCEPTION, "Payment provider rejected the amount as too small", cause);
    }
}