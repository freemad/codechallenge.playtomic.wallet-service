package com.playtomic.tests.common.contracts.exceptions;

import java.io.Serializable;

/** Exposes the application error code and diagnostic context carried by a business failure. */
public interface IBusinessException
        extends Serializable {
    /** Returns the application error classification. */
    IErrorCode getErrorCode();

    /** Returns the diagnostic information accompanying the error. */
    String getAdditionalInfo();

    /** Returns the underlying cause of the failure. */
    Throwable getCause();

    /** Reports whether this exception permits suppressed exceptions. */
    boolean isEnableSuppression();

    /** Reports whether this exception records a writable stack trace. */
    boolean isWritableStackTrace();
}