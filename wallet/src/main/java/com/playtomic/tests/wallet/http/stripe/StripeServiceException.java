package com.playtomic.tests.wallet.http.stripe;

import com.playtomic.tests.common.enums.CommonErrorCode;
import com.playtomic.tests.common.exceptions.BusinessException;

import java.io.Serial;

public class StripeServiceException extends BusinessException {

    @Serial
    private static final long serialVersionUID = -2717985606763066055L;

    public StripeServiceException(String message) {
        this(message, null);
    }

    public StripeServiceException(String message, Throwable cause) {
        this(CommonErrorCode.EXTERNAL_SERVICE_EXCEPTION, message, cause);
    }

    protected StripeServiceException(CommonErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}