package com.playtomic.tests.common.contracts.exceptions;



import com.playtomic.tests.common.contracts.models.IModel;

import java.util.Map;

/** Describes an application failure before conversion to a transport error. */
public interface IError
        extends IModel {
    /** Returns the application error code. */
    IErrorCode getCode();

    /** Returns the message associated with the error. */
    String getMessage();

    /** Returns the exception associated with the error. */
    RuntimeException getException();

    /** Returns the additional error parameters. */
    Map<String, String> getParams();
}