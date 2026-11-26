package com.playtomic.tests.common.contracts.dtos;

import java.util.Map;

/** Defines the error fields exposed at the transport boundary. */
public interface IErrorDto
        extends IDto {
    /** Returns the application error code. */
    Integer getCode();

    /** Returns the message associated with the error. */
    String getMessage();

    /** Returns the additional error parameters. */
    Map<String, String> getParams();
}
