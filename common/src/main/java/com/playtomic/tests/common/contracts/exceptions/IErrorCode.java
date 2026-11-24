package com.playtomic.tests.common.contracts.exceptions;

import java.io.Serializable;

public interface IErrorCode
        extends Serializable {
    int getCode();

    String getMessage();

    String getDescription();
}