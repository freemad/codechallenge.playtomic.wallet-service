package com.playtomic.tests.common.contracts.dtos;

import java.util.Map;

public interface IResponseDto
        extends IDto {
    String getCid();

    boolean isSuccess();

    String getTimestamp();

    IDto getResult();

    IErrorDto getError();

    Map<String, String> getMetadata();
}