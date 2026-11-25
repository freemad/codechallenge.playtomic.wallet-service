package com.playtomic.tests.common.contracts.dtos;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

public interface IResponseDto
        extends IDto {
    String getCid();

    boolean isSuccess();

    default String getTimestamp() {
        return String.valueOf(OffsetDateTime.now());
    }

    IDto getResult();

    IErrorDto getError();

    Map<String, String> getMetadata();
}