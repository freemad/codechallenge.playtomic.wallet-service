package com.playtomic.tests.wallet.http.stripe;

import java.io.Serial;

public class StripeAmountTooSmallException extends StripeServiceException {

    @Serial
    private static final long serialVersionUID = -5622710290810842994L;

    public StripeAmountTooSmallException() {
        super("Payment provider rejected the amount as too small");
    }
}