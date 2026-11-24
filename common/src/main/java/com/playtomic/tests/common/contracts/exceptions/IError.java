package com.playtomic.tests.common.contracts.exceptions;



import com.playtomic.tests.common.contracts.models.IModel;

import java.util.Map;

public interface IError
        extends IModel {
    IErrorCode getCode();

    String getMessage();

    RuntimeException getException();

    Map<String, String> getParams();
}