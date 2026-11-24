package com.playtomic.tests.common.contracts.dtos;

import java.util.Map;

public interface IErrorDto
        extends IDto {
    Integer getCode();

    String getMessage();

    Map<String, String> getParams();
}
