package com.playtomic.tests.common.contracts.exceptions;

import java.io.Serializable;

/** Defines the code, message and description used to identify an application error. */
public interface IErrorCode
        extends Serializable {
    /** Returns the application error code. */
    int getCode();

    /** Returns the message associated with the error. */
    String getMessage();

    /** Returns the description of the failure category. */
    String getDescription();
}