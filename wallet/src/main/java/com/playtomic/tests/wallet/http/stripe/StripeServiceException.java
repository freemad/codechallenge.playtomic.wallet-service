package com.playtomic.tests.wallet.http.stripe;

import java.io.Serial;

public class StripeServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2717985606763066055L;

    public StripeServiceException(String message) {
        super(message);
    }

    public StripeServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}