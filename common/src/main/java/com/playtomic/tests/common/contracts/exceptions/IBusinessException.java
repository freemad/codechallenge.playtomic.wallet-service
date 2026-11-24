package com.playtomic.tests.common.contracts.exceptions;

import java.io.Serializable;

public interface IBusinessException
        extends Serializable {
    IErrorCode getErrorCode();

    String getAdditionalInfo();

    Throwable getCause();

    boolean isEnableSuppression();

    boolean isWritableStackTrace();
}